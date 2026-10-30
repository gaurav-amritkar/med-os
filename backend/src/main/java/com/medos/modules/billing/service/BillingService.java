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
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
            charges = chargeRepository.findByPatientIdAndStatusForUpdate(request.getPatientId(), Charge.Status.unbilled);
            if (charges.isEmpty()) {
                throw new BusinessException("No unbilled charges found for patient");
            }
        }

        BigDecimal subtotal = charges.stream()
                .map(Charge::getAmount)
                .reduce(BigDecimal.ZERO, MoneyUtil::add);
        BigDecimal requestedDiscount = request.getDiscount() == null ? BigDecimal.ZERO : request.getDiscount();
        BigDecimal discount = MoneyUtil.normalize(requestedDiscount);
        if (requestedDiscount.compareTo(BigDecimal.ZERO) < 0 || discount.compareTo(subtotal) > 0) {
            throw new BusinessException("Invoice discount must be between 0 and the subtotal");
        }

        BigDecimal gstTotal;
        BigDecimal totalAmount;
        if (discount.compareTo(BigDecimal.ZERO) == 0) {
            // Preserve the charge ledger's established rounding for undiscounted invoices.
            gstTotal = charges.stream()
                    .map(Charge::getGstAmount)
                    .reduce(BigDecimal.ZERO, MoneyUtil::add);
            totalAmount = charges.stream()
                    .map(Charge::getTotalAmount)
                    .reduce(BigDecimal.ZERO, MoneyUtil::add);
        } else {
            gstTotal = calculateDiscountedGst(charges, subtotal, discount);
            totalAmount = MoneyUtil.add(MoneyUtil.subtract(subtotal, discount), gstTotal);
        }

        Invoice invoice = Invoice.builder()
                .invoiceNumber(generateInvoiceNumber())
                .patientId(request.getPatientId())
                .tenantId(patient.getTenantId())
                .invoiceDate(LocalDateTime.now())
                .subtotal(subtotal)
                .gstTotal(gstTotal)
                .discount(discount)
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
                null, "amount=" + totalAmount.stripTrailingZeros().toPlainString()
                        + " discount=" + discount.stripTrailingZeros().toPlainString()
                        + " charges=" + charges.size());

        // Balance counts billed|paid charges vs payments — invoicing moves charges
        // into the counted set, so refresh the patient's outstanding balance.
        eventPublisher.publishEvent(new PatientBalanceEvent(request.getPatientId()));

        return savedInvoice;
    }

    /**
     * Applies a flat invoice discount proportionally across GST-rate groups,
     * then recalculates GST on each adjusted group base. Largest-remainder
     * allocation assigns every discount paise exactly once without making a
     * taxable base negative.
     */
    private BigDecimal calculateDiscountedGst(List<Charge> charges, BigDecimal subtotal, BigDecimal discount) {
        Map<BigDecimal, BigDecimal> basesByRate = new TreeMap<>(BigDecimal::compareTo);
        for (Charge charge : charges) {
            BigDecimal rate = charge.getGstPercent() == null ? BigDecimal.ZERO : charge.getGstPercent();
            basesByRate.merge(rate, charge.getAmount(), MoneyUtil::add);
        }

        long subtotalCents = subtotal.movePointRight(2).longValueExact();
        long discountCents = discount.movePointRight(2).longValueExact();
        BigInteger denominator = BigInteger.valueOf(subtotalCents);
        List<GroupDiscountShare> shares = new ArrayList<>();
        long allocatedCents = 0;
        for (Map.Entry<BigDecimal, BigDecimal> entry : basesByRate.entrySet()) {
            long baseCents = entry.getValue().movePointRight(2).longValueExact();
            BigInteger[] share = BigInteger.valueOf(discountCents)
                    .multiply(BigInteger.valueOf(baseCents))
                    .divideAndRemainder(denominator);
            long floorCents = share[0].longValueExact();
            allocatedCents += floorCents;
            shares.add(new GroupDiscountShare(entry.getKey(), entry.getValue(), floorCents, share[1]));
        }

        long remainingCents = discountCents - allocatedCents;
        shares.sort(Comparator.comparing(GroupDiscountShare::remainder).reversed()
                .thenComparing(GroupDiscountShare::rate));
        Map<BigDecimal, Long> allocatedByRate = new TreeMap<>(BigDecimal::compareTo);
        for (int i = 0; i < shares.size(); i++) {
            GroupDiscountShare share = shares.get(i);
            long groupDiscountCents = share.floorCents() + (i < remainingCents ? 1 : 0);
            allocatedByRate.put(share.rate(), groupDiscountCents);
        }

        BigDecimal gstTotal = BigDecimal.ZERO;
        for (GroupDiscountShare share : shares) {
            BigDecimal allocatedDiscount = BigDecimal.valueOf(allocatedByRate.get(share.rate()), 2);
            BigDecimal discountedBase = MoneyUtil.subtract(share.base(), allocatedDiscount);
            BigDecimal rate = share.rate();
            BigDecimal lineGst = discountedBase.multiply(rate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            gstTotal = MoneyUtil.add(gstTotal, lineGst);
        }
        return gstTotal;
    }

    private record GroupDiscountShare(BigDecimal rate, BigDecimal base, long floorCents, BigInteger remainder) {}

    /**
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
