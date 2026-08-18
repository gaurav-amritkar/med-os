package com.medos.mapper;

import com.medos.dto.*;
import com.medos.entity.*;
import java.util.List;
import java.util.stream.Collectors;

public class EntityDtoMapper {

    public static PatientDTO toDTO(Patient patient) {
        if (patient == null) return null;
        return PatientDTO.builder()
                .id(patient.getId())
                .uhid(patient.getUhid())
                .name(patient.getName())
                .age(patient.getAge())
                .gender(patient.getGender())
                .phone(patient.getPhone())
                .email(patient.getEmail())
                .address(patient.getAddress())
                .bloodGroup(patient.getBloodGroup())
                .dpdpConsent(patient.getDpdpConsent())
                .dpdpConsentAt(patient.getDpdpConsentAt())
                .outstanding(patient.getOutstanding())
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .build();
    }

    public static List<PatientDTO> toPatientDTOList(List<Patient> patients) {
        return patients.stream()
                .map(EntityDtoMapper::toDTO)
                .collect(Collectors.toList());
    }

    public static EncounterDTO toDTO(Encounter encounter) {
        if (encounter == null) return null;
        return EncounterDTO.builder()
                .id(encounter.getId())
                .patientId(encounter.getPatientId())
                .doctorId(encounter.getDoctorId())
                .appointmentId(encounter.getAppointmentId())
                .status(encounter.getStatus() != null ? encounter.getStatus().name() : null)
                .chiefComplaint(encounter.getChiefComplaint())
                .diagnosis(encounter.getDiagnosis())
                .clinicalNotes(encounter.getClinicalNotes())
                .vitalsJson(encounter.getVitalsJson())
                .aiNote(encounter.getAiNote())
                .signedAt(encounter.getSignedAt())
                .signedBy(encounter.getSignedBy())
                .createdAt(encounter.getCreatedAt())
                .build();
    }

    public static List<EncounterDTO> toEncounterDTOList(List<Encounter> encounters) {
        return encounters.stream()
                .map(EntityDtoMapper::toDTO)
                .collect(Collectors.toList());
    }

    public static MedicineCatalogDTO toDTO(MedicineCatalog medicine) {
        if (medicine == null) return null;
        return MedicineCatalogDTO.builder()
                .id(medicine.getId())
                .name(medicine.getName())
                .genericName(medicine.getGenericName())
                .manufacturer(medicine.getManufacturer())
                .category(medicine.getCategory())
                .unit(medicine.getUnit())
                .unitPrice(medicine.getUnitPrice())
                .reorderLevel(medicine.getReorderLevel())
                .keywords(medicine.getKeywords())
                .indications(medicine.getIndications())
                .active(medicine.getActive())
                .createdAt(medicine.getCreatedAt())
                .build();
    }

    public static List<MedicineCatalogDTO> toMedicineCatalogDTOList(List<MedicineCatalog> medicines) {
        return medicines.stream()
                .map(EntityDtoMapper::toDTO)
                .collect(Collectors.toList());
    }

    public static MedicineBatchDTO toDTO(MedicineBatch batch) {
        if (batch == null) return null;
        return MedicineBatchDTO.builder()
                .id(batch.getId())
                .version(batch.getVersion())
                .medicineId(batch.getMedicineId())
                .batchNo(batch.getBatchNo())
                .expiryDate(batch.getExpiryDate())
                .remainingQty(batch.getRemainingQty())
                .purchasePrice(batch.getPurchasePrice())
                .supplier(batch.getSupplier())
                .receivedDate(batch.getReceivedDate())
                .build();
    }

    public static List<MedicineBatchDTO> toMedicineBatchDTOList(List<MedicineBatch> batches) {
        return batches.stream()
                .map(EntityDtoMapper::toDTO)
                .collect(Collectors.toList());
    }

    public static StockTransactionDTO toDTO(StockTransaction txn) {
        if (txn == null) return null;
        return StockTransactionDTO.builder()
                .id(txn.getId())
                .medicineId(txn.getMedicineId())
                .batchId(txn.getBatchId())
                .transactionType(txn.getTransactionType() != null ? txn.getTransactionType().name() : null)
                .quantity(txn.getQuantity())
                .patientId(txn.getPatientId())
                .prescriptionId(txn.getPrescriptionId())
                .referenceNo(txn.getReferenceNo())
                .notes(txn.getNotes())
                .performedBy(txn.getPerformedBy())
                .performedAt(txn.getPerformedAt())
                .build();
    }

    public static List<StockTransactionDTO> toStockTransactionDTOList(List<StockTransaction> transactions) {
        return transactions.stream()
                .map(EntityDtoMapper::toDTO)
                .collect(Collectors.toList());
    }

    public static ChargeDTO toDTO(Charge charge) {
        if (charge == null) return null;
        return ChargeDTO.builder()
                .id(charge.getId())
                .patientId(charge.getPatientId())
                .encounterId(charge.getEncounterId())
                .admissionId(charge.getAdmissionId())
                .chargeType(charge.getChargeType() != null ? charge.getChargeType().name() : null)
                .description(charge.getDescription())
                .quantity(charge.getQuantity())
                .unitPrice(charge.getUnitPrice())
                .amount(charge.getAmount())
                .gstPercent(charge.getGstPercent())
                .gstAmount(charge.getGstAmount())
                .totalAmount(charge.getTotalAmount())
                .invoiceId(charge.getInvoiceId())
                .status(charge.getStatus() != null ? charge.getStatus().name() : null)
                .createdAt(charge.getCreatedAt())
                .build();
    }

    public static List<ChargeDTO> toChargeDTOList(List<Charge> charges) {
        return charges.stream()
                .map(EntityDtoMapper::toDTO)
                .collect(Collectors.toList());
    }

    public static PaymentDTO toDTO(Payment payment) {
        if (payment == null) return null;
        return PaymentDTO.builder()
                .id(payment.getId())
                .paymentNumber(payment.getPaymentNumber())
                .invoiceId(payment.getInvoiceId())
                .patientId(payment.getPatientId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : null)
                .transactionRef(payment.getTransactionRef())
                .status(payment.getStatus() != null ? payment.getStatus().name() : null)
                .receivedBy(payment.getReceivedBy())
                .receivedAt(payment.getReceivedAt())
                .notes(payment.getNotes())
                .build();
    }

    public static List<PaymentDTO> toPaymentDTOList(List<Payment> payments) {
        return payments.stream()
                .map(EntityDtoMapper::toDTO)
                .collect(Collectors.toList());
    }

    public static InvoiceDTO toDTO(Invoice invoice) {
        if (invoice == null) return null;
        return InvoiceDTO.builder()
                .id(invoice.getId())
                .version(invoice.getVersion())
                .invoiceNumber(invoice.getInvoiceNumber())
                .patientId(invoice.getPatientId())
                .invoiceDate(invoice.getInvoiceDate())
                .subtotal(invoice.getSubtotal())
                .gstTotal(invoice.getGstTotal())
                .discount(invoice.getDiscount())
                .totalAmount(invoice.getTotalAmount())
                .paidAmount(invoice.getPaidAmount())
                .status(invoice.getStatus() != null ? invoice.getStatus().name() : null)
                .generatedBy(invoice.getGeneratedBy())
                .notes(invoice.getNotes())
                .createdAt(invoice.getCreatedAt())
                .build();
    }

    public static List<InvoiceDTO> toInvoiceDTOList(List<Invoice> invoices) {
        return invoices.stream()
                .map(EntityDtoMapper::toDTO)
                .collect(Collectors.toList());
    }

    public static AdmissionDTO toDTO(Admission admission) {
        if (admission == null) return null;
        return AdmissionDTO.builder()
                .id(admission.getId())
                .version(admission.getVersion())
                .patientId(admission.getPatientId())
                .roomId(admission.getRoomId())
                .doctorId(admission.getDoctorId())
                .admissionDate(admission.getAdmissionDate())
                .dischargeDate(admission.getDischargeDate())
                .status(admission.getStatus() != null ? admission.getStatus().name() : null)
                .dischargeDiagnosis(admission.getDischargeDiagnosis())
                .roomCharges(admission.getRoomCharges())
                .daysAdmitted(admission.getDaysAdmitted())
                .createdAt(admission.getCreatedAt())
                .build();
    }

    public static List<AdmissionDTO> toAdmissionDTOList(List<Admission> admissions) {
        return admissions.stream()
                .map(EntityDtoMapper::toDTO)
                .collect(Collectors.toList());
    }

    public static RoomDTO toDTO(Room room) {
        if (room == null) return null;
        return RoomDTO.builder()
                .id(room.getId())
                .version(room.getVersion())
                .roomNumber(room.getRoomNumber())
                .ward(room.getWard())
                .roomType(room.getRoomType() != null ? room.getRoomType().name() : null)
                .dailyRate(room.getDailyRate())
                .capacity(room.getCapacity())
                .occupied(room.getOccupied())
                .floor(room.getFloor())
                .notes(room.getNotes())
                .createdAt(room.getCreatedAt())
                .build();
    }

    public static List<RoomDTO> toRoomDTOList(List<Room> rooms) {
        return rooms.stream()
                .map(EntityDtoMapper::toDTO)
                .collect(Collectors.toList());
    }

    public static PrescriptionDTO toDTO(Prescription prescription) {
        if (prescription == null) return null;
        return PrescriptionDTO.builder()
                .id(prescription.getId())
                .encounterId(prescription.getEncounterId())
                .patientId(prescription.getPatientId())
                .medicineId(prescription.getMedicineId())
                .dosage(prescription.getDosage())
                .frequency(prescription.getFrequency())
                .duration(prescription.getDuration())
                .instructions(prescription.getInstructions())
                .status(prescription.getStatus() != null ? prescription.getStatus().name() : null)
                .prescribedBy(prescription.getPrescribedBy())
                .prescribedAt(prescription.getPrescribedAt())
                .build();
    }

    public static List<PrescriptionDTO> toPrescriptionDTOList(List<Prescription> prescriptions) {
        return prescriptions.stream()
                .map(EntityDtoMapper::toDTO)
                .collect(Collectors.toList());
    }
}