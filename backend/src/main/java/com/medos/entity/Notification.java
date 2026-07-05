package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "recipient_id", columnDefinition = "uuid")
    private UUID recipientId;

    @Column(name = "role_target", length = 64)
    private String roleTarget;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Type type = Type.info;

    @Column(nullable = false)
    private Boolean read = false;

    @Column(length = 255)
    private String link;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (read == null) read = false;
        if (type == null) type = Type.info;
    }

    public enum Type {
        info, warning, critical, success
    }
}
