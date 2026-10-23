package com.medos.repository;

import com.medos.entity.Prescription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {
    List<Prescription> findByEncounterId(UUID encounterId);
    List<Prescription> findByPatientId(UUID patientId);
    List<Prescription> findByStatus(Prescription.Status status);
    List<Prescription> findByPatientIdOrderByPrescribedAtDesc(UUID patientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Prescription p WHERE p.id = :id")
    Optional<Prescription> findByIdForUpdate(@Param("id") UUID id);
}
