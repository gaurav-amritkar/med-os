package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "disease_medicine_map")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiseaseMedicineMap {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "disease_keyword", unique = true, nullable = false, length = 128)
    private String diseaseKeyword;

    @Column(name = "medicine_id", nullable = false, columnDefinition = "uuid")
    private UUID medicineId;

    @Column(length = 64)
    private String dosage;

    @Column(length = 64)
    private String frequency;

    private Integer priority = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (priority == null) priority = 1;
    }
}
