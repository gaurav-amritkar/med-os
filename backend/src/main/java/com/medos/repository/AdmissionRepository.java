package com.medos.repository;

import com.medos.entity.Admission;
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
}
