package com.medos.modules.pharmacy.service;

import com.medos.dto.PageResponse;
import com.medos.entity.*;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.repository.*;
import com.medos.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final MedicineCatalogRepository medicineCatalogRepository;
    private final MedicineBatchRepository medicineBatchRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final AuditLogger auditLogger;

    public MedicineCatalog createMedicine(MedicineCatalog med) {
        MedicineCatalog saved = medicineCatalogRepository.save(med);
        auditLogger.log("MEDICINE_CREATED", "MedicineCatalog", saved.getId().toString(),
                null, "name=" + saved.getName());
        return saved;
    }

    public MedicineCatalog getMedicine(UUID id) {
        return medicineCatalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MedicineCatalog", id.toString()));
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

    public PageResponse<StockTransaction> getStockTransactions(UUID medicineId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (medicineId != null) {
            Page<StockTransaction> result = stockTransactionRepository.findByMedicineId(medicineId, pageable);
            return PageResponse.of(result);
        }
        Page<StockTransaction> result = stockTransactionRepository.findAll(pageable);
        return PageResponse.of(result);
    }

    public List<StockTransaction> getStockLedger(UUID medicineId) {
        return stockTransactionRepository.findByMedicineIdOrderByPerformedAtDesc(medicineId);
    }

    public List<StockTransaction> getAllTransactions() {
        return stockTransactionRepository.findAll();
    }
}