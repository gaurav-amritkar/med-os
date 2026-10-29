package com.medos.modules.clinical.service;

import com.medos.dto.DischargeRequest;
import com.medos.entity.Admission;
import com.medos.entity.DischargeSummary;
import com.medos.entity.Encounter;
import com.medos.entity.LabOrder;
import com.medos.entity.MedicineCatalog;
import com.medos.entity.Prescription;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DischargeSummaryAssemblerTest {

    private final DischargeSummaryAssembler assembler = new DischargeSummaryAssembler();

    @Test
    void assemble_snapshotsAdmissionWindowClinicalDataAndMarksMissingSections() {
        UUID patientId = UUID.randomUUID();
        UUID clinicianId = UUID.randomUUID();
        UUID medicineId = UUID.randomUUID();
        LocalDateTime admittedAt = LocalDateTime.of(2026, 10, 20, 9, 0);
        LocalDateTime signedAt = LocalDateTime.of(2026, 10, 29, 14, 30);

        Admission admission = Admission.builder()
                .id(UUID.randomUUID())
                .patientId(patientId)
                .doctorId(clinicianId)
                .admissionDate(admittedAt)
                .status(Admission.Status.admitted)
                .build();

        Encounter beforeAdmission = Encounter.builder()
                .patientId(patientId)
                .diagnosis("Old diagnosis")
                .vitalsJson("{\"temperature\":37.0}")
                .createdAt(admittedAt.minusDays(1))
                .build();
        Encounter duringAdmission = Encounter.builder()
                .patientId(patientId)
                .diagnosis("Community-acquired pneumonia")
                .clinicalNotes("Improved after antibiotics")
                .vitalsJson("{\"temperature\":38.2,\"spo2\":94}")
                .createdAt(admittedAt.plusHours(2))
                .build();

        Prescription prescription = Prescription.builder()
                .patientId(patientId)
                .medicineId(medicineId)
                .dosage("500 mg")
                .frequency("twice daily")
                .duration("5 days")
                .instructions("after food")
                .prescribedAt(admittedAt.plusDays(1))
                .build();
        MedicineCatalog medicine = MedicineCatalog.builder()
                .id(medicineId)
                .name("Amoxicillin")
                .build();

        LabOrder lab = LabOrder.builder()
                .patientId(patientId)
                .testName("Chest X-ray")
                .status(LabOrder.Status.completed)
                .result("Right lower-zone opacity")
                .orderedAt(admittedAt.plusHours(4))
                .build();

        DischargeRequest request = new DischargeRequest();
        request.setDischargeDiagnosis("Pneumonia, clinically improved");
        request.setConditionAtDischarge("Stable, afebrile, maintaining oxygen saturation");
        request.setFollowUpAdvice("Review in respiratory clinic");
        request.setNextReviewDate(LocalDate.of(2026, 11, 5));

        DischargeSummary result = assembler.assemble(
                admission,
                request,
                signedAt,
                clinicianId,
                "Dr Anita Rao",
                List.of(beforeAdmission, duringAdmission),
                List.of(prescription),
                Map.of(medicineId, medicine),
                List.of(lab));

        assertThat(result.getAdmissionDiagnosis()).isEqualTo("Community-acquired pneumonia");
        assertThat(result.getClinicalCourse()).isEqualTo("Improved after antibiotics");
        assertThat(result.getVitalsSummary()).contains("38.2").doesNotContain("37.0");
        assertThat(result.getMedicationsAtDischarge())
                .isEqualTo("Amoxicillin — 500 mg, twice daily, 5 days; after food");
        assertThat(result.getInvestigationsSummary())
                .isEqualTo("Chest X-ray — Right lower-zone opacity");
        assertThat(result.getProceduresPerformed()).isEqualTo("Not recorded");
        assertThat(result.getDischargeDiagnosis()).isEqualTo("Pneumonia, clinically improved");
        assertThat(result.getConditionAtDischarge()).startsWith("Stable");
        assertThat(result.getFollowUpAdvice()).isEqualTo("Review in respiratory clinic");
        assertThat(result.getNextReviewDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(result.getDischargingClinicianId()).isEqualTo(clinicianId);
        assertThat(result.getDischargingClinicianName()).isEqualTo("Dr Anita Rao");
        assertThat(result.getSignedAt()).isEqualTo(signedAt);
    }
}
