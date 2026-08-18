package com.medos.repository;

import com.medos.entity.Admission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdmissionRepository extends JpaRepository<Admission, UUID> {
    List<Admission> findByPatientId(UUID patientId);
    List<Admission> findByStatus(Admission.Status status);
    Optional<Admission> findByPatientIdAndStatus(UUID patientId, Admission.Status status);
    List<Admission> findByRoomId(UUID roomId);

    Page<Admission> findByPatientId(UUID patientId, Pageable pageable);
    Page<Admission> findByStatus(Admission.Status status, Pageable pageable);
    Page<Admission> findAll(Pageable pageable);
}
