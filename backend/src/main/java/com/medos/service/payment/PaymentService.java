package com.medos.service.payment;

import com.medos.entity.Invoice;
import com.medos.entity.Payment;
import com.medos.entity.Tenant;
import com.medos.repository.InvoiceRepository;
import com.medos.repository.PaymentRepository;
import com.medos.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final TenantRepository tenantRepository;
    private final List<PaymentGateway> gateways;

    @Transactional
    public PaymentGateway.PaymentResponse processPayment(UUID invoiceId, String gatewayName, Map<String, String> metadata) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        Tenant tenant = tenantRepository.findById(invoice.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant not found for invoice"));

        PaymentGateway gateway = gateways.stream()
                .filter(g -> g.getName().equalsIgnoreCase(gatewayName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Payment gateway not found: " + gatewayName));

        PaymentGateway.PaymentRequest request = new PaymentGateway.PaymentRequest(
                tenant.getId(),
                invoiceId,
                invoice.getTotalAmount(),
                "USD",
                "Payment for Invoice " + invoice.getInvoiceNumber(),
                null, null,
                metadata
        );

        PaymentGateway.PaymentResponse response = gateway.createPayment(request);

        if (response.success()) {
            Payment payment = Payment.builder()
                    .invoiceId(invoiceId)
                    .patientId(invoice.getPatientId())
                    .amount(invoice.getTotalAmount())
                    .paymentMethod(Payment.PaymentMethod.valueOf(gatewayName))
                    .transactionRef(response.transactionId())
                    .status(Payment.Status.pending)
                    .tenantId(tenant.getId())
                    .build();
            paymentRepository.save(payment);
        }

        return response;
    }

    @Transactional
    public void handlePaymentCallback(String transactionId, String gatewayName, String status) {
        Payment payment = paymentRepository.findByTransactionRef(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for transaction: " + transactionId));

        payment.setStatus(Payment.Status.valueOf(status));
        paymentRepository.save(payment);

        if ("success".equalsIgnoreCase(status)) {
            Invoice invoice = invoiceRepository.findById(payment.getInvoiceId())
                    .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
            invoice.setStatus(Invoice.Status.paid);
            invoiceRepository.save(invoice);
        }
    }
}