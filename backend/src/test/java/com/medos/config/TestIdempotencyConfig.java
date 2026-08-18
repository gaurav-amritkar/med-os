package com.medos.config;

import com.medos.service.IdempotencyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.Optional;

/**
 * Test configuration that provides a no-op IdempotencyService
 * and disables the IdempotencyFilter for tests.
 */
@Configuration
@Profile("test")
public class TestIdempotencyConfig {

    @Bean
    @Primary
    public IdempotencyService idempotencyService() {
        return new IdempotencyService(null) {
            @Override
            public Optional<Object> getResponse(String endpoint, String idempotencyKey) {
                return Optional.empty();
            }

            @Override
            public void storeResponse(String endpoint, String idempotencyKey, Object response) {
                // No-op
            }

            @Override
            public boolean exists(String endpoint, String idempotencyKey) {
                return false;
            }

            @Override
            public void delete(String endpoint, String idempotencyKey) {
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