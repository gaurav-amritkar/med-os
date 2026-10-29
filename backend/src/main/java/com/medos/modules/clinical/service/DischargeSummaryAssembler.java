package com.medos.modules.clinical.service;

import com.medos.dto.DischargeRequest;
import com.medos.entity.Admission;
import com.medos.entity.DischargeSummary;
import com.medos.entity.Encounter;
import com.medos.entity.LabOrder;
import com.medos.entity.MedicineCatalog;
import com.medos.entity.Prescription;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

@Component
public class DischargeSummaryAssembler {

    static final String NOT_RECORDED = "Not recorded";

    public DischargeSummary assemble(
            Admission admission,
            DischargeRequest request,
            LocalDateTime signedAt,
            UUID clinicianId,
            String clinicianName,
            List<Encounter> encounters,
            List<Prescription> prescriptions,
            Map<UUID, MedicineCatalog> medicines,
            List<LabOrder> labOrders) {

        LocalDateTime admissionStart = admission.getAdmissionDate();
        List<Encounter> admissionEncounters = encounters.stream()
                .filter(e -> withinAdmission(e.getCreatedAt(), admissionStart, signedAt))
                .toList();
        List<Prescription> admissionPrescriptions = prescriptions.stream()
                .filter(p -> withinAdmission(p.getPrescribedAt(), admissionStart, signedAt))
                .toList();
        List<LabOrder> admissionLabs = labOrders.stream()
                .filter(l -> withinAdmission(l.getOrderedAt(), admissionStart, signedAt))
                .toList();

        return DischargeSummary.builder()
                .tenantId(admission.getTenantId())
                .admissionId(admission.getId())
                .patientId(admission.getPatientId())
                .dischargingClinicianId(clinicianId)
                .dischargingClinicianName(valueOrNotRecorded(clinicianName))
                .admissionDiagnosis(prefer(request.getAdmissionDiagnosis(), join(admissionEncounters, Encounter::getDiagnosis)))
                .dischargeDiagnosis(request.getDischargeDiagnosis().trim())
                .clinicalCourse(prefer(request.getClinicalCourse(), join(admissionEncounters, Encounter::getClinicalNotes)))
                .proceduresPerformed(valueOrNotRecorded(request.getProceduresPerformed()))
                .investigationsSummary(prefer(request.getInvestigationsSummary(), summarizeLabs(admissionLabs)))
                .vitalsSummary(join(admissionEncounters, Encounter::getVitalsJson))
                .medicationsAtDischarge(prefer(request.getMedicationsAtDischarge(),
                        summarizeMedications(admissionPrescriptions, medicines)))
                .conditionAtDischarge(request.getConditionAtDischarge().trim())
                .followUpAdvice(request.getFollowUpAdvice().trim())
                .nextReviewDate(request.getNextReviewDate())
                .signedAt(signedAt)
                .build();
    }

    private boolean withinAdmission(LocalDateTime timestamp, LocalDateTime start, LocalDateTime end) {
        if (timestamp == null) return false;
        return (start == null || !timestamp.isBefore(start)) && !timestamp.isAfter(end);
    }

    private <T> String join(List<T> values, Function<T, String> extractor) {
        String joined = values.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
        return valueOrNotRecorded(joined);
    }

    private String summarizeMedications(List<Prescription> prescriptions, Map<UUID, MedicineCatalog> medicines) {
        String joined = prescriptions.stream()
                .filter(p -> p.getStatus() != Prescription.Status.cancelled)
                .map(p -> {
                    MedicineCatalog medicine = medicines.get(p.getMedicineId());
                    String name = medicine == null ? "Medicine not recorded" : medicine.getName();
                    String regimen = List.of(p.getDosage(), p.getFrequency(), p.getDuration()).stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(value -> !value.isEmpty())
                            .reduce((left, right) -> left + ", " + right)
                            .orElse(NOT_RECORDED);
                    String instructions = isBlank(p.getInstructions()) ? "" : "; " + p.getInstructions().trim();
                    return name + " — " + regimen + instructions;
                })
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
        return valueOrNotRecorded(joined);
    }

    private String summarizeLabs(List<LabOrder> labOrders) {
        String joined = labOrders.stream()
                .map(lab -> lab.getTestName() + " — "
                        + (isBlank(lab.getResult()) ? "Result not recorded (" + lab.getStatus().name() + ")" : lab.getResult().trim()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
        return valueOrNotRecorded(joined);
    }

    private String prefer(String supplied, String aggregated) {
        return isBlank(supplied) ? valueOrNotRecorded(aggregated) : supplied.trim();
    }

    private String valueOrNotRecorded(String value) {
        return isBlank(value) ? NOT_RECORDED : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
