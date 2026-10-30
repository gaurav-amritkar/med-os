package com.medos.service;

import com.medos.dto.InvoiceRequest;
import com.medos.entity.Charge;
import com.medos.entity.Invoice;
import com.medos.entity.Patient;
import com.medos.exception.BusinessException;
import com.medos.modules.billing.service.BillingService;
import com.medos.repository.ChargeRepository;
import com.medos.repository.InvoiceRepository;
import com.medos.repository.PatientRepository;
import com.medos.security.CurrentUserProvider;
import com.medos.util.AuditLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingInvoiceDiscountTest {

    private static final UUID PATIENT_ID = UUID.randomUUID();

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private ChargeRepository chargeRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditLogger auditLogger;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private com.medos.modules.payment.service.PaymentService paymentService;
    @InjectMocks private BillingService billingService;

    @Test
    void generateInvoice_appliesFlatDiscountAcrossTaxBasesAndPersistsTotals() {
        Charge serviceCharge = charge("100.00", "18.00", "18.00", "118.00");
        Charge medicineCharge = charge("100.00", "5.00", "5.00", "105.00");
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(new Patient()));
        when(chargeRepository.findByPatientIdAndStatusForUpdate(PATIENT_ID, Charge.Status.unbilled))
                .thenReturn(List.of(serviceCharge, medicineCharge));
        when(invoiceRepository.getNextInvoiceSeq()).thenReturn(1L);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId(UUID.randomUUID());
            return invoice;
        });

        InvoiceRequest request = new InvoiceRequest();
        request.setPatientId(PATIENT_ID);
        request.setDiscount(new BigDecimal("10.00"));

        Invoice invoice = billingService.generateInvoice(request);

        assertEquals(new BigDecimal("200.00"), invoice.getSubtotal());
        assertEquals(new BigDecimal("10.00"), invoice.getDiscount());
        assertEquals(new BigDecimal("21.85"), invoice.getGstTotal());
        assertEquals(new BigDecimal("211.85"), invoice.getTotalAmount());
        verify(auditLogger).log(eq("INVOICE_GENERATED"), eq("Invoice"), anyString(), isNull(), contains("discount=10"));
    }

    @Test
    void generateInvoice_rejectsDiscountGreaterThanSubtotalBeforeSaving() {
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(new Patient()));
        when(chargeRepository.findByPatientIdAndStatusForUpdate(PATIENT_ID, Charge.Status.unbilled))
                .thenReturn(List.of(charge("100.00", "18.00", "18.00", "118.00")));

        for (BigDecimal discount : List.of(new BigDecimal("100.01"), new BigDecimal("-0.001"))) {
            InvoiceRequest request = new InvoiceRequest();
            request.setPatientId(PATIENT_ID);
            request.setDiscount(discount);
            assertThrows(BusinessException.class, () -> billingService.generateInvoice(request));
        }
        verify(invoiceRepository, never()).save(any(Invoice.class));
        verify(chargeRepository, never()).saveAll(any());
    }

    @Test
    void generateInvoice_allocatesDiscountCentsAcrossSmallChargesWithoutNegativeTaxBases() {
        List<Charge> charges = IntStream.range(0, 1_000)
                .mapToObj(ignored -> charge("0.01", "18.00", "0.00", "0.01"))
                .toList();
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(new Patient()));
        when(chargeRepository.findByPatientIdAndStatusForUpdate(PATIENT_ID, Charge.Status.unbilled))
                .thenReturn(charges);
        when(invoiceRepository.getNextInvoiceSeq()).thenReturn(2L);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId(UUID.randomUUID());
            return invoice;
        });

        InvoiceRequest request = new InvoiceRequest();
        request.setPatientId(PATIENT_ID);
        request.setDiscount(new BigDecimal("4.90"));

        Invoice invoice = billingService.generateInvoice(request);

        assertEquals(new BigDecimal("10.00"), invoice.getSubtotal());
        assertEquals(new BigDecimal("0.92"), invoice.getGstTotal());
        assertEquals(new BigDecimal("6.02"), invoice.getTotalAmount());
    }

    private static Charge charge(String amount, String gstPercent, String gstAmount, String total) {
        return Charge.builder()
                .id(UUID.randomUUID())
                .patientId(PATIENT_ID)
                .chargeType(Charge.ChargeType.consultation)
                .quantity(1)
                .amount(new BigDecimal(amount))
                .gstPercent(new BigDecimal(gstPercent))
                .gstAmount(new BigDecimal(gstAmount))
                .totalAmount(new BigDecimal(total))
                .status(Charge.Status.unbilled)
                .build();
    }
}
