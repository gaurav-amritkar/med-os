package com.medos.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        limiter = new LoginRateLimiter(redis);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void notBlockedInitially() {
        assertFalse(limiter.isBlocked("admin"));
    }

    @Test
    void blockedAfterMaxFailures() {
        for (int i = 0; i < LoginRateLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure("admin");
        }
        when(valueOps.get(anyString())).thenReturn(String.valueOf(LoginRateLimiter.MAX_FAILED_ATTEMPTS));

        assertTrue(limiter.isBlocked("admin"));
    }

    @Test
    void resetClearsFailures() {
        limiter.recordFailure("admin");
        limiter.reset("admin");

        verify(redis).delete(anyString());
        assertFalse(limiter.isBlocked("admin"));
    }

    @Test
    void redisDownFallsBackToMemory() {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        for (int i = 0; i < LoginRateLimiter.MAX_FAILED_ATTEMPTS; i++) {
            limiter.recordFailure("admin");
        }
        // Redis still down for reads
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        assertTrue(limiter.isBlocked("admin"));
    }

    @Test
    void firstFailureSetsRedisTtl() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        limiter.recordFailure("admin");
        verify(redis).expire(anyString(), org.mockito.ArgumentMatchers.any());
    }
}
