package com.medos.service.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface PaymentGateway {
    String getName();
    PaymentResponse createPayment(PaymentRequest request);
    PaymentResponse verifyPayment(String transactionId);
    PaymentResponse refundPayment(String transactionId, BigDecimal amount);

    record PaymentRequest(UUID tenantId, UUID invoiceId, BigDecimal amount, String currency,
                          String description, String customerEmail, String customerPhone,
                          Map<String, String> metadata) {}

    record PaymentResponse(boolean success, String transactionId, String gatewayReference,
                          String paymentUrl, String status, String errorMessage,
                          Map<String, String> rawResponse) {}
}