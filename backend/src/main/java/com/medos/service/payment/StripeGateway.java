package com.medos.service.payment;

import com.medos.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StripeGateway implements PaymentGateway {
    private final TenantService tenantService;

    @Override
    public String getName() {
        return "stripe";
    }

    @Override
    public PaymentResponse createPayment(PaymentRequest request) {
        String apiKey = tenantService.getConfig(request.tenantId(), "stripe_secret_key");
        // In production, use Stripe SDK here
        return new PaymentResponse(true, "txn_" + UUID.randomUUID().toString().substring(0, 12),
                "pi_" + UUID.randomUUID().toString().substring(0, 20),
                "https://checkout.stripe.com/pay/" + UUID.randomUUID(),
                "pending", null, Map.of("gateway", "stripe"));
    }

    @Override
    public PaymentResponse verifyPayment(String transactionId) {
        return new PaymentResponse(true, transactionId, null, null, "success", null, Map.of());
    }

    @Override
    public PaymentResponse refundPayment(String transactionId, BigDecimal amount) {
        return new PaymentResponse(true, transactionId, null, null, "refunded", null, Map.of());
    }
}