package com.medos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Rotating a client-supplied header must not reset the brute-force counter.
 *
 * <p>The key was {@code medos:login:fail:{username}:{ip}} with the IP taken
 * from the first {@code X-Forwarded-For} value. Caddy overwrites that header,
 * so through the production edge it is trustworthy — but the limiter is the only
 * thing standing between an attacker and unlimited password guesses, and it
 * should not depend on which proxy happens to be in front.
 *
 * <p>An address the client sent is therefore not part of the key. The socket
 * peer is used instead: through Caddy that is always the proxy, which weakens
 * per-source limiting but cannot be defeated from the browser. A username-only
 * counter is layered on top so a distributed attack is still bounded.
 */
class LoginRateLimiterHeaderSpoofingTest {

    private final Map<String, AtomicLong> counts = new HashMap<>();
    private StringRedisTemplate redis;
    private LoginRateLimiter limiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // A counter store behind the Redis API, so the limiter's own key
        // construction is what these tests exercise.
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(valueOps.get(any())).thenAnswer(inv -> {
            AtomicLong c = counts.get(inv.getArgument(0));
            return c == null ? null : String.valueOf(c.get());
        });
        when(valueOps.increment(any())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return counts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        });
        redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        limiter = new LoginRateLimiter(redis);
    }

    private void requestFrom(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("rotating X-Forwarded-For does not reset the counter")
    void spoofedHeaderDoesNotResetTheCounter() {
        // Behind the proxy, the socket peer is constant. The client rotates the
        // header it believes will be trusted.
        for (int attempt = 1; attempt <= LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            requestFrom("172.18.0.5", "203.0.113." + attempt);
            assertThat(limiter.isBlocked("admin"))
                    .as("attempt %d must not yet be locked", attempt)
                    .isFalse();
            limiter.recordFailure("admin");
        }

        requestFrom("172.18.0.5", "203.0.113.99");
        assertThat(limiter.isBlocked("admin"))
                .as("a sixth attempt from a brand-new spoofed address must still be blocked")
                .isTrue();
    }

    @Test
    @DisplayName("an unknown peer cannot be used to skip the lockout")
    void unknownPeerStillCounts() {
        RequestContextHolder.resetRequestAttributes(); // no request bound at all

        for (int i = 0; i < LoginRateLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure("admin");
        }

        assertThat(limiter.isBlocked("admin")).isTrue();
    }

    @Test
    @DisplayName("the key no longer contains a client-supplied address")
    void keyIsFreeOfHeaderValues() {
        requestFrom("172.18.0.5", "1.2.3.4, 5.6.7.8");
        limiter.recordFailure("admin");

        assertThat(counts.keySet().toString())
                .doesNotContain("1.2.3.4")
                .doesNotContain("5.6.7.8")
                .contains("172.18.0.5");
    }

    @Test
    @DisplayName("a username-only counter also blocks, bounding a distributed attack")
    void usernameOnlyCounterAlsoBlocks() {
        // Different real source addresses — a botnet rather than one host.
        for (int i = 1; i <= LoginRateLimiter.ANY_PEER_MAX_FAILED_ATTEMPTS; i++) {
            requestFrom("172.18.0." + (i % 200 + 1), null);
            limiter.recordFailure("admin");
        }

        requestFrom("172.18.0.250", null);
        assertThat(limiter.isBlocked("admin"))
                .as("many distinct source addresses must not grant unlimited guesses")
                .isTrue();
    }

    @Test
    @DisplayName("the username-wide counter is not a cheap lockout lever")
    void usernameWideCounterIsHarderToReach() {
        // One attacker, one username, spread over distinct addresses. This must
        // NOT lock the account out: otherwise anyone who knows a clinician's
        // username can deny them access for the whole window with a handful of
        // requests, which is a denial-of-service the fix would be introducing.
        for (int i = 1; i < LoginRateLimiter.ANY_PEER_MAX_FAILED_ATTEMPTS; i++) {
            requestFrom("172.18.0." + (i % 200 + 1), null);
            assertThat(limiter.isBlocked("admin"))
                    .as("attempt %d must not lock a named account out", i)
                    .isFalse();
            limiter.recordFailure("admin");
        }
    }

    @Test
    @DisplayName("a single source is still locked out at the original threshold")
    void singleSourceStillBlocksAtFive() {
        for (int i = 0; i < LoginRateLimiter.MAX_FAILED_ATTEMPTS; i++) {
            requestFrom("172.18.0.5", null);
            limiter.recordFailure("admin");
        }
        requestFrom("172.18.0.5", null);

        assertThat(limiter.isBlocked("admin"))
                .as("the per-source limit must not have been weakened")
                .isTrue();
    }

    @Test
    @DisplayName("a successful login still clears the counters")
    void resetClearsBothCounters() {
        requestFrom("172.18.0.5", "203.0.113.1");
        limiter.recordFailure("admin");
        limiter.reset("admin");

        requestFrom("172.18.0.5", "203.0.113.1");
        assertThat(limiter.isBlocked("admin")).isFalse();
        org.mockito.Mockito.verify(redis, org.mockito.Mockito.atLeastOnce()).delete(anyString());
    }
}
