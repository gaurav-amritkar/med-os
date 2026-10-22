package com.medos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One front-desk enquiry, per the MedWorks Enquiry Desk grammar: Enquiry #,
 * Contact, Reason/Department, Status. A person who phoned or walked in before
 * becoming a patient.
 *
 * <p>Contact fields (name/phone/email) are business-card data, deliberately not
 * routed through the PII encryption path — no clinical context lives here. The
 * desk's lifecycle (new → follow_up → converted/cancelled) is audited row by row
 * in {@link EnquiryStatusHistory}; a conversion may later point at the patient
 * it became (Patients Hub deepening, this wave).
 */
@Entity
@EntityListeners(TenantEntityListener.class)
@Table(name = "enquiries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enquiry implements TenantOwned {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", columnDefinition = "uuid")
    private UUID tenantId;

    /** Per-tenant serial; the page renders it as ENQ-&lt;no&gt; */
    @Column(name = "enquiry_no", nullable = false)
    private Integer enquiryNo;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "enquiry_type", nullable = false, length = 32)
    private EnquiryType enquiryType;

    @Column(name = "department")
    private String department;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EnquiryStatus status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** Set when the enquiry converts to a registered patient. */
    @Column(name = "converted_patient_id", columnDefinition = "uuid")
    private UUID convertedPatientId;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = EnquiryStatus.new_enquiry;
        }
        if (enquiryType == null) {
            enquiryType = EnquiryType.general;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** The enquiry's state on the desk board. */
    public enum EnquiryStatus { new_enquiry, follow_up, converted, cancelled }

    /** Why they came, per the MedWorks "Reason/Department" column's type axis. */
    public enum EnquiryType { general, appointment, admission, emergency, pharmacy, other }
}
