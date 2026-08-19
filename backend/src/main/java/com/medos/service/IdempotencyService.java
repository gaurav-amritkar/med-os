package com.medos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Idempotency key service for preventing duplicate operations.
 * 
 * Stores idempotency keys in Redis with 24-hour TTL.
 * Key format: "idempotency:{endpoint}:{key}"
 * Value: serialized response object
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<Object, Object> redisTemplate;
    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    /**
     * Check if an idempotency key exists and return cached response.
     * 
     * @param endpoint The endpoint identifier (e.g., "billing/payments", "pharmacy/dispense", "billing/invoices")
     * @param idempotencyKey The client-provided idempotency key
     * @return Optional containing cached response if key exists, empty otherwise
     */
    public Optional<Object> getResponse(String endpoint, String idempotencyKey) {
        String key = KEY_PREFIX + endpoint + ":" + idempotencyKey;
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            log.debug("Idempotency key hit: {}", key);
            return Optional.of(value);
        }
        return Optional.empty();
    }

    /**
     * Store a response for an idempotency key.
     * 
     * @param endpoint The endpoint identifier
     * @param idempotencyKey The client-provided idempotency key
     * @param response The response to cache
     */
    public void storeResponse(String endpoint, String idempotencyKey, Object response) {
        String key = KEY_PREFIX + endpoint + ":" + idempotencyKey;
        redisTemplate.opsForValue().set(key, response, TTL.toMillis(), TimeUnit.MILLISECONDS);
        log.debug("Idempotency key stored: {}", key);
    }

    /**
     * Check if an idempotency key exists (without returning response).
     * 
     * @param endpoint The endpoint identifier
     * @param idempotencyKey The client-provided idempotency key
     * @return true if key exists
     */
    public boolean exists(String endpoint, String idempotencyKey) {
        String key = KEY_PREFIX + endpoint + ":" + idempotencyKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Delete an idempotency key (for testing or manual cleanup).
     */
    public void delete(String endpoint, String idempotencyKey) {
        String key = KEY_PREFIX + endpoint + ":" + idempotencyKey;
        redisTemplate.delete(key);
    }
}