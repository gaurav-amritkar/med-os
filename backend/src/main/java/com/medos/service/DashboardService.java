package com.medos.service;

import com.medos.entity.*;
import com.medos.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final AdmissionRepository admissionRepository;
    private final EncounterRepository encounterRepository;
    private final ChargeRepository chargeRepository;
    private final InvoiceRepository invoiceRepository;
    private final MedicineBatchRepository medicineBatchRepository;
    private final NotificationRepository notificationRepository;

    public Map<String, Object> getAdminDashboard() {
        Map<String, Object> stats = new HashMap<>();

        long totalPatients = patientRepository.count();
        long totalAppointments = appointmentRepository.count();
        long activeAdmissions = admissionRepository.findByStatus(Admission.Status.admitted).size();
        long pendingInvoices = invoiceRepository.findByStatus(Invoice.Status.issued).size();
        long todayAppointments = appointmentRepository.findByAppointmentDateBetween(
                LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay()).size();
        long openEncounters = encounterRepository.findByStatus(Encounter.Status.open).size();
        long totalRevenue = getAllTimeRevenue();

        List<MedicineBatch> expiringBatches = medicineBatchRepository
                .findExpiringSoon(LocalDate.now(), LocalDate.now().plusDays(30));
        long expiringStockCount = expiringBatches.stream()
                .filter(b -> b.getRemainingQty() > 0)
                .count();

        stats.put("totalPatients", totalPatients);
        stats.put("totalAppointments", totalAppointments);
        stats.put("activeAdmissions", activeAdmissions);
        stats.put("pendingInvoices", pendingInvoices);
        stats.put("todayAppointments", todayAppointments);
        stats.put("openEncounters", openEncounters);
        stats.put("totalRevenue", totalRevenue);
        stats.put("expiringStockCount", expiringStockCount);

        return stats;
    }

    public Map<String, Object> getDoctorDashboard(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        long todayAppointments = appointmentRepository
                .findByDoctorIdAndAppointmentDateBetween(null, todayStart, tomorrowStart).size();
        long pendingEncounters = encounterRepository.findByStatus(Encounter.Status.open).size();

        stats.put("todayAppointments", todayAppointments);
        stats.put("pendingEncounters", pendingEncounters);
        return stats;
    }

    public Map<String, Object> getDashboardByRole(String role) {
        return getAdminDashboard();
    }

    public List<Notification> getNotifications(UUID userId, boolean unreadOnly) {
        if (unreadOnly) {
            return notificationRepository.findByRecipientIdAndReadFalse(userId);
        }
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    private long getAllTimeRevenue() {
        try {
            BigDecimal total = invoiceRepository.findAll().stream()
                    .filter(i -> i.getStatus() == Invoice.Status.paid)
                    .map(Invoice::getTotalAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return total.longValue();
        } catch (Exception e) {
            return 0L;
        }
    }
}
