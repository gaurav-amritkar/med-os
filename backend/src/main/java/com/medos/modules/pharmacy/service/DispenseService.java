package com.medos.modules.pharmacy.service;

import com.medos.dto.DispenseRequest;
import com.medos.entity.*;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.repository.*;
import com.medos.security.CurrentUserProvider;
import com.medos.util.AuditLogger;
import com.medos.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * FEFO dispensing workflow — the pharmacy's write-side use case.
 *
 * <p>Validates the prescription and patient, deducts stock first-expiry-first-out
 * under a row-level lock ({@code findAvailableBatchesByFefoForUpdate}), posts the
 * pharmacy charge with GST, and publishes {@link com.medos.modules.billing.event.PatientBalanceEvent}
 * so the patient's outstanding balance is refreshed.
 *
 * <p>Extracted from the former god {@code PharmacyService}; catalog queries moved
 * to {@link MedicineCatalogService}, stock/batch ops to {@link InventoryService},
 * prescription status handling to {@link PrescriptionService}.
 */
@Service
@RequiredArgsConstructor
public class DispenseService {

    private final MedicineBatchRepository medicineBatchRepository;
    private final MedicineCatalogRepository medicineCatalogRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final ChargeRepository chargeRepository;
    private final PatientRepository patientRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogger auditLogger;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public void dispense(DispenseRequest request) {
        Prescription rx = prescriptionRepository.findByIdForUpdate(request.getPrescriptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", request.getPrescriptionId().toString()));
        if (rx.getStatus() != Prescription.Status.pending
                && rx.getStatus() != Prescription.Status.partially_dispensed) {
            throw new BusinessException("Prescription is already " + rx.getStatus());
        }

        // The patient on the request must be the patient the prescription belongs
        // to. Checking only that the requested patient exists is not enough: it
        // lets stock leave against one patient and the charge land on another,
        // which is both a clinical record and a financial integrity defect (#222).
        if (rx.getPatientId() != null && !rx.getPatientId().equals(request.getPatientId())) {
            throw new BusinessException("Patient " + request.getPatientId()
                    + " does not belong to this prescription");
        }

        patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", request.getPatientId().toString()));

        // The quantity is capped three ways, in order of authority:
        //
        //   1. what the caller asked for,
        //   2. what the prescription has left to give — quantity minus what has
        //      already been dispensed,
        //   3. what stock actually exists.
        //
        // The second bound is the one that was missing (#160): prescribed 10,
        // already dispensed 8, a request for 10 was honoured in full, so 18 units
        // left the pharmacy against a prescription for 10. Stock is checked after
        // this, not instead of it.
        //
        // It is resolved before the batch query, so a prescription that has
        // nothing left to give costs no locked read.
        int requiredQty = request.getQuantity();
        Integer prescribedQty = rx.getQuantity();
        int alreadyDispensed = rx.getDispensedQty() == null ? 0 : rx.getDispensedQty();

        int authorizedQty = requiredQty;
        if (prescribedQty != null) {
            int remaining = prescribedQty - alreadyDispensed;
            if (remaining <= 0) {
                throw new BusinessException("Prescription is already fully dispensed");
            }
            authorizedQty = Math.min(authorizedQty, remaining);
        }

        // FEFO with row-level locking: fetch batches with PESSIMISTIC_WRITE lock.
        List<MedicineBatch> batches = medicineBatchRepository
                .findAvailableBatchesByFefoForUpdate(rx.getMedicineId(), LocalDate.now());

        if (batches.isEmpty()) {
            throw new BusinessException("No stock available for medicine");
        }

        int totalAvailable = batches.stream().mapToInt(MedicineBatch::getRemainingQty).sum();

        // Partial dispensing: dispense up to the authorized quantity, limited by
        // what stock is available. Never throw just because the full request
        // cannot be fulfilled — dispense what we can and mark the prescription
        // accordingly. Atomicity is preserved: we never fail partway through
        // the batch loop.
        int toDispense = Math.min(authorizedQty, totalAvailable);
        if (toDispense <= 0) {
            throw new BusinessException("No stock available for medicine");
        }

        int remaining = toDispense;

        for (MedicineBatch batch : batches) {
            if (remaining <= 0) break;
            int deduction = Math.min(remaining, batch.getRemainingQty());
            batch.setRemainingQty(batch.getRemainingQty() - deduction);
            remaining -= deduction;
            medicineBatchRepository.save(batch);

            StockTransaction txn = StockTransaction.builder()
                    .medicineId(rx.getMedicineId())
                    .batchId(batch.getId())
                    .transactionType(StockTransaction.TransactionType.out)
                    .quantity(-deduction)
                    .patientId(request.getPatientId())
                    .prescriptionId(request.getPrescriptionId())
                    .notes(request.getNotes())
                    .performedBy(currentUserProvider.getCurrentUserId())
                    .performedAt(LocalDateTime.now())
                    .build();
            stockTransactionRepository.save(txn);
        }

        // `remaining` is 0 once the available stock is exhausted.
        assert remaining == 0 : "Expected all available stock to be deducted; remaining=" + remaining;

        // Auto-generate charge for billing.
        MedicineCatalog med = medicineCatalogRepository.findById(rx.getMedicineId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine", rx.getMedicineId().toString()));

        BigDecimal unitPrice = med.getUnitPrice();
        BigDecimal[] lineItem = MoneyUtil.calculateLineItem(unitPrice, toDispense, MoneyUtil.GST_RATE_PHARMACY);

        // Update cumulative dispensed quantity and transition status.
        Integer totalQty = rx.getQuantity();
        int dispensedQty = (rx.getDispensedQty() == null ? 0 : rx.getDispensedQty()) + toDispense;
        rx.setDispensedQty(dispensedQty);
        rx.setDispensedAt(LocalDateTime.now());

        if (totalQty != null && dispensedQty >= totalQty) {
            rx.setStatus(Prescription.Status.dispensed);
        } else if (totalQty != null) {
            rx.setStatus(Prescription.Status.partially_dispensed);
        }
        // Legacy prescriptions (no quantity set) keep the previous behaviour.
        else {
            rx.setStatus(Prescription.Status.dispensed);
        }

        prescriptionRepository.save(rx);

        Charge charge = Charge.builder()
                .patientId(request.getPatientId())
                .encounterId(rx.getEncounterId())
                .chargeType(Charge.ChargeType.pharmacy)
                .description(med.getName() + " x" + toDispense + " (" + rx.getDosage() + ")")
                .quantity(toDispense)
                .unitPrice(unitPrice)
                .amount(lineItem[0])
                .gstPercent(MoneyUtil.GST_RATE_PHARMACY)
                .gstAmount(lineItem[1])
                .totalAmount(lineItem[2])
                .status(Charge.Status.unbilled)
                .build();
        chargeRepository.save(charge);

        // Sync patient outstanding (auto-sync balance).
        eventPublisher.publishEvent(new com.medos.modules.billing.event.PatientBalanceEvent(request.getPatientId()));

        auditLogger.log("DISPENSE", "Prescription", request.getPrescriptionId().toString(),
                "pending", "dispensed qty=" + toDispense);
    }
}
