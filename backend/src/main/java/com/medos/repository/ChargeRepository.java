package com.medos.repository;

import com.medos.entity.Charge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<Charge> findByPatientId(UUID patientId, Pageable pageable);
    Page<Charge> findByPatientIdAndStatus(UUID patientId, Charge.Status status, Pageable pageable);
    Page<Charge> findByInvoiceId(UUID invoiceId, Pageable pageable);
    Page<Charge> findAll(Pageable pageable);
}
