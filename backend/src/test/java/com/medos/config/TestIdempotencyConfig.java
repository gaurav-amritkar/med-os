package com.medos.config;

import com.medos.service.IdempotencyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.UUID;

/**
 * Test configuration that provides a no-op IdempotencyService
 * and disables the IdempotencyFilter for tests.
 *
 * <p>The stub always hands the claim back, so a test that exercises a billing
 * endpoint is not silently turned into an idempotency-replay test. Tests that
 * care about idempotency use {@code IdempotencyService} directly.
 */
@Configuration
@Profile("test")
public class TestIdempotencyConfig {

    @Bean
    @Primary
    public IdempotencyService idempotencyService() {
        return new IdempotencyService(null) {
            @Override
            public Claim claim(String endpoint, String idempotencyKey, UUID tenantId, UUID userId) {
                return new Claim.Acquired();
            }

            @Override
            public void complete(String endpoint, String idempotencyKey, UUID tenantId, UUID userId,
                                 CachedResponse response) {
                // No-op
            }

            @Override
            public void release(String endpoint, String idempotencyKey, UUID tenantId, UUID userId) {
                // No-op
            }
        };
    }

    @Bean
    @Primary
    public IdempotencyFilter idempotencyFilter() {
        return new IdempotencyFilter(null, null) {
            @Override
            protected boolean shouldNotFilter(jakarta.servlet.http.HttpServletRequest request) {
                // Skip filter entirely in tests
                return true;
            }
        };
    }
}
