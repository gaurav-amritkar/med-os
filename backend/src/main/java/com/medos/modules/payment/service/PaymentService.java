package com.medos.modules.payment.service;

import com.medos.dto.PaymentRequest;
import com.medos.entity.Payment;
import com.medos.entity.Invoice;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.modules.billing.event.PatientBalanceEvent;
import com.medos.repository.PaymentRepository;
import com.medos.repository.InvoiceRepository;
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
import java.util.Locale;
import java.util.UUID;

/**
 * Payment processing — the payment bounded context's write side.
 *
 * <p>Locks the invoice row ({@code PESSIMISTIC_WRITE}) so concurrent payments
 * serialize and the balance-due check cannot be raced past. Sequence-backed
 * {@code paymentNumber}; recalculates {@code paidAmount}; publishes
 * {@link PatientBalanceEvent} so the patient's outstanding balance reflects
 * the payment.
 *
 * <p>Extracted from the former god {@code BillingService}; invoice generation
 * and queries remain in {@code BillingService}.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogger auditLogger;

    @Transactional
    public Payment processPayment(PaymentRequest request) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(request.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", request.getInvoiceId().toString()));

        if (invoice.getStatus() == Invoice.Status.paid) {
            throw new BusinessException("Invoice already paid");
        }

        BigDecimal paymentAmount = MoneyUtil.normalize(request.getAmount());
        if (MoneyUtil.isZeroOrNegative(paymentAmount)) {
            throw new BusinessException("Payment amount must be positive");
        }

        BigDecimal alreadyPaid = paymentRepository.findByInvoiceId(invoice.getId()).stream()
                .filter(p -> p.getStatus() != Payment.Status.failed && p.getStatus() != Payment.Status.refunded)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, MoneyUtil::add);
        BigDecimal balanceDue = MoneyUtil.subtract(invoice.getTotalAmount(), alreadyPaid);
        if (paymentAmount.compareTo(balanceDue) > 0) {
            throw new BusinessException("Payment amount exceeds invoice balance due ("
                    + balanceDue.stripTrailingZeros().toPlainString() + ")");
        }

        Payment payment = Payment.builder()
                .paymentNumber(generatePaymentNumber())
                .invoiceId(invoice.getId())
                .patientId(invoice.getPatientId())
                .tenantId(invoice.getTenantId())
                .amount(paymentAmount)
                .paymentMethod(parsePaymentMethod(request.getPaymentMethod()))
                .transactionRef(request.getTransactionRef())
                .receivedBy(currentUserProvider.getCurrentUserId())
                .receivedAt(LocalDateTime.now())
                .notes(request.getNotes())
                .status(Payment.Status.success)
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        BigDecimal paidAfter = MoneyUtil.add(alreadyPaid, paymentAmount);
        if (paidAfter.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(Invoice.Status.paid);
            invoice.setPaidAmount(invoice.getTotalAmount());
            invoiceRepository.save(invoice);
            auditLogger.log("INVOICE_PAID", "Invoice", invoice.getId().toString(),
                    null, "amount=" + paymentAmount.stripTrailingZeros().toPlainString());
        } else {
            invoice.setStatus(Invoice.Status.partially_paid);
            invoice.setPaidAmount(paidAfter);
            invoiceRepository.save(invoice);
            auditLogger.log("PAYMENT_RECORDED", "Payment", savedPayment.getId().toString(),
                    null, "amount=" + paymentAmount.stripTrailingZeros().toPlainString()
                            + " remaining=" + MoneyUtil.subtract(invoice.getTotalAmount(), paidAfter)
                                    .stripTrailingZeros().toPlainString());
        }

        eventPublisher.publishEvent(new PatientBalanceEvent(invoice.getPatientId()));

        return savedPayment;
    }

    public Payment getPayment(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id.toString()));
    }

    public List<Payment> getInvoicePayments(UUID invoiceId) {
        return paymentRepository.findByInvoiceId(invoiceId);
    }
    private String generatePaymentNumber() {
        return String.format("PAY-%06d", paymentRepository.getNextPaymentSeq());
    }

    private Payment.PaymentMethod parsePaymentMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new BusinessException("Payment method is required");
        }
        try {
            // Enum constants are lowercase (cash, card, upi, ...); accept any case.
            return Payment.PaymentMethod.valueOf(method.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid payment method: " + method);
        }
    }
}
