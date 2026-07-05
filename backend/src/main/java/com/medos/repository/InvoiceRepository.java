package com.medos.repository;

import com.medos.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    List<Invoice> findByPatientId(UUID patientId);
    List<Invoice> findByStatus(Invoice.Status status);
}
