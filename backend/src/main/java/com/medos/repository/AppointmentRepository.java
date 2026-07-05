package com.medos.repository;

import com.medos.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    List<Appointment> findByPatientId(UUID patientId);
    List<Appointment> findByDoctorId(UUID doctorId);
    List<Appointment> findByStatus(Appointment.Status status);
    List<Appointment> findByAppointmentDateBetween(LocalDateTime start, LocalDateTime end);
    List<Appointment> findByDoctorIdAndAppointmentDateBetween(UUID doctorId, LocalDateTime start, LocalDateTime end);
}
