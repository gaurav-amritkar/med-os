package com.medos.modules.billing.service;

import com.medos.dto.InvoiceRequest;
import com.medos.dto.PaymentRequest;
import com.medos.entity.Charge;
import com.medos.entity.Invoice;
import com.medos.entity.Patient;
import com.medos.entity.Payment;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.repository.ChargeRepository;
import com.medos.repository.InvoiceRepository;
import com.medos.repository.PatientRepository;
import com.medos.modules.billing.event.PatientBalanceEvent;
import com.medos.security.CurrentUserProvider;
import com.medos.util.AuditLogger;
import com.medos.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final PatientRepository patientRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogger auditLogger;
    private final CurrentUserProvider currentUserProvider;
    private final com.medos.modules.payment.service.PaymentService paymentService;

    /**
     * Generates an invoice from the patient's unbilled charges.
     *
     * <p>Marks the charges as {@code billed} and links them to the invoice via
     * {@code charge.invoiceId}, so {@code getChargesByInvoice} returns the lines
     * that make up the invoice.
     */
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
                throw new BusinessException("No unbilled charges found for patient");
            }
        } else {
            charges = chargeRepository.findByPatientIdAndStatus(request.getPatientId(), Charge.Status.unbilled);
            if (charges.isEmpty()) {
                throw new BusinessException("No unbilled charges found for patient");
            }
        }

        BigDecimal subtotal = charges.stream()
                .map(Charge::getAmount)
                .reduce(BigDecimal.ZERO, MoneyUtil::add);
        BigDecimal gstTotal = charges.stream()
                .map(Charge::getGstAmount)
                .reduce(BigDecimal.ZERO, MoneyUtil::add);
        BigDecimal totalAmount = charges.stream()
                .map(Charge::getTotalAmount)
                .reduce(BigDecimal.ZERO, MoneyUtil::add);

        Invoice invoice = Invoice.builder()
                .invoiceNumber(generateInvoiceNumber())
                .patientId(request.getPatientId())
                .tenantId(patient.getTenantId())
                .invoiceDate(LocalDateTime.now())
                .subtotal(subtotal)
                .gstTotal(gstTotal)
                .totalAmount(totalAmount)
                .status(Invoice.Status.issued)
                .generatedBy(currentUserProvider.getCurrentUserId())
                .notes(request.getNotes())
                .build();

        Invoice savedInvoice = invoiceRepository.save(invoice);

        charges.forEach(c -> {
            c.setStatus(Charge.Status.billed);
            c.setInvoiceId(savedInvoice.getId());
        });
        chargeRepository.saveAll(charges);

        auditLogger.log("INVOICE_GENERATED", "Invoice", savedInvoice.getId().toString(),
                null, "amount=" + totalAmount.stripTrailingZeros().toPlainString() + " charges=" + charges.size());

        // Balance counts billed|paid charges vs payments — invoicing moves charges
        // into the counted set, so refresh the patient's outstanding balance.
        eventPublisher.publishEvent(new PatientBalanceEvent(request.getPatientId()));

        return savedInvoice;
    }    /**
     * Records a payment against an invoice.
     *
     * @deprecated payment processing moved to the payment bounded context —
     *             use {@link com.medos.modules.payment.service.PaymentService#processPayment}.
     *             Kept only until remaining callers are migrated.
     */
    @Deprecated(forRemoval = true)
    @Transactional
    public Payment recordPayment(PaymentRequest request) {
        return paymentService.processPayment(request);
    }

    public List<Invoice> getPatientInvoices(UUID patientId) {
        return invoiceRepository.findByPatientId(patientId);
    }

    public List<Charge> getUnbilledCharges(UUID patientId) {
        return chargeRepository.findByPatientIdAndStatus(patientId, Charge.Status.unbilled);
    }

    public List<Charge> getChargesByInvoice(UUID invoiceId) {
        return chargeRepository.findByInvoiceIdOrderByPaidAtDesc(invoiceId);
    }

    public List<Payment> getPaymentsByInvoice(UUID invoiceId) {
        return paymentService.getInvoicePayments(invoiceId);
    }

    /**
     * Sequence-backed invoice number — monotonic across concurrent requests,
     * replacing the old System.currentTimeMillis() generator that produced
     * duplicates when two invoices were created in the same millisecond.
     */
    private String generateInvoiceNumber() {
        return String.format("INV-%06d", invoiceRepository.getNextInvoiceSeq());
    }
}
