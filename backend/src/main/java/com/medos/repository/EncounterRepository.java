package com.medos.repository;

import com.medos.entity.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface EncounterRepository extends JpaRepository<Encounter, UUID> {
    List<Encounter> findByPatientId(UUID patientId);
    List<Encounter> findByDoctorId(UUID doctorId);
    List<Encounter> findByStatus(Encounter.Status status);
    List<Encounter> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
