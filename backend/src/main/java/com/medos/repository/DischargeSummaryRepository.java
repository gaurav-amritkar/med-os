package com.medos.repository;

import com.medos.entity.DischargeSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DischargeSummaryRepository extends JpaRepository<DischargeSummary, UUID> {
    Optional<DischargeSummary> findByAdmissionId(UUID admissionId);
    boolean existsByAdmissionId(UUID admissionId);
}
