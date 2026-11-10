package com.medos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.medos.service.IdempotencyService.CachedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * An idempotency key must mean "this operation, once" to one principal.
 *
 * <p>The key was {@code idempotency:{endpoint}:{key}} with no tenant and no
 * user. Two hospitals sending the same key collided on one entry, so the second
 * caller received the first caller's stored payment or invoice — a different
 * patient's amount, method and id. The endpoints guarded are exactly the ones
 * where that matters.
 *
 * <p>Separately, the read-then-write sequence let two concurrent requests with
 * the same key both miss and both execute, which is the double-charge the
 * feature exists to prevent. The claim is now atomic.
 */
class IdempotencyScopeAndAtomicityTest {

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");
    private static final UUID USER_A = UUID.fromString("cccccccc-0000-4000-8000-000000000003");
    private static final UUID USER_B = UUID.fromString("dddddddd-0000-4000-8000-000000000004");

    private static final String FINGERPRINT = "sha256:body-A";
    private static final String OTHER_FINGERPRINT = "sha256:body-B";

    private final String endpoint = "billing/payments";
    private final String key = "client-key-1";

    private RedisTemplate<Object, Object> redis;
    private ValueOperations<Object, Object> ops;
    /** Models the atomic SETNX that Redis actually offers. */
    private final Map<String, Object> store = new ConcurrentHashMap<>();
    private final Map<String, Boolean> held = new ConcurrentHashMap<>();

    private IdempotencyService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ops = mock(ValueOperations.class);
        when(ops.get(any())).thenAnswer(inv -> store.get(((Object) inv.getArgument(0)).toString()));
        // setIfAbsent is the atomic claim: only the first caller for a key wins.
        when(ops.setIfAbsent(any(), any(), anyLong(), any()))
                .thenAnswer(inv -> held.putIfAbsent(((Object) inv.getArgument(0)).toString(), Boolean.TRUE) == null);
        // set is the completion write; it also clears the in-flight hold.
        org.mockito.Mockito.doAnswer(inv -> {
            String k = ((Object) inv.getArgument(0)).toString();
            held.remove(k);
            store.put(k, inv.getArgument(1, Object.class));
            return null;
        }).when(ops).set(any(), any(), anyLong(), any());

        redis = mock(RedisTemplate.class);
        when(redis.opsForValue()).thenReturn(ops);
        org.mockito.Mockito.doAnswer(inv -> {
            String k = ((Object) inv.getArgument(0)).toString();
            held.remove(k);
            return store.remove(k);
        }).when(redis).delete((Object) any());

        service = new IdempotencyService(redis);
    }

    @Test
    @DisplayName("two tenants using the same key do not see each other's response")
    void tenantsAreIsolated() {
        var first = service.claim(endpoint, key, TENANT_A, USER_A, FINGERPRINT);
        assertThat(first).isInstanceOf(IdempotencyService.Claim.Acquired.class);

        service.complete(endpoint, key, TENANT_A, USER_A, FINGERPRINT,
                new CachedResponse(201, "application/json", "{\"invoiceNumber\":\"INV-A\"}", null));

        // Same client key, different hospital. Must not replay the first response.
        var second = service.claim(endpoint, key, TENANT_B, USER_B, FINGERPRINT);
        assertThat(second)
                .as("a tenant must never receive another tenant's cached response")
                .isInstanceOf(IdempotencyService.Claim.Acquired.class);
    }

    @Test
    @DisplayName("two users in the same tenant do not see each other's response")
    void usersAreIsolated() {
        assertThat(service.claim(endpoint, key, TENANT_A, USER_A, FINGERPRINT))
                .isInstanceOf(IdempotencyService.Claim.Acquired.class);
        service.complete(endpoint, key, TENANT_A, USER_A, FINGERPRINT,
                new CachedResponse(201, "application/json", "{\"ref\":\"A\"}", null));

        assertThat(service.claim(endpoint, key, TENANT_A, USER_B, FINGERPRINT))
                .as("a user must never receive another user's cached response")
                .isInstanceOf(IdempotencyService.Claim.Acquired.class);
    }

    @Test
    @DisplayName("reusing a key with a different payload is a conflict, not a replay")
    void keyReusedWithDifferentPayloadConflicts() {
        service.claim(endpoint, key, TENANT_A, USER_A, FINGERPRINT);
        service.complete(endpoint, key, TENANT_A, USER_A, FINGERPRINT,
                new CachedResponse(201, "application/json", "{\"amount\":100}", FINGERPRINT));

        // Same principal, same key, different body. Replaying the stored response
        // would tell the client a different operation succeeded (#221).
        var reused = service.claim(endpoint, key, TENANT_A, USER_A, OTHER_FINGERPRINT);

        assertThat(reused)
                .as("a key bound to one payload must not answer a different payload")
                .isInstanceOf(IdempotencyService.Claim.FingerprintMismatch.class);
    }

    @Test
    @DisplayName("the same principal replaying the same key and payload gets its own response back")
    void samePrincipalReplays() {
        service.claim(endpoint, key, TENANT_A, USER_A, FINGERPRINT);
        service.complete(endpoint, key, TENANT_A, USER_A, FINGERPRINT,
                new CachedResponse(201, "application/json", "{\"invoiceNumber\":\"INV-A\"}", null));

        var replay = service.claim(endpoint, key, TENANT_A, USER_A, FINGERPRINT);

        assertThat(replay).isInstanceOf(IdempotencyService.Claim.Replay.class);
        CachedResponse cached = ((IdempotencyService.Claim.Replay) replay).response();
        assertThat(cached.status()).isEqualTo(201);
        assertThat(cached.body()).contains("INV-A");
    }

    @Test
    @DisplayName("a replay preserves the original status, not always 200")
    void replayPreservesStatus() {
        service.claim(endpoint, key, TENANT_A, USER_A, FINGERPRINT);
        service.complete(endpoint, key, TENANT_A, USER_A, FINGERPRINT,
                new CachedResponse(201, "application/json", "{\"created\":true}", null));

        var replay = service.claim(endpoint, key, TENANT_A, USER_A, FINGERPRINT);

        assertThat(((IdempotencyService.Claim.Replay) replay).response().status())
                .as("a 201 replayed as 200 tells the client this was not created now")
                .isEqualTo(201);
    }

    @Test
    @DisplayName("a void endpoint replays as an empty body")
    void voidEndpointReplays() {
        service.claim("pharmacy/dispense", key, TENANT_A, USER_A, FINGERPRINT);
        service.complete("pharmacy/dispense", key, TENANT_A, USER_A, FINGERPRINT,
                new CachedResponse(200, null, "", null));

        var replay = service.claim("pharmacy/dispense", key, TENANT_A, USER_A, FINGERPRINT);

        assertThat(((IdempotencyService.Claim.Replay) replay).response().body()).isEmpty();
    }

    @Test
    @DisplayName("concurrent requests with one key execute exactly once")
    void concurrentSameKeyExecutesOnce() throws Exception {
        int racers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger acquired = new AtomicInteger();

        for (int i = 0; i < racers; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (service.claim(endpoint, key, TENANT_A, USER_A, FINGERPRINT)
                            instanceof IdempotencyService.Claim.Acquired) {
                        acquired.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();

        assertThat(acquired.get())
                .as("only one caller may execute a given idempotent operation")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a failed operation releases the claim so a retry can proceed")
    void failedOperationReleasesTheClaim() {
        assertThat(service.claim(endpoint, key, TENANT_A, USER_A, FINGERPRINT))
                .isInstanceOf(IdempotencyService.Claim.Acquired.class);

        service.release(endpoint, key, TENANT_A, USER_A);

        assertThat(service.claim(endpoint, key, TENANT_A, USER_A, FINGERPRINT))
                .as("a 500 must not lock the client's key for 24 hours")
                .isInstanceOf(IdempotencyService.Claim.Acquired.class);
    }

    @Test
    @DisplayName("a concurrent caller while one is in flight is told to retry")
    void inFlightIsReported() {
        service.claim(endpoint, key, TENANT_A, USER_A, FINGERPRINT);

        var second = service.claim(endpoint, key, TENANT_A, USER_A, FINGERPRINT);

        assertThat(second)
                .isInstanceOf(IdempotencyService.Claim.InFlight.class);
    }
}
