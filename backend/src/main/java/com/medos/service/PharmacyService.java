package com.medos.service;

import com.medos.dto.DispenseRequest;
import com.medos.entity.*;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.repository.*;
import com.medos.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PharmacyService {

    private final MedicineCatalogRepository medicineCatalogRepository;
    private final MedicineBatchRepository medicineBatchRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final ChargeRepository chargeRepository;
    private final PatientRepository patientRepository;
    private final AuditLogger auditLogger;

    public List<MedicineCatalog> listAllMedicines() {
        return medicineCatalogRepository.findByActiveTrue();
    }

    public MedicineCatalog getMedicine(UUID id) {
        return medicineCatalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine", id.toString()));
    }

    public MedicineCatalog createMedicine(MedicineCatalog med) {
        MedicineCatalog saved = medicineCatalogRepository.save(med);
        auditLogger.log("CREATE", "MedicineCatalog", saved.getId().toString());
        return saved;
    }

    public List<MedicineBatch> getBatches(UUID medicineId) {
        return medicineBatchRepository.findByMedicineId(medicineId);
    }

    @Transactional
    public MedicineBatch addStock(UUID medicineId, String batchNo, LocalDate expiryDate,
                                   int quantity, BigDecimal purchasePrice, String supplier) {
        MedicineBatch batch = MedicineBatch.builder()
                .medicineId(medicineId)
                .batchNo(batchNo)
                .expiryDate(expiryDate)
                .remainingQty(quantity)
                .purchasePrice(purchasePrice)
                .supplier(supplier)
                .receivedDate(LocalDateTime.now())
                .build();
        MedicineBatch saved = medicineBatchRepository.save(batch);

        StockTransaction txn = StockTransaction.builder()
                .medicineId(medicineId)
                .batchId(saved.getId())
                .transactionType(StockTransaction.TransactionType.in)
                .quantity(quantity)
                .referenceNo("PUR-" + batchNo)
                .notes("Stock in: batch " + batchNo)
                .performedAt(LocalDateTime.now())
                .build();
        stockTransactionRepository.save(txn);

        auditLogger.log("STOCK_IN", "MedicineBatch", saved.getId().toString(),
                null, "qty=" + quantity + " batch=" + batchNo);
        return saved;
    }

    @Transactional
    public void dispense(DispenseRequest request) {
        Prescription rx = prescriptionRepository.findById(request.getPrescriptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", request.getPrescriptionId().toString()));
        if (rx.getStatus() != Prescription.Status.pending) {
            throw new BusinessException("Prescription is already " + rx.getStatus());
        }

        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", request.getPatientId().toString()));

        int requiredQty = request.getQuantity();

        // FEFO Algorithm: fetch batches sorted by expiry date, deduct oldest first
        List<MedicineBatch> batches = medicineBatchRepository
                .findAvailableBatchesByFefo(rx.getMedicineId(), LocalDate.now());

        if (batches.isEmpty()) {
            throw new BusinessException("No stock available for medicine");
        }

        int remaining = requiredQty;
        int dispensedFromBatches = 0;

        for (MedicineBatch batch : batches) {
            if (remaining <= 0) break;
            int deduction = Math.min(remaining, batch.getRemainingQty());
            batch.setRemainingQty(batch.getRemainingQty() - deduction);
            remaining -= deduction;
            dispensedFromBatches += deduction;
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
            throw new BusinessException("Insufficient stock: need " + requiredQty
                    + ", available " + (requiredQty - remaining));
        }

        // Update prescription status
        rx.setStatus(Prescription.Status.dispensed);
        prescriptionRepository.save(rx);

        // Auto-generate charge for billing
        MedicineCatalog med = medicineCatalogRepository.findById(rx.getMedicineId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine", rx.getMedicineId().toString()));

        BigDecimal unitPrice = med.getUnitPrice();
        BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(requiredQty));
        BigDecimal gstPercent = BigDecimal.valueOf(5);
        BigDecimal gstAmount = amount.multiply(gstPercent).divide(BigDecimal.valueOf(100));
        BigDecimal totalAmount = amount.add(gstAmount);

        Charge charge = Charge.builder()
                .patientId(request.getPatientId())
                .encounterId(rx.getEncounterId())
                .chargeType(Charge.ChargeType.pharmacy)
                .description(med.getName() + " x" + requiredQty + " (" + rx.getDosage() + ")")
                .quantity(requiredQty)
                .unitPrice(unitPrice)
                .amount(amount)
                .gstPercent(gstPercent)
                .gstAmount(gstAmount)
                .totalAmount(totalAmount)
                .status(Charge.Status.unbilled)
                .build();
        chargeRepository.save(charge);

        // Sync patient outstanding (auto-sync balance)
        syncPatientBalance(request.getPatientId());

        auditLogger.log("DISPENSE", "Prescription", request.getPrescriptionId().toString(),
                "pending", "dispensed qty=" + requiredQty);
    }

    public List<StockTransaction> getStockLedger(UUID medicineId) {
        return stockTransactionRepository.findByMedicineId(medicineId);
    }

    public List<StockTransaction> getAllTransactions() {
        return stockTransactionRepository.findAll();
    }

    private void syncPatientBalance(UUID patientId) {
        List<Charge> unbilled = chargeRepository.findByPatientIdAndStatus(patientId, Charge.Status.unbilled);
        BigDecimal total = unbilled.stream()
                .map(c -> c.getTotalAmount() != null ? c.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient != null) {
            patient.setOutstanding(total);
            patientRepository.save(patient);
        }
    }
}
