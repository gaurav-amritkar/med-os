package com.medos.repository;

import com.medos.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    List<Notification> findByRecipientIdAndReadFalse(UUID recipientId);
    List<Notification> findByRoleTargetOrderByCreatedAtDesc(String roleTarget);
    long countByRecipientIdAndReadFalse(UUID recipientId);

    /**
     * Scoped lookup for writes. A notification is mutable only by its recipient,
     * and {@code notifications} is not in the statement inspector's tenant table
     * set, so nothing else narrows the row. Callers that mutate must use this
     * rather than {@code findById}.
     */
    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);
}
