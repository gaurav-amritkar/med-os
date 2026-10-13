package com.medos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Idempotency key service for preventing duplicate operations.
 *
 * <p>Keys are scoped to the tenant and the user as well as the endpoint:
 * {@code idempotency:{tenant}:{user}:{endpoint}:{key}}. Without the tenant and
 * user, two hospitals choosing the same client key collided on one entry and
 * the second caller received the first caller's stored payment or invoice — a
 * different patient's amount, method and id. The endpoints protected here are
 * precisely the ones where that matters.
 *
 * <p>Claiming is a single atomic {@code SET NX}. The previous read-then-write
 * let two concurrent requests with the same key both miss and both execute,
 * which is the double-charge the feature exists to prevent: only the caller
 * that wins the claim runs the operation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<Object, Object> redisTemplate;
    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);
    /**
     * How long an uncompleted claim is held. Long enough for a slow request,
     * short enough that a crashed holder does not strand a client's key.
     */
    private static final Duration IN_FLIGHT_TTL = Duration.ofMinutes(2);

    /** A stored response, with the status the original call returned. */
    public record CachedResponse(int status, String body) implements java.io.Serializable {
    }

    /**
     * The outcome of trying to take ownership of an idempotency key.
     *
     * <p>Three states rather than a boolean, because "someone else is running it
     * right now" and "it already finished" need different responses: the first
     * is a retry, the second is a replay.
     */
    public sealed interface Claim {

        /** This caller owns the operation and should execute it. */
        record Acquired() implements Claim {
        }

        /** The operation already completed; return {@code response} verbatim. */
        record Replay(CachedResponse response) implements Claim {
        }

        /** Another caller holds the claim and has not finished. Retry shortly. */
        record InFlight() implements Claim {
        }
    }

    /**
     * Attempts to take ownership of {@code (endpoint, key)} for one principal.
     *
     * @param tenantId the acting tenant, or null for a platform-level session
     * @param userId   the acting user
     */
    public Claim claim(String endpoint, String idempotencyKey, UUID tenantId, UUID userId) {
        String key = buildKey(endpoint, idempotencyKey, tenantId, userId);

        Object existing = redisTemplate.opsForValue().get(key);
        if (existing instanceof CachedResponse cached) {
            log.debug("Idempotency replay for {}", key);
            return new Claim.Replay(cached);
        }

        Boolean won = redisTemplate.opsForValue()
                .setIfAbsent(key, IN_FLIGHT_MARKER, IN_FLIGHT_TTL.toMillis(), TimeUnit.MILLISECONDS);
        if (Boolean.TRUE.equals(won)) {
            return new Claim.Acquired();
        }

        // Someone claimed it between the read and the write. If they have since
        // finished, replay; otherwise they are still running.
        Object now = redisTemplate.opsForValue().get(key);
        if (now instanceof CachedResponse raced) {
            return new Claim.Replay(raced);
        }
        return new Claim.InFlight();
    }

    private static final Object IN_FLIGHT_MARKER = new InFlightMarker();

    /** Serialized form of the in-progress marker. */
    private record InFlightMarker() implements java.io.Serializable {
    }

    /** Records a successful outcome so later retries replay it. */
    public void complete(String endpoint, String idempotencyKey, UUID tenantId, UUID userId,
                         CachedResponse response) {
        String key = buildKey(endpoint, idempotencyKey, tenantId, userId);
        redisTemplate.opsForValue().set(key, response, TTL.toMillis(), TimeUnit.MILLISECONDS);
        log.debug("Idempotency completed for {}", key);
    }

    /**
     * Releases an unfinished claim so the client can retry.
     *
     * <p>Called when the operation fails. Without this a 500 would leave the key
     * held for the in-flight window and the client could not retry the payment
     * it was legitimately attempting.
     */
    public void release(String endpoint, String idempotencyKey, UUID tenantId, UUID userId) {
        redisTemplate.delete(buildKey(endpoint, idempotencyKey, tenantId, userId));
    }

    /**
     * Builds the Redis key.
     *
     * <p>Tenant and user lead the key so an operator can see at a glance which
     * hospital a stored response belongs to.
     */
    String buildKey(String endpoint, String idempotencyKey, UUID tenantId, UUID userId) {
        return KEY_PREFIX + (tenantId == null ? "platform" : tenantId)
                + ":" + (userId == null ? "anonymous" : userId)
                + ":" + endpoint + ":" + idempotencyKey;
    }
}
