package com.medos.controller;

import com.medos.dto.InvoiceRequest;
import com.medos.dto.PaymentRequest;
import com.medos.entity.Charge;
import com.medos.entity.Invoice;
import com.medos.entity.Payment;
import com.medos.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('BILLING','ADMIN')")
    public ResponseEntity<Invoice> generateInvoice(@Valid @RequestBody InvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.generateInvoice(request));
    }

    @GetMapping("/patients/{patientId}/invoices")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Invoice>> getInvoices(@PathVariable UUID patientId) {
        return ResponseEntity.ok(billingService.getPatientInvoices(patientId));
    }

    @GetMapping("/patients/{patientId}/unbilled")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Charge>> getUnbilled(@PathVariable UUID patientId) {
        return ResponseEntity.ok(billingService.getUnbilledCharges(patientId));
    }

    @GetMapping("/invoices/{invoiceId}/charges")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Charge>> getInvoiceCharges(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(billingService.getChargesByInvoice(invoiceId));
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('BILLING','ADMIN')")
    public ResponseEntity<Payment> recordPayment(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.recordPayment(request));
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Payment>> getPayments(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(billingService.getPaymentsByInvoice(invoiceId));
    }
}
