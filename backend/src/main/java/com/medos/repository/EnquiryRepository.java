package com.medos.repository;

import com.medos.entity.Enquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Enquiries read like the rest of the tenant-owned tables: no tenant predicate in
 * the derived queries — {@code TenantStatementInspector} scopes the SELECT root to
 * the acting tenant, and an unscoped superadmin read is a deliberate act.
 */
@Repository
public interface EnquiryRepository extends JpaRepository<Enquiry, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Enquiry> {

    Optional<Enquiry> findByEnquiryNo(Integer enquiryNo);

    @Query("""
            select max(e.enquiryNo) from Enquiry e
            """)
    Integer findMaxEnquiryNo();

    /**
     * The desk board's counter rail: one group query answering
     * Total / New / Follow-up / Converted / Cancelled at once.
     */
    @Query("""
            select e.status, count(e) from Enquiry e group by e.status
            """)
    List<Object[]> countByStatus();

    @Query("""
            select count(e) from Enquiry e
            """)
    long countAllEnquiries();
}
