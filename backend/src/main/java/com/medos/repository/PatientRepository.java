package com.medos.repository;

import com.medos.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByUhid(String uhid);

    @Query("SELECT MAX(CAST(SUBSTRING(p.uhid, 5) AS int)) FROM Patient p WHERE p.uhid LIKE 'UHID%'")
    Optional<Integer> findMaxUhidSequence();

    List<Patient> findByNameContainingIgnoreCase(String name);

    List<Patient> findByDpdpConsentFalse();
}
