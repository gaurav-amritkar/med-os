package com.medos.repository;

import com.medos.entity.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransaction, UUID> {
    List<StockTransaction> findByMedicineId(UUID medicineId);
    List<StockTransaction> findByBatchId(UUID batchId);
    List<StockTransaction> findByPatientId(UUID patientId);
    List<StockTransaction> findByPrescriptionId(UUID prescriptionId);
    List<StockTransaction> findByTransactionTypeOrderByPerformedAtDesc(StockTransaction.TransactionType type);
}
