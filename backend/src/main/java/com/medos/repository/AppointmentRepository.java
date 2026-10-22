package com.medos.repository;

import com.medos.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Appointment reads rely on the inspector's tenant scoping like every other
 * tenant-owned table; the series queries below bound the per-tenant serials
 * (Appt No. overall, token within one day) that the numbering policy races on.
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID>,
        JpaSpecificationExecutor<Appointment> {

    List<Appointment> findByPatientId(UUID patientId);

    List<Appointment> findByDoctorId(UUID doctorId);

    List<Appointment> findByStatus(Appointment.Status status);

    List<Appointment> findByAppointmentDateBetween(LocalDateTime start, LocalDateTime end);

    List<Appointment> findByDoctorIdAndAppointmentDateBetween(UUID doctorId,
                                                              LocalDateTime start, LocalDateTime end);

    @Query("select max(a.appointmentNo) from Appointment a")
    Integer findMaxAppointmentNo();

    /** The day's largest token — tomorrow restarts at 1 (V8's per-day index). */
    @Query("select max(a.tokenNo) from Appointment a where a.tokenDate = :day")
    Integer findMaxTokenNoOn(@Param("day") LocalDate day);

    @Query("select a.status, count(a) from Appointment a "
            + "where a.appointmentDate >= :dayStart and a.appointmentDate < :dayEnd "
            + "group by a.status")
    List<Object[]> countTodayByStatus(@Param("dayStart") LocalDateTime dayStart,
                                      @Param("dayEnd") LocalDateTime dayEnd);

    @Query("select count(a) from Appointment a "
            + "where a.appointmentDate >= :dayStart and a.appointmentDate < :dayEnd")
    long countToday(@Param("dayStart") LocalDateTime dayStart,
                    @Param("dayEnd") LocalDateTime dayEnd);
}
