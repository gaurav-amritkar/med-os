package com.medos.modules.pharmacy.service;

import com.medos.dto.DispenseRequest;
import com.medos.entity.*;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.repository.*;
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

    @Transactional
    public void dispense(DispenseRequest request) {
        Prescription rx = prescriptionRepository.findById(request.getPrescriptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", request.getPrescriptionId().toString()));
        if (rx.getStatus() != Prescription.Status.pending) {
            throw new BusinessException("Prescription is already " + rx.getStatus());
        }

        patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", request.getPatientId().toString()));

        int requiredQty = request.getQuantity();

        // FEFO with row-level locking: fetch batches with PESSIMISTIC_WRITE lock.
        List<MedicineBatch> batches = medicineBatchRepository
                .findAvailableBatchesByFefoForUpdate(rx.getMedicineId(), LocalDate.now());

        if (batches.isEmpty()) {
            throw new BusinessException("No stock available for medicine");
        }

        int totalAvailable = batches.stream().mapToInt(MedicineBatch::getRemainingQty).sum();

        int remaining = requiredQty;

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
                    .performedAt(LocalDateTime.now())
                    .build();
            stockTransactionRepository.save(txn);
        }

        if (remaining > 0) {
            throw new BusinessException("Insufficient stock: required " + requiredQty + ", available " + totalAvailable);
        }

        // Auto-generate charge for billing.
        MedicineCatalog med = medicineCatalogRepository.findById(rx.getMedicineId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine", rx.getMedicineId().toString()));

        BigDecimal unitPrice = med.getUnitPrice();
        BigDecimal[] lineItem = MoneyUtil.calculateLineItem(unitPrice, requiredQty, MoneyUtil.GST_RATE_PHARMACY);

        // Mark prescription as dispensed.
        rx.setStatus(Prescription.Status.dispensed);
        rx.setDispensedAt(LocalDateTime.now());
        prescriptionRepository.save(rx);

        Charge charge = Charge.builder()
                .patientId(request.getPatientId())
                .encounterId(rx.getEncounterId())
                .chargeType(Charge.ChargeType.pharmacy)
                .description(med.getName() + " x" + requiredQty + " (" + rx.getDosage() + ")")
                .quantity(requiredQty)
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
                "pending", "dispensed qty=" + requiredQty);
    }
}
