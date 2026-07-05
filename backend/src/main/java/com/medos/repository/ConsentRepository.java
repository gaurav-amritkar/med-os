package com.medos.repository;

import com.medos.entity.Consent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConsentRepository extends JpaRepository<Consent, UUID> {
    List<Consent> findByPatientId(UUID patientId);
    List<Consent> findByPatientIdAndConsentType(UUID patientId, String consentType);
}
