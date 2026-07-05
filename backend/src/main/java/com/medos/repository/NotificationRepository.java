package com.medos.repository;

import com.medos.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    List<Notification> findByRecipientIdAndReadFalse(UUID recipientId);
    List<Notification> findByRoleTargetOrderByCreatedAtDesc(String roleTarget);
    long countByRecipientIdAndReadFalse(UUID recipientId);
}
