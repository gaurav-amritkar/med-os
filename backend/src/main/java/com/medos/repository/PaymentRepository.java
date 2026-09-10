package com.medos.repository;

import com.medos.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByPaymentNumber(String paymentNumber);
    Optional<Payment> findByTransactionRef(String transactionRef);
    List<Payment> findByInvoiceId(UUID invoiceId);
    List<Payment> findByPatientId(UUID patientId);

    Page<Payment> findByInvoiceId(UUID invoiceId, Pageable pageable);
    Page<Payment> findByPatientId(UUID patientId, Pageable pageable);
    Page<Payment> findAll(Pageable pageable);

    @Query(value = "SELECT nextval('payment_number_seq')", nativeQuery = true)
    Long getNextPaymentSeq();
}
