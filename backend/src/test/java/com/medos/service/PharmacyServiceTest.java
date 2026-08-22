package com.medos.service;

import com.medos.dto.DispenseRequest;
import com.medos.entity.*;
import com.medos.exception.BusinessException;
import com.medos.exception.ResourceNotFoundException;
import com.medos.repository.AuditLogRepository;
import com.medos.repository.*;
import com.medos.security.CurrentUserProvider;
import com.medos.util.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PharmacyServiceTest {

    @Mock private MedicineCatalogRepository medicineCatalogRepository;
    @Mock private MedicineBatchRepository medicineBatchRepository;
    @Mock private StockTransactionRepository stockTransactionRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private ChargeRepository chargeRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    private AuditLogger auditLogger; // real — uses mocked AuditLogRepository; Mockito cannot mock AuditLogger on JDK 26
    @InjectMocks private PharmacyService pharmacyService;

    @BeforeEach
    void wireAuditLogger() {
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn(UUID.randomUUID());
        lenient().when(userRepository.existsById(any(UUID.class))).thenReturn(true);
        auditLogger = new AuditLogger(auditLogRepository, userRepository, currentUserProvider);
        ReflectionTestUtils.setField(pharmacyService, "auditLogger", auditLogger);
    }

    private static final UUID MEDICINE_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID ENCOUNTER_ID = UUID.randomUUID();
    private static final UUID RX_ID = UUID.randomUUID();

    private MedicineBatch batch(UUID id, LocalDate expiry, int qty) {
        return MedicineBatch.builder()
                .id(id)
                .medicineId(MEDICINE_ID)
                .batchNo("BAT" + id.toString().substring(0, 4))
                .expiryDate(expiry)
                .remainingQty(qty)
                .build();
    }

    private DispenseRequest dispenseRequest(int qty) {
        DispenseRequest r = new DispenseRequest();
        r.setPatientId(PATIENT_ID);
        r.setPrescriptionId(RX_ID);
        r.setQuantity(qty);
        r.setNotes("test dispense");
        return r;
    }

    private Prescription pendingRx() {
        return Prescription.builder()
                .id(RX_ID)
                .encounterId(ENCOUNTER_ID)
                .patientId(PATIENT_ID)
                .medicineId(MEDICINE_ID)
                .dosage("1-0-1")
                .status(Prescription.Status.pending)
                .build();
    }

    private MedicineCatalog medicineWithPrice(BigDecimal price) {
        MedicineCatalog m = new MedicineCatalog();
        m.setId(MEDICINE_ID);
        m.setName("Paracetamol");
        m.setUnitPrice(price);
        return m;
    }

    private Patient anyPatient() {
        Patient p = new Patient();
        p.setId(PATIENT_ID);
        p.setOutstanding(BigDecimal.ZERO);
        return p;
    }

    @Test
    void dispense_deductsFromBatchesInFefoOrderAndPostsCharge() {
        LocalDate today = LocalDate.now();
        LocalDate older = today.plusDays(30);
        LocalDate newer = today.plusDays(120);
        MedicineBatch b1 = batch(UUID.randomUUID(), older, 20);
        MedicineBatch b2 = batch(UUID.randomUUID(), newer, 100);

        when(prescriptionRepository.findById(RX_ID)).thenReturn(Optional.of(pendingRx()));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(anyPatient()));
        // Repository returns in FEFO order (oldest expiry first) — service relies on this ordering.
        when(medicineBatchRepository.findAvailableBatchesByFefoForUpdate(MEDICINE_ID, today))
                .thenReturn(List.of(b1, b2));
        when(medicineCatalogRepository.findById(MEDICINE_ID))
                .thenReturn(Optional.of(medicineWithPrice(new BigDecimal("10.00"))));
        when(chargeRepository.findByPatientIdAndStatus(PATIENT_ID, Charge.Status.unbilled))
                .thenReturn(List.of());

        pharmacyService.dispense(dispenseRequest(15));

        // FEFO: should consume entirely from the oldest-expiring batch.
        assertEquals(5, b1.getRemainingQty());
        assertEquals(100, b2.getRemainingQty());

        ArgumentCaptor<Prescription> rxCaptor = ArgumentCaptor.forClass(Prescription.class);
        verify(prescriptionRepository).save(rxCaptor.capture());
        assertEquals(Prescription.Status.dispensed, rxCaptor.getValue().getStatus());

        ArgumentCaptor<Charge> chargeCaptor = ArgumentCaptor.forClass(Charge.class);
        verify(chargeRepository).save(chargeCaptor.capture());
        Charge charge = chargeCaptor.getValue();
        assertEquals(Charge.ChargeType.pharmacy, charge.getChargeType());
        assertEquals(PATIENT_ID, charge.getPatientId());
        assertEquals(ENCOUNTER_ID, charge.getEncounterId());
        assertEquals(new BigDecimal("150.00"), charge.getAmount());
        assertEquals(new BigDecimal("7.50"), charge.getGstAmount());
        assertEquals(new BigDecimal("157.50"), charge.getTotalAmount());
        assertEquals(Charge.Status.unbilled, charge.getStatus());

        verify(stockTransactionRepository, times(1)).save(any(StockTransaction.class));
        verify(auditLogRepository, atLeastOnce()).save(any(com.medos.entity.AuditLog.class));
    }

    @Test
    void dispense_spansMultipleBatchesWhenFirstInsufficient() {
        LocalDate today = LocalDate.now();
        MedicineBatch b1 = batch(UUID.randomUUID(), today.plusDays(30), 10);
        MedicineBatch b2 = batch(UUID.randomUUID(), today.plusDays(90), 50);

        when(prescriptionRepository.findById(RX_ID)).thenReturn(Optional.of(pendingRx()));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(anyPatient()));
        when(medicineBatchRepository.findAvailableBatchesByFefoForUpdate(MEDICINE_ID, today))
                .thenReturn(List.of(b1, b2));
        when(medicineCatalogRepository.findById(MEDICINE_ID))
                .thenReturn(Optional.of(medicineWithPrice(new BigDecimal("5.00"))));
        when(chargeRepository.findByPatientIdAndStatus(PATIENT_ID, Charge.Status.unbilled))
                .thenReturn(List.of());

        pharmacyService.dispense(dispenseRequest(25));

        assertEquals(0, b1.getRemainingQty());
        assertEquals(35, b2.getRemainingQty());
        verify(stockTransactionRepository, times(2)).save(any(StockTransaction.class));
    }

    @Test
    void dispense_noStock_throwsAndDoesNotUpdatePrescription() {
        when(prescriptionRepository.findById(RX_ID)).thenReturn(Optional.of(pendingRx()));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(anyPatient()));
        when(medicineBatchRepository.findAvailableBatchesByFefoForUpdate(eq(MEDICINE_ID), any()))
                .thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pharmacyService.dispense(dispenseRequest(5)));
        assertEquals(400, ex.getStatus().value());
        verify(prescriptionRepository, never()).save(any(Prescription.class));
        verify(chargeRepository, never()).save(any(Charge.class));
    }

    @Test
    void dispense_insufficientStock_acrossBatchesThrows() {
        LocalDate today = LocalDate.now();
        MedicineBatch b1 = batch(UUID.randomUUID(), today.plusDays(30), 5);
        // b1 partially fulfills; service mutates batch, saves txn, then throws.
        // We assert it throws and the prescription is NOT marked dispensed.
        when(prescriptionRepository.findById(RX_ID)).thenReturn(Optional.of(pendingRx()));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(anyPatient()));
        when(medicineBatchRepository.findAvailableBatchesByFefoForUpdate(MEDICINE_ID, today))
                .thenReturn(List.of(b1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pharmacyService.dispense(dispenseRequest(20)));
        assertTrue(ex.getMessage().contains("Insufficient stock"));
        verify(prescriptionRepository, never()).save(any(Prescription.class));
        verify(chargeRepository, never()).save(any(Charge.class));
        // Batch was still reduced before the failure (documented behavior).
        assertEquals(0, b1.getRemainingQty());
    }

    @Test
    void dispense_alreadyDispensedPrescription_throws() {
        Prescription rx = pendingRx();
        rx.setStatus(Prescription.Status.dispensed);
        when(prescriptionRepository.findById(RX_ID)).thenReturn(Optional.of(rx));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pharmacyService.dispense(dispenseRequest(1)));
        assertTrue(ex.getMessage().contains("already"));
        verifyNoInteractions(medicineBatchRepository);
    }

    @Test
    void dispense_unknownPrescription_throwsNotFound() {
        when(prescriptionRepository.findById(RX_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> pharmacyService.dispense(dispenseRequest(1)));
        verifyNoInteractions(medicineBatchRepository);
    }

    @Test
    void dispense_unknownPatient_throwsNotFound() {
        when(prescriptionRepository.findById(RX_ID)).thenReturn(Optional.of(pendingRx()));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> pharmacyService.dispense(dispenseRequest(1)));
        verifyNoInteractions(medicineBatchRepository);
    }

    @Test
    void addStock_persistsBatchAndStockInTransaction() {
        UUID medicineId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        LocalDate expiry = LocalDate.now().plusYears(1);

        when(medicineBatchRepository.save(any(MedicineBatch.class))).thenAnswer(inv -> {
            MedicineBatch b = inv.getArgument(0);
            b.setId(batchId);
            return b;
        });

        MedicineBatch result = pharmacyService.addStock(
                medicineId, "LOT-A", expiry, 50, new BigDecimal("12.50"), "Acme");

        assertEquals("LOT-A", result.getBatchNo());
        assertEquals(50, result.getRemainingQty());
        assertEquals(expiry, result.getExpiryDate());

        ArgumentCaptor<StockTransaction> txnCaptor = ArgumentCaptor.forClass(StockTransaction.class);
        verify(stockTransactionRepository).save(txnCaptor.capture());
        StockTransaction txn = txnCaptor.getValue();
        assertEquals(StockTransaction.TransactionType.in, txn.getTransactionType());
        assertEquals(50, txn.getQuantity());
        assertEquals("PUR-LOT-A", txn.getReferenceNo());
        assertEquals(medicineId, txn.getMedicineId());
        verify(auditLogRepository).save(any(com.medos.entity.AuditLog.class));
    }
}
