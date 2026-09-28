package com.medos.repository;

import com.medos.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByTenantIdAndUhid(UUID tenantId, String uhid);
    List<Patient> findByTenantId(UUID tenantId);
    Optional<Patient> findByUhid(String uhid);

    @Query("SELECT MAX(CAST(SUBSTRING(p.uhid, 5) AS int)) FROM Patient p WHERE p.uhid LIKE 'UHID%'")
    Optional<Integer> findMaxUhidSequence();

    @Query(value = "SELECT nextval('uhid_seq')", nativeQuery = true)
    Long getNextUhidSeq();

    @Query(value = "SELECT nextval('uhid_seq')", nativeQuery = true)
    Long getNextPatientSeq();

    List<Patient> findByNameContainingIgnoreCase(String name);

    Page<Patient> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Blind-index lookups. The encrypted {@code name} column cannot be matched
     * with LIKE, so search compares a keyed digest of the normalised name, and
     * falls back to the plaintext {@code uhid} for partial matching.
     */
    Page<Patient> findByNameIndexOrderByCreatedAtDescIdDesc(String nameIndex, Pageable pageable);

    Page<Patient> findByUhidContainingIgnoreCaseOrderByCreatedAtDescIdDesc(String uhid, Pageable pageable);

    /** Rows predating the blind index; backfilled on startup. */
    List<Patient> findByNameIndexIsNull();

    /**
     * Newest first, because the unfiltered list had no ORDER BY at all and
     * Postgres therefore returned physical order: a patient registered a moment
     * ago could land on any page, and since neither the patient list nor the OPD
     * picker had pagination, they were simply unreachable. The id is a
     * tiebreaker so the order is total: without it, two patients sharing a
     * created_at can swap between page fetches, so paging both repeats and
     * skips rows.
     */
    Page<Patient> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    List<Patient> findByDpdpConsentFalse();
}
