package com.medos.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Brute-force protection for the login endpoint.
 *
 * Tracks failed attempts in Redis with a TTL, on two counters:
 *
 * <ul>
 *   <li>per {@code (username, socket peer)} — limits one source address
 *   <li>per {@code username} — bounds a distributed attack
 * </ul>
 *
 * <p>The address deliberately comes from the socket, not from
 * {@code X-Forwarded-For}. Caddy overwrites that header, so through the
 * production edge the forwarded value happens to be trustworthy — but the
 * limiter is the only control between an attacker and unlimited password
 * guesses, and it should not depend on which proxy is in front. Behind Caddy the
 * peer is always the proxy, which costs per-source granularity; the
 * username-only counter is what keeps that from becoming a bypass, and it is
 * also what bounds an attacker spread across many real addresses.
 *
 * <p>If Redis is unavailable, falls back to a process-local counter so the
 * application never fails open on the availability axis.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    static final int MAX_FAILED_ATTEMPTS = 5;
    /**
     * Higher ceiling for the username-wide counter.
     *
     * <p>That counter is deliberately harder to reach, because it is also a
     * lockout lever: anyone who knows a username can push it over, whereas the
     * per-source counter only locks the attacker themselves out. Five failures
     * would let one request deny service to a named clinician. At this ceiling
     * it still bounds a distributed attacker — twenty attempts cannot be spread
     * thin enough to escape — while costing an attacker several times more noise.
     */
    static final int ANY_PEER_MAX_FAILED_ATTEMPTS = 20;
    static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);
    static final String REDIS_KEY_PREFIX = "medos:login:fail:";
    /** Prefix for the username-wide counter, which ignores the source address. */
    static final String ANY_PEER_PREFIX = "any:";

    private final StringRedisTemplate redis;

    private final ConcurrentHashMap<String, InMemoryCounter> inMemory = new ConcurrentHashMap<>();

    public LoginRateLimiter(@Autowired(required = false) StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Returns true when the caller should be locked out (too many recent failures). */
    public boolean isBlocked(String username) {
        return atOrAboveLimit(sourceKey(username), MAX_FAILED_ATTEMPTS)
                || atOrAboveLimit(anyPeerKey(username), ANY_PEER_MAX_FAILED_ATTEMPTS);
    }

    public void recordFailure(String username) {
        increment(sourceKey(username));
        increment(anyPeerKey(username));
    }

    /** Called after a successful login so previous lockouts are cleared. */
    public void reset(String username) {
        for (String key : keys(username)) {
            try {
                if (redis != null) {
                    redis.delete(key);
                }
            } catch (Exception e) {
                log.warn("Redis unavailable for rate-limit reset");
            }
            inMemory.remove(key);
        }
    }

    private boolean atOrAboveLimit(String key, int limit) {
        try {
            if (redis != null) {
                String raw = redis.opsForValue().get(key);
                return raw != null && Integer.parseInt(raw) >= limit;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for rate-limit read, using in-memory fallback");
        }
        InMemoryCounter c = inMemory.get(key);
        return c != null && c.isActive() && c.count.get() >= limit;
    }

    private void increment(String key) {
        try {
            if (redis != null) {
                Long count = redis.opsForValue().increment(key);
                if (count != null && count == 1L) {
                    redis.expire(key, LOCKOUT_WINDOW);
                }
                return;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for rate-limit write, using in-memory fallback");
        }
        inMemory.computeIfAbsent(key, k -> new InMemoryCounter()).count.incrementAndGet();
    }

    private List<String> keys(String username) {
        return List.of(sourceKey(username), anyPeerKey(username));
    }

    private String sourceKey(String username) {
        return REDIS_KEY_PREFIX + sanitize(username) + ":" + clientIp();
    }

    private String anyPeerKey(String username) {
        return REDIS_KEY_PREFIX + ANY_PEER_PREFIX + sanitize(username);
    }

    private String sanitize(String username) {
        return username == null ? "unknown" : username.replaceAll("[^a-zA-Z0-9@._-]", "_");
    }

    /**
     * The immediate socket peer — never a header value.
     *
     * <p>Reading {@code X-Forwarded-For} made the lockout advisory: rotating the
     * header reset the counter, so an attacker guessing a password could send
     * unlimited attempts. The peer cannot be chosen by the caller. Through Caddy
     * it is the proxy for every request, which is why the username-wide counter
     * exists alongside this one.
     */
    private String clientIp() {
        try {
            HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String peer = req.getRemoteAddr();
            return peer != null && !peer.isBlank() ? peer : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static class InMemoryCounter {
        final AtomicInteger count = new AtomicInteger(0);
        final Instant windowStart = Instant.now();

        boolean isActive() {
            return Instant.now().isBefore(windowStart.plus(LOCKOUT_WINDOW));
        }
    }
}
