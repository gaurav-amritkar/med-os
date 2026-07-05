package com.medos.repository;

import com.medos.entity.Charge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChargeRepository extends JpaRepository<Charge, UUID> {
    List<Charge> findByPatientId(UUID patientId);
    List<Charge> findByInvoiceId(UUID invoiceId);
    List<Charge> findByStatus(Charge.Status status);
    List<Charge> findByPatientIdAndStatus(UUID patientId, Charge.Status status);
    List<Charge> findByEncounterId(UUID encounterId);
}
