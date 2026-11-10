package com.medos.modules.clinical.service;

import com.medos.dto.CreateLabOrderRequest;
import com.medos.dto.LabOrderDTO;
import com.medos.dto.RecordLabResultRequest;
import com.medos.dto.UpdateLabOrderStatusRequest;
import com.medos.entity.Encounter;
import com.medos.entity.LabOrder;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.repository.EncounterRepository;
import com.medos.repository.LabOrderRepository;
import com.medos.security.CurrentUserProvider;
import com.medos.security.TenantContext;
import com.medos.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lab orders: a doctor orders a test from an encounter; a lab role updates
 * status through the lifecycle and records the result. PRD §1 — an order is
 * not mutable once its parent encounter is signed.
 *
 * <p>All reads and writes are tenant-scoped: the repository's finder methods
 * are invoked through the tenant ThreadLocal that {@link TenantContext} sets,
 * and the {@code TenantStatementInspector} adds the tenant predicate to every
 * SELECT on {@code lab_orders}.
 */
@Service
@RequiredArgsConstructor
public class LabOrderService {

    private final LabOrderRepository labOrderRepository;
    private final EncounterRepository encounterRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogger auditLogger;

    /**
     * Order a lab test from an encounter. DOCTOR or ADMIN only — the caller
     * is the ordering doctor.
     *
     * @param request encounterId, patientId, testName, optional testCode/priority
     * @return the created order
     */
    @Transactional
    public LabOrderDTO createLabOrder(CreateLabOrderRequest request) {
        UUID actingUser = currentUserProvider.getCurrentUserId();
        if (actingUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Authenticated user required");
        }

        Encounter encounter = encounterRepository.findById(request.encounterId())
                .orElseThrow(() -> new ResourceNotFoundException("Encounter", request.encounterId().toString()));

        TenantContext.getTenantId().ifPresent(tenantId -> {
            if (!tenantId.equals(encounter.getTenantId())) {
                throw new BusinessException(HttpStatus.FORBIDDEN,
                        "Encounter belongs to another tenant");
            }
        });

        if (encounter.getStatus() == Encounter.Status.signed) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Cannot order a lab test on a signed encounter");
        }

        // The patient comes from the encounter, not from the request. Accepting an
        // independent patientId let a lab order for one patient be attached to
        // another patient's encounter (#222).
        UUID patientId = encounter.getPatientId() != null
                ? encounter.getPatientId()
                : request.patientId();
        if (patientId != null && !patientId.equals(request.patientId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "Patient " + request.patientId() + " does not belong to this encounter");
        }

        LabOrder order = LabOrder.builder()
                .tenantId(encounter.getTenantId())
                .encounterId(request.encounterId())
                .patientId(patientId)
                .doctorId(actingUser)
                .testName(request.testName().trim())
                .testCode(request.testCode() == null ? null : request.testCode().trim())
                .priority(request.priority() == null || request.priority().isBlank()
                        ? LabOrder.Priority.normal : LabOrder.Priority.valueOf(request.priority()))
                .status(LabOrder.Status.ordered)
                .build();

        LabOrder saved = labOrderRepository.save(order);
        auditLogger.log("LAB_ORDER_CREATED", "LabOrder", saved.getId().toString(),
                null, "test=" + saved.getTestName() + ", priority=" + saved.getPriority());
        return LabOrderDTO.from(saved);
    }

    /**
     * List lab orders, optionally filtered by patient, encounter, or status.
     * Readable by all authenticated roles; tenant-scoped automatically.
     */
    public List<LabOrderDTO> listLabOrders(UUID patientId, UUID encounterId, String status) {
        List<LabOrder> orders;
        if (patientId != null) {
            orders = labOrderRepository.findByPatientId(patientId);
        } else if (encounterId != null) {
            orders = labOrderRepository.findByEncounterId(encounterId);
        } else if (status != null && !status.isBlank()) {
            orders = labOrderRepository.findByStatus(LabOrder.Status.valueOf(status.trim().toUpperCase()));
        } else {
            orders = labOrderRepository.findAll();
        }
        return orders.stream().map(LabOrderDTO::from).toList();
    }

    /**
     * Update an order's status through the lifecycle. LAB or ADMIN only.
     *
     * Allowed transitions:
     *   ordered → collected, cancelled
     *   collected → in_progress, completed
     *   in_progress → completed
     *
     * Rejected: completed → anything, cancelled → anything, collected → ordered,
     * in_progress → collected, etc.
     */
    @Transactional
    public LabOrderDTO updateStatus(UUID orderId, UpdateLabOrderStatusRequest request) {
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("LabOrder", orderId.toString()));

        LabOrder.Status from = order.getStatus();
        LabOrder.Status to = LabOrder.Status.valueOf(request.status().trim());

        if (!isValidTransition(from, to)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Invalid lab order transition " + from + " → " + to);
        }

        // PRD §1: an order is not mutable once its parent encounter is signed.
        Encounter encounter = encounterRepository.findById(order.getEncounterId()).orElseThrow();
        if (encounter.getStatus() == Encounter.Status.signed && from != LabOrder.Status.cancelled) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Cannot change a lab order on a signed encounter");
        }

        order.setStatus(to);
        LabOrder saved = labOrderRepository.save(order);
        auditLogger.log("LAB_ORDER_STATUS_CHANGED", "LabOrder", saved.getId().toString(),
                String.valueOf(from), String.valueOf(to));
        return LabOrderDTO.from(saved);
    }

    /**
     * Record a lab result. LAB or ADMIN only. Requires status collected or
     * in_progress, and an unsigned parent encounter. Sets resultAt automatically.
     */
    @Transactional
    public LabOrderDTO recordResult(UUID orderId, RecordLabResultRequest request) {
        LabOrder order = labOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("LabOrder", orderId.toString()));

        if (order.getStatus() != LabOrder.Status.collected
                && order.getStatus() != LabOrder.Status.in_progress) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "A result can only be recorded once the order is collected or in progress");
        }

        Encounter encounter = encounterRepository.findById(order.getEncounterId()).orElseThrow();
        if (encounter.getStatus() == Encounter.Status.signed) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Cannot record a result on a lab order whose encounter is signed");
        }

        order.setResult(request.result().trim());
        order.setResultAt(LocalDateTime.now());
        LabOrder saved = labOrderRepository.save(order);
        auditLogger.log("LAB_RESULT_RECORDED", "LabOrder", saved.getId().toString(),
                // The result text is clinical content. Recording it in the audit
                // row duplicates it into a plaintext table (#224): record that a
                // result was recorded, and who recorded it, not what it says.
                null, "recorded=true length=" + (saved.getResult() == null ? 0 : saved.getResult().length()));
        return LabOrderDTO.from(saved);
    }

    /**
     * The lab order state machine. ordered can go to collected or cancelled;
     * collected to in_progress or completed; in_progress to completed.
     * Completed and cancelled are terminal.
     */
    private boolean isValidTransition(LabOrder.Status from, LabOrder.Status to) {
        return switch (from) {
            case ordered -> to == LabOrder.Status.collected || to == LabOrder.Status.cancelled;
            case collected -> to == LabOrder.Status.in_progress || to == LabOrder.Status.completed;
            case in_progress -> to == LabOrder.Status.completed;
            case completed, cancelled -> false; // terminal
        };
    }
}
