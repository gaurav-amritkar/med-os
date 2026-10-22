package com.medos.modules.appointment.service;

import com.medos.dto.AppointmentDTO;
import com.medos.dto.AppointmentNoteRequest;
import com.medos.dto.BookAppointmentRequest;
import com.medos.dto.PageResponse;
import com.medos.dto.UpdateAppointmentStatusRequest;
import com.medos.entity.Appointment;
import com.medos.entity.Patient;
import com.medos.entity.User;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.repository.AppointmentRepository;
import com.medos.repository.PatientRepository;
import com.medos.repository.UserRepository;
import com.medos.security.CurrentUserProvider;
import com.medos.security.TenantContext;
import com.medos.util.AuditLogger;
import com.medos.util.PerTenantSerial;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The Appointments board's operations: book, list today with filters, issue the
 * per-day token at check-in, move through the lifecycle, write the doctor's
 * note, and delete only terminal rows (cancelled/no-show).
 *
 * <p>Two independent per-tenant serials live here:
 * <ul>
 *   <li>{@code appointment_no} — the overall per-tenant Appt No. (APT-1...).</li>
 *   <li>{@code token_no} within {@code token_date} — the day's queue number,
 *       resets every morning (the {@code uq_appointments_tenant_day_token}
 *       index). Issued at check-in, not at booking.</li>
 * </ul>
 * Both use {@link PerTenantSerial.assign} so a concurrent double-claim fails
 * loud and retries; the desk is not a high-contention surface.
 */
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final List<String> LIFECYCLE = List.of(
            "scheduled", "checked_in", "in_consultation", "completed", "cancelled", "no_show");

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AuditLogger auditLogger;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate txTemplate;

    /** Book an appointment (no token yet — that happens at check-in). */
    public AppointmentDTO book(BookAppointmentRequest request) {
        UUID tenantId = tenant();
        return PerTenantSerial.assign(txTemplate,
                appointmentRepository::findMaxAppointmentNo,
                apptNo -> {
                    Appointment a = Appointment.builder()
                            .tenantId(tenantId)
                            .patientId(request.patientId())
                            .doctorId(request.doctorId())
                            .appointmentDate(LocalDateTime.parse(request.appointmentDate()))
                            .status(Appointment.Status.scheduled)
                            .appointmentType(Appointment.Type.valueOf(request.type()))
                            .reason(request.reason().trim())
                            .createdBy(currentUserProvider.getCurrentUserId())
                            .appointmentNo(apptNo)
                            .build();
                    Appointment saved = appointmentRepository.saveAndFlush(a);
                    auditLogger.log("APPOINTMENT_BOOKED", "Appointment", saved.getId().toString(),
                            null, "apptNo=" + apptNo);
                    return toDto(saved);
                });
    }

    /** List today's appointments with filters. */
    public PageResponse<AppointmentDTO> listToday(String status, String doctorId, String type,
                                                  int page, int size) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        validateFilters(status, doctorId, type);

        Specification<Appointment> spec = (root, query, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.between(root.get("appointmentDate"), start, end));
            if (status != null && !status.isBlank()) {
                preds.add(cb.equal(root.get("status"),
                        Appointment.Status.valueOf(status.trim().toLowerCase(Locale.ROOT))));
            }
            if (doctorId != null && !doctorId.isBlank()) {
                preds.add(cb.equal(root.get("doctorId"), UUID.fromString(doctorId)));
            }
            if (type != null && !type.isBlank()) {
                preds.add(cb.equal(root.get("appointmentType"),
                        Appointment.Type.valueOf(type.trim().toLowerCase(Locale.ROOT))));
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.ASC, "appointmentDate"));
        Page<Appointment> result = appointmentRepository.findAll(spec, pageable);
        return PageResponse.of(result.map(this::toDto));
    }

    /** Today's counter rail: total / scheduled / checked_in / in_consultation / completed / cancelled / no_show. */
    public BoardSummary summaryToday() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        long total = appointmentRepository.countToday(start, end);
        var byStatus = new java.util.EnumMap<Appointment.Status, Long>(Appointment.Status.class);
        for (Object[] row : appointmentRepository.countTodayByStatus(start, end)) {
            byStatus.merge((Appointment.Status) row[0], (Long) row[1], Long::sum);
        }
        return new BoardSummary(
                total,
                byStatus.getOrDefault(Appointment.Status.scheduled, 0L),
                byStatus.getOrDefault(Appointment.Status.checked_in, 0L),
                byStatus.getOrDefault(Appointment.Status.in_consultation, 0L),
                byStatus.getOrDefault(Appointment.Status.completed, 0L),
                byStatus.getOrDefault(Appointment.Status.cancelled, 0L),
                byStatus.getOrDefault(Appointment.Status.no_show, 0L)
        );
    }

    public record BoardSummary(long total, long scheduled, long checkedIn, long inConsultation,
                               long completed, long cancelled, long noShow) {}

    /** Issue the day's token at check-in; if already issued, no-op (idempotent). */
    @Transactional
    public AppointmentDTO checkIn(UUID id) {
        UUID tenantId = tenant();
        Appointment a = findById(id);
        if (a.getTokenNo() != null) return toDto(a);
        if (a.getStatus() != Appointment.Status.scheduled) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Only scheduled appointments can be checked in (current: " + a.getStatus() + ")");
        }
        return PerTenantSerial.assign(txTemplate,
                () -> appointmentRepository.findMaxTokenNoOn(LocalDate.now()),
                tokenNo -> {
                    a.setTokenNo(tokenNo);
                    a.setTokenDate(LocalDate.now());
                    a.setStatus(Appointment.Status.checked_in);
                    Appointment saved = appointmentRepository.save(a);
                    auditLogger.log("APPOINTMENT_CHECK_IN", "Appointment", saved.getId().toString(),
                            "scheduled", "checked_in");
                    return toDto(saved);
                });
    }

    /** Move across the lifecycle; terminal states cannot reopen. */
    @Transactional
    public AppointmentDTO updateStatus(UUID id, UpdateAppointmentStatusRequest request) {
        Appointment a = findById(id);
        Appointment.Status to = Appointment.Status.valueOf(request.status().trim().toLowerCase(Locale.ROOT));
        Appointment.Status from = a.getStatus();

        // Terminal gates
        if (from == Appointment.Status.completed || from == Appointment.Status.cancelled
                || from == Appointment.Status.no_show) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "This appointment is already " + from + " and cannot change status.");
        }
        if (to == Appointment.Status.completed && from != Appointment.Status.in_consultation) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Complete requires the patient to be in_consultation first.");
        }
        // checked_in → in_consultation (doctor starts); in_consultation → completed (doctor finishes)
        // Both pre-completion states can cancel/no_show
        a.setStatus(to);
        Appointment saved = appointmentRepository.save(a);
        auditLogger.log("APPOINTMENT_STATUS_CHANGED", "Appointment", saved.getId().toString(),
                String.valueOf(from), String.valueOf(to));
        return toDto(saved);
    }

    /** Doctor writes the visit note. */
    @Transactional
    public AppointmentDTO writeNote(UUID id, AppointmentNoteRequest request) {
        Appointment a = findById(id);
        if (a.getStatus() != Appointment.Status.in_consultation
                && a.getStatus() != Appointment.Status.completed) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Note can only be written during or after consultation (status: " + a.getStatus() + ")");
        }
        a.setDocNote(request.docNote() == null ? null : request.docNote().trim());
        Appointment saved = appointmentRepository.save(a);
        auditLogger.log("APPOINTMENT_NOTE_WRITTEN", "Appointment", saved.getId().toString(),
                null, saved.getDocNote());
        return toDto(saved);
    }

    /** Delete ONLY terminal rows — a cancelled/no-show record is destroyed, not archived. */
    @Transactional
    public void deleteTerminal(UUID id) {
        Appointment a = findById(id);
        if (a.getStatus() != Appointment.Status.cancelled
                && a.getStatus() != Appointment.Status.no_show) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Only cancelled or no-show appointments can be deleted (status: " + a.getStatus() + ")");
        }
        appointmentRepository.delete(a);
        auditLogger.log("APPOINTMENT_DELETED", "Appointment", a.getId().toString(),
                String.valueOf(a.getStatus()), null);
    }

    private Appointment findById(UUID id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id.toString()));
    }

    private AppointmentDTO toDto(Appointment a) {
        Patient patient = patientRepository.findById(a.getPatientId()).orElse(null);
        User doctor = userRepository.findById(a.getDoctorId()).orElse(null);
        return AppointmentDTO.from(a, patient, doctor);
    }

    private UUID tenant() {
        return TenantContext.getTenantId().orElseThrow(
                () -> new BusinessException("No organisation is selected for this session"));
    }

    private void validateFilters(String status, String doctorId, String type) {
        if (status != null && !status.isBlank() && !LIFECYCLE.contains(status.trim().toLowerCase(Locale.ROOT))) {
            throw new BusinessException("Unknown appointment status filter: " + status);
        }
        if (type != null && !type.isBlank()
                && !List.of("first_visit", "follow_up").contains(type.trim().toLowerCase(Locale.ROOT))) {
            throw new BusinessException("Unknown appointment type filter: " + type);
        }
    }
}