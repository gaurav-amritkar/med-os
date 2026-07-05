package com.medos.repository;

import com.medos.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByUserId(UUID userId);
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId);
    List<AuditLog> findByAction(String action);
}
