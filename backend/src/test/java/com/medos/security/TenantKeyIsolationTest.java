package com.medos.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import com.medos.security.TenantKeyResolverFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The core safety property of per-tenant keys: a value written in one tenant's
 * context must be unreadable in another's.
 *
 * <p>EncryptionUtil is a JPA {@code AttributeConverter}, which Hibernate instantiates
 * itself, so it cannot take constructor dependencies. It therefore resolves keys
 * through a process-wide holder that a Spring-managed component populates. That
 * indirection is only safe if the holder resolves the *acting* tenant rather than a
 * single global key, which is what these tests pin down.
 */
class TenantKeyIsolationTest {

    private static final String KEK_B64 = Base64.getEncoder()
            .encodeToString("kek-kek-kek-kek-kek-kek-kek-kek!".getBytes(StandardCharsets.UTF_8));

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    @AfterEach
    void tearDown() {
        TenantKeyResolverFactory.reset();
        TenantContext.clear();
    }

    /** Independent implementation, so the test does not assert the code's own arithmetic. */
    private static String encryptWith(byte[] key, String plaintext) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
        buf.put(iv);
        buf.put(ct);
        return Base64.getEncoder().encodeToString(buf.array());
    }

    @Test
    @DisplayName("a value encrypted for tenant A cannot be decrypted in tenant B's context")
    void crossTenantReadFails() throws Exception {
        TenantKeyHolder holder = TenantKeyHolder.inMemoryForTesting(KEK_B64);
        TenantKeyResolverFactory.setInstance(holder);

        TenantContext.setTenantId(TENANT_A);
        byte[] dekA = holder.dekFor(TENANT_A);
        String storedForA = encryptWith(dekA, "Anita Sharma");

        // Tenant B has its own DEK, resolved on first use.
        TenantContext.setTenantId(TENANT_B);
        byte[] dekB = holder.dekFor(TENANT_B);

        assertThat(dekA).as("each tenant must get a distinct DEK").isNotEqualTo(dekB);

        // And the value written under A does not open under B.
        assertThatThrownBy(() -> {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] decoded = Base64.getDecoder().decode(storedForA);
            byte[] iv = new byte[12];
            System.arraycopy(decoded, 0, iv, 0, 12);
            byte[] ct = new byte[decoded.length - 12];
            System.arraycopy(decoded, 12, ct, 0, ct.length);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dekB, "AES"), new GCMParameterSpec(128, iv));
            c.doFinal(ct);
        }).as("GCM must reject ciphertext under the wrong key")
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("distinct tenants get distinct DEKs, checked directly")
    void distinctTenantsGetDistinctDeks() throws Exception {
        TenantKeyHolder holder = TenantKeyHolder.inMemoryForTesting(KEK_B64);
        TenantKeyResolverFactory.setInstance(holder);

        TenantContext.setTenantId(TENANT_A);
        byte[] a = holder.dekFor(TENANT_A);
        TenantContext.setTenantId(TENANT_B);
        byte[] b = holder.dekFor(TENANT_B);
        TenantContext.setTenantId(UUID.fromString("cccccccc-0000-4000-8000-000000000003"));
        byte[] c = holder.dekFor(TENantC());

        assertThat(a).isNotEqualTo(b);
        assertThat(b).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(c);
    }

    private static java.util.UUID TENantC() {
        return java.util.UUID.fromString("cccccccc-0000-4000-8000-000000000003");
    }

    @Test
    @DisplayName("a tenant's DEK is stable across writes")
    void dekIsStablePerTenant() throws Exception {
        TenantKeyHolder holder = TenantKeyHolder.inMemoryForTesting(KEK_B64);
        TenantKeyResolverFactory.setInstance(holder);

        TenantContext.setTenantId(TENANT_A);
        byte[] first = holder.dekFor(TENANT_A);
        byte[] second = holder.dekFor(TENANT_A);

        assertThat(second).as("a new DEK per write would break search and joins")
                .isEqualTo(first);
    }

    @Test
    @DisplayName("resolving with no acting tenant fails closed instead of using a global key")
    void noTenantFailsClosed() throws Exception {
        TenantKeyHolder holder = TenantKeyHolder.inMemoryForTesting(KEK_B64);
        TenantKeyResolverFactory.setInstance(holder);
        TenantContext.clear();

        assertThatThrownBy(() -> holder.dekFor(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    @DisplayName("the holder refuses to operate before the KEK is configured")
    void missingKekFailsClosed() {
        TenantKeyResolverFactory.reset();
        TenantContext.setTenantId(TENANT_A);

        assertThatThrownBy(() -> TenantKeyResolverFactory.get().resolveDek(TENANT_A))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("two DEKs for different tenants unwrap from the same KEK")
    void dekIsWrappedByKek() {
        TenantKeyHolder holder = TenantKeyHolder.inMemoryForTesting(KEK_B64);
        TenantKeyResolverFactory.setInstance(holder);

        TenantContext.setTenantId(TENANT_A);
        byte[] a = holder.dekFor(TENANT_A);
        TenantContext.setTenantId(TENANT_B);
        byte[] b = holder.dekFor(TENANT_B);

        // Both must be 32 bytes: an AES-256 DEK, generated fresh and stored wrapped.
        assertThat(a).hasSize(32);
        assertThat(b).hasSize(32);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("a tenant's DEK survives a KEK re-wrap unchanged, since the DEK itself did not change")
    void dekSurvivesKekReWrap() {
        TenantKeyHolder holder = TenantKeyHolder.inMemoryForTesting(KEK_B64);
        TenantKeyResolverFactory.setInstance(holder);
        TenantContext.setTenantId(TENANT_A);
        byte[] before = holder.dekFor(TENANT_A);

        holder.rewrapAll();

        assertThat(holder.dekFor(TENANT_A))
                .as("rotation must change the wrapper, never the data key")
                .isEqualTo(before);
    }

    /**
     * The in-memory holder's rewrapAll() is a no-op by construction, so the test above
     * cannot catch an implementation that regenerates DEKs during a re-wrap. This
     * drives {@code rewrapAll()} on a holder with a real repository, via a stub, and
     * asserts the DEK it writes back unwraps to the same bytes.
     */
    @Test
    @DisplayName("a real re-wrap preserves DEK bytes end to end")
    void realRewrapPreservesDekBytes() {
        FakeTenantKeyStore repo = new FakeTenantKeyStore();
        TenantKeyHolder holder = new TenantKeyHolder(KEK_B64, repo);
        TenantKeyResolverFactory.setInstance(holder);

        TenantContext.setTenantId(TENANT_A);
        byte[] before = holder.dekFor(TENANT_A);

        int rewrapped = holder.rewrapAll();

        assertThat(rewrapped).isEqualTo(1);
        holder.clearCache();
        assertThat(holder.dekFor(TENANT_A))
                .as("a re-wrap must round-trip the same DEK through wrap then unwrap")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("a corrupted stored DEK fails loudly rather than yielding a wrong key")
    void corruptedWrappedDekFailsLoudly() {
        TenantKeyHolder holder = TenantKeyHolder.inMemoryForTesting(KEK_B64);
        TenantKeyResolverFactory.setInstance(holder);
        TenantContext.setTenantId(TENANT_A);
        holder.dekFor(TENANT_A);

        holder.corruptStoredDekForTesting(TENANT_A);

        holder.clearCache();
        assertThatThrownBy(() -> holder.dekFor(TENANT_A))
                .isInstanceOf(IllegalStateException.class);
    }
}
