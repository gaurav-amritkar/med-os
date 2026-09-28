package com.medos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncounterDTO {
    private UUID id;
    private UUID patientId;
    /**
     * Patient name and UHID, resolved for display only. Patient PII is encrypted
     * at rest, so a worklist cannot be assembled from an encounter row alone —
     * these are denormalised at read time and are never persisted on the
     * encounter.
     */
    private String patientName;
    private String patientUhid;
    private UUID doctorId;
    private UUID appointmentId;
    private String status;
    private String chiefComplaint;
    private String diagnosis;
    private String clinicalNotes;
    private String vitalsJson;
    private String aiNote;
    private LocalDateTime signedAt;
    private UUID signedBy;
    private LocalDateTime createdAt;
}