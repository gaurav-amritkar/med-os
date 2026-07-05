package com.medos.repository;

import com.medos.entity.MedicineBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface MedicineBatchRepository extends JpaRepository<MedicineBatch, UUID> {
    List<MedicineBatch> findByMedicineId(UUID medicineId);

    @Query("SELECT b FROM MedicineBatch b WHERE b.medicineId = :medicineId AND b.remainingQty > 0 AND b.expiryDate > :today ORDER BY b.expiryDate ASC")
    List<MedicineBatch> findAvailableBatchesByFefo(@Param("medicineId") UUID medicineId, @Param("today") LocalDate today);

    @Query("SELECT b FROM MedicineBatch b WHERE b.remainingQty > 0 AND b.expiryDate > :today ORDER BY b.expiryDate ASC")
    List<MedicineBatch> findAllAvailableBatches(@Param("today") LocalDate today);

    @Query("SELECT b FROM MedicineBatch b WHERE b.expiryDate BETWEEN :start AND :end")
    List<MedicineBatch> findExpiringSoon(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
