package com.medos.service;

import com.medos.dto.InvoiceRequest;
import com.medos.dto.PaymentRequest;
import com.medos.entity.*;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.repository.*;
import com.medos.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final InvoiceRepository invoiceRepository;
    private final ChargeRepository chargeRepository;
    private final PaymentRepository paymentRepository;
    private final PatientRepository patientRepository;
    private final AuditLogger auditLogger;

    @Transactional
    public Invoice generateInvoice(InvoiceRequest request) {
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", request.getPatientId().toString()));

        List<Charge> charges;
        if (request.getChargeIds() != null && !request.getChargeIds().isEmpty()) {
            charges = chargeRepository.findAllById(request.getChargeIds()).stream()
                    .filter(c -> c.getPatientId().equals(request.getPatientId()))
                    .filter(c -> c.getStatus() == Charge.Status.unbilled)
                    .toList();
            if (charges.isEmpty()) {
                throw new BusinessException("No unbilled charges found for the given IDs");
            }
        } else {
            charges = chargeRepository.findByPatientIdAndStatus(request.getPatientId(), Charge.Status.unbilled);
            if (charges.isEmpty()) {
                throw new BusinessException("No unbilled charges for patient");
            }
        }

        BigDecimal subtotal = charges.stream()
                .map(Charge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gstTotal = charges.stream()
                .map(c -> c.getGstAmount() != null ? c.getGstAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO;
        BigDecimal totalAmount = subtotal.add(gstTotal).subtract(discount);

        Invoice invoice = Invoice.builder()
                .invoiceNumber(generateInvoiceNumber())
                .patientId(request.getPatientId())
                .invoiceDate(LocalDateTime.now())
                .subtotal(subtotal)
                .gstTotal(gstTotal)
                .discount(discount)
                .totalAmount(totalAmount)
                .paidAmount(BigDecimal.ZERO)
                .status(Invoice.Status.issued)
                .notes(request.getNotes())
                .build();
        Invoice saved = invoiceRepository.save(invoice);

        // Link charges to invoice
        for (Charge c : charges) {
            c.setInvoiceId(saved.getId());
            c.setStatus(Charge.Status.billed);
            chargeRepository.save(c);
        }

        syncPatientBalance(request.getPatientId());

        auditLogger.log("INVOICE", "Invoice", saved.getId().toString(),
                null, "total=" + totalAmount + " charges=" + charges.size());
        return saved;
    }

    @Transactional
    public Payment recordPayment(PaymentRequest request) {
        Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", request.getInvoiceId().toString()));
        if (invoice.getStatus() == Invoice.Status.paid) {
            throw new BusinessException("Invoice is already fully paid");
        }

        BigDecimal newPaid = invoice.getPaidAmount().add(request.getAmount());
        if (newPaid.compareTo(invoice.getTotalAmount()) > 0) {
            throw new BusinessException(
                    "Payment exceeds invoice balance. Pending: " + invoice.getTotalAmount().subtract(invoice.getPaidAmount()));
        }

        Payment payment = Payment.builder()
                .paymentNumber(generatePaymentNumber())
                .invoiceId(request.getInvoiceId())
                .patientId(invoice.getPatientId())
                .amount(request.getAmount())
                .paymentMethod(Payment.PaymentMethod.valueOf(request.getPaymentMethod()))
                .transactionRef(request.getTransactionRef())
                .status(Payment.Status.success)
                .receivedAt(LocalDateTime.now())
                .notes(request.getNotes())
                .build();
        Payment saved = paymentRepository.save(payment);

        invoice.setPaidAmount(newPaid);
        if (newPaid.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(Invoice.Status.paid);
            // Mark all charges as paid
            List<Charge> charges = chargeRepository.findByInvoiceId(invoice.getId());
            charges.forEach(c -> { c.setStatus(Charge.Status.paid); chargeRepository.save(c); });
        } else {
            invoice.setStatus(Invoice.Status.partially_paid);
        }
        invoiceRepository.save(invoice);

        syncPatientBalance(invoice.getPatientId());

        auditLogger.log("PAYMENT", "Payment", saved.getId().toString(),
                null, "amount=" + request.getAmount() + " method=" + request.getPaymentMethod());
        return saved;
    }

    public List<Invoice> getPatientInvoices(UUID patientId) {
        return invoiceRepository.findByPatientId(patientId);
    }

    public List<Charge> getUnbilledCharges(UUID patientId) {
        return chargeRepository.findByPatientIdAndStatus(patientId, Charge.Status.unbilled);
    }

    public List<Charge> getChargesByInvoice(UUID invoiceId) {
        return chargeRepository.findByInvoiceId(invoiceId);
    }

    public List<Payment> getPaymentsByInvoice(UUID invoiceId) {
        return paymentRepository.findByInvoiceId(invoiceId);
    }

    private String generateInvoiceNumber() {
        return "INV-" + System.currentTimeMillis() % 1000000;
    }

    private String generatePaymentNumber() {
        return "PAY-" + System.currentTimeMillis() % 1000000;
    }

    private void syncPatientBalance(UUID patientId) {
        List<Charge> allCharges = chargeRepository.findByPatientId(patientId);
        BigDecimal totalBilled = allCharges.stream()
                .filter(c -> c.getStatus() == Charge.Status.billed || c.getStatus() == Charge.Status.paid)
                .map(c -> c.getTotalAmount() != null ? c.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Payment> payments = paymentRepository.findByPatientId(patientId);
        BigDecimal totalPaid = payments.stream()
                .filter(p -> p.getStatus() == Payment.Status.success)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstanding = totalBilled.subtract(totalPaid);

        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient != null) {
            patient.setOutstanding(outstanding.max(BigDecimal.ZERO));
            patientRepository.save(patient);
        }
    }
}
