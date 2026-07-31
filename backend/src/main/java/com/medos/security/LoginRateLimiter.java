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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Brute-force protection for the login endpoint.
 *
 * Tracks failed attempts per (username + client IP) in Redis with a TTL.
 * If Redis is unavailable, falls back to a process-local counter so the
 * application never fails open on the availability axis.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);
    static final String REDIS_KEY_PREFIX = "medos:login:fail:";

    private final StringRedisTemplate redis;

    private final ConcurrentHashMap<String, InMemoryCounter> inMemory = new ConcurrentHashMap<>();

    public LoginRateLimiter(@Autowired(required = false) StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Returns true when the caller should be locked out (too many recent failures). */
    public boolean isBlocked(String username) {
        String key = buildKey(username);
        try {
            if (redis != null) {
                String raw = redis.opsForValue().get(key);
                return raw != null && Integer.parseInt(raw) >= MAX_FAILED_ATTEMPTS;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for rate-limit read, using in-memory fallback");
        }
        InMemoryCounter c = inMemory.get(key);
        return c != null && c.isActive() && c.count.get() >= MAX_FAILED_ATTEMPTS;
    }

    public void recordFailure(String username) {
        String key = buildKey(username);
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

    /** Called after a successful login so a previous lockout is cleared. */
    public void reset(String username) {
        String key = buildKey(username);
        try {
            if (redis != null) {
                redis.delete(key);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for rate-limit reset");
        }
        inMemory.remove(key);
    }

    private String buildKey(String username) {
        return REDIS_KEY_PREFIX + sanitize(username) + ":" + clientIp();
    }

    private String sanitize(String username) {
        return username == null ? "unknown" : username.replaceAll("[^a-zA-Z0-9@._-]", "_");
    }

    private String clientIp() {
        try {
            HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            return xff != null ? xff.split(",")[0].trim() : req.getRemoteAddr();
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
