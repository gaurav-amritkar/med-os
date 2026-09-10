package com.medos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    private String name;

    @Enumerated(EnumType.STRING)
    private TenantType type;

    private String slug;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private Boolean active;

    @ElementCollection
    private Map<String, String> config;

    public enum TenantType {
        HOSPITAL, CLINIC, INDIVIDUAL_PRACTITIONER, PHARMACY
    }
}