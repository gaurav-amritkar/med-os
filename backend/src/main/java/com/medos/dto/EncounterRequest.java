package com.medos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class EncounterRequest {
    @NotNull
    private UUID patientId;

    private String chiefComplaint;
    private String diagnosis;
    private String clinicalNotes;

    @NotNull
    private Map<String, Object> vitals;
}
