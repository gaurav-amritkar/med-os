package com.medos.dto;

import com.medos.entity.Appointment;
import com.medos.entity.Patient;
import com.medos.entity.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * The Appointments board row: Token, Appt No., Patient Information, Doctor &
 * Department, Time & Type, Status — resolved server-side (patient names decrypt
 * through the entity's converter; the page never sees ciphertext or raw PII
 * columns).
 */
public record AppointmentDTO(
        UUID id,
        Integer appointmentNo,
        String apptRef,
        Integer tokenNo,
        UUID patientId,
        String patientName,
        String patientUhid,
        Integer patientAge,
        UUID doctorId,
        String doctorName,
        String doctorSpecialization,
        String date,
        String time,
        String type,
        String status,
        String reason,
        String docNote,
        String note,
        LocalDateTime createdAt
) {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ISO_LOCAL_DATE;

    public static AppointmentDTO from(Appointment a, Patient patient, User doctor) {
        return new AppointmentDTO(
                a.getId(),
                a.getAppointmentNo(),
                a.getAppointmentNo() == null ? null : "APT-" + a.getAppointmentNo(),
                a.getTokenNo(),
                a.getPatientId(),
                patient == null ? null : patient.getName(),
                patient == null ? null : patient.getUhid(),
                patient == null ? null : patient.getAge(),
                a.getDoctorId(),
                doctor == null ? null : doctor.getFullName(),
                doctor == null ? null : doctor.getSpecialization(),
                DAY.format(a.getAppointmentDate()),
                TIME.format(a.getAppointmentDate()),
                a.getAppointmentType() == null ? null : a.getAppointmentType().name(),
                a.getStatus() == null ? null : a.getStatus().name(),
                a.getReason(),
                a.getDocNote(),
                a.getReason(),
                a.getCreatedAt()
        );
    }
}
