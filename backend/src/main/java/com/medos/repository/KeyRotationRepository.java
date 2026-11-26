package com.medos.repository;

import com.medos.entity.KeyRotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KeyRotationRepository extends JpaRepository<KeyRotation, UUID> {

    // Encryption converters invoke this lookup during Hibernate flush. A nested
    // auto-flush would modify the action queue while the original flush iterates it.
    @org.springframework.data.jpa.repository.QueryHints(
            @jakarta.persistence.QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    java.util.Optional<KeyRotation> findFirstByOperationAndTenantIdOrderByStartedAtDesc(String operation, UUID tenantId);

    /**
     * Every run for one tenant, newest first. A DEK rotation is resumable, so the
     * service reads this to find an unfinished run before starting a new one; two
     * concurrent runs would each advance the generation and leave the other's target
     * unreachable.
     */
    List<KeyRotation> findByOperationAndTenantIdOrderByStartedAtDesc(String operation, UUID tenantId);
}
