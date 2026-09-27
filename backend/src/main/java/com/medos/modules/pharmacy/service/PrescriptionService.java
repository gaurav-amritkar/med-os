package com.medos.modules.pharmacy.service;

import com.medos.dto.DispenseRequest;
import com.medos.entity.Prescription;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.repository.PrescriptionRepository;
import com.medos.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final AuditLogger auditLogger;

    @Transactional
    public void processPrescription(DispenseRequest request) {
        Prescription rx = prescriptionRepository.findById(request.getPrescriptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", request.getPrescriptionId().toString()));
        if (rx.getStatus() != Prescription.Status.pending) {
            throw new BusinessException("Prescription is already " + rx.getStatus());
        }
        rx.setStatus(Prescription.Status.dispensed);
        rx.setDispensedAt(LocalDateTime.now());
        prescriptionRepository.save(rx);
        auditLogger.log("PRESCRIPTION_DISPENSED", "Prescription", rx.getId().toString(),
                null, "qty=" + request.getQuantity());
    }

    public Prescription getPrescriptionDetails(UUID id) {
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", id.toString()));
    }
}