package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "room_number", unique = true, nullable = false, length = 32)
    private String roomNumber;

    @Column(nullable = false, length = 64)
    private String ward;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 32)
    private RoomType roomType;

    @Column(name = "daily_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyRate;

    @Column(nullable = false)
    private Integer capacity = 1;

    @Column(nullable = false)
    private Boolean occupied = false;

    private Integer floor = 1;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (capacity == null) capacity = 1;
        if (occupied == null) occupied = false;
        if (floor == null) floor = 1;
    }

    public enum RoomType {
        general, semi_private, private_room, icu, nicu, operation
    }
}
