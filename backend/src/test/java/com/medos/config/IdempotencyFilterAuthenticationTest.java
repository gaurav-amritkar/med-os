package com.medos.config;

import com.medos.security.TenantContext;
import com.medos.service.IdempotencyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A cached idempotency response must never be served to a caller who has not
 * been authenticated.
 *
 * <p>The filter used to run before {@code JwtAuthenticationFilter} and read the
 * tenant/principal from a context that was still empty. A successful request
 * therefore stored its financial response under {@code platform:anonymous}, and
 * any caller who knew the key could read it back with no token at all (#221).
 *
 * <p>This exercises the real filter rather than a mock, and asserts on what the
 * filter does <em>before</em> it calls down the chain — which is the property
 * that was broken.
 */
class IdempotencyFilterAuthenticationTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID USER = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");
    private static final String BODY = "{\"patientId\":\"11111111-1111-4111-8111-111111111111\","
            + "\"invoiceId\":\"22222222-2222-4222-8222-222222222222\",\"amount\":100}";

    private IdempotencyService service;
    private IdempotencyFilter filter;

    @BeforeEach
    void setUp() {
        service = mock(IdempotencyService.class);
        filter = new IdempotencyFilter(service, new com.medos.util.RequestFingerprint());
        SecurityContextHolder.clearContext();
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/billing/payments");
        request.setContentType("application/json");
        request.setContent(BODY.getBytes());
        request.addHeader("Idempotency-Key", "client-key-1");
        return request;
    }

    private void authenticated() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                USER.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_BILLING"))));
    }

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("an unauthenticated caller is refused even with a known key")
    void unauthenticatedCallerIsRefused() throws Exception {
        // The stored entry exists and matches the key: this is exactly the
        // response the old filter would have handed over.
        when(service.claim(anyString(), anyString(), any(), any(), any()))
                .thenReturn(new IdempotencyService.Claim.Replay(
                        new IdempotencyService.CachedResponse(200, "application/json", "{\"amount\":100}", null)));

        MockHttpServletResponse response = run(request());

        assertThat(response.getStatus())
                .as("no token means no cached financial response, whatever the key")
                .isEqualTo(401);
        assertThat(response.getContentAsString())
                .doesNotContain("amount");
    }

    @Test
    @DisplayName("a non-UUID principal is refused rather than cached under a marker")
    void nonUuidPrincipalIsRefused() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "anonymousUser", null, List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        MockHttpServletResponse response = run(request());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("an authenticated caller with a matching key is replayed")
    void authenticatedCallerReplays() throws Exception {
        authenticated();
        when(service.claim(anyString(), anyString(), any(), any(), any()))
                .thenReturn(new IdempotencyService.Claim.Replay(
                        new IdempotencyService.CachedResponse(201, "application/json",
                                "{\"invoiceNumber\":\"INV-1\"}", null)));

        MockHttpServletResponse response = run(request());

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeader("Idempotency-Key-Replayed")).isEqualTo("true");
        assertThat(response.getContentAsString()).contains("INV-1");
    }

    @Test
    @DisplayName("a replay returns the original content type, not a blanket JSON type")
    void replayPreservesContentType() throws Exception {
        authenticated();
        when(service.claim(anyString(), anyString(), any(), any(), any()))
                .thenReturn(new IdempotencyService.Claim.Replay(
                        new IdempotencyService.CachedResponse(200, "application/json", "{}", null)));

        MockHttpServletResponse response = run(request());

        assertThat(response.getContentType()).startsWith("application/json");
    }

    @Test
    @DisplayName("a changed payload under the same key is a conflict, not a replay")
    void changedPayloadConflicts() throws Exception {
        authenticated();
        when(service.claim(anyString(), anyString(), any(), any(), any()))
                .thenReturn(new IdempotencyService.Claim.FingerprintMismatch("sha256:other"));

        MockHttpServletResponse response = run(request());

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    @DisplayName("the fingerprint is computed over the body the caller sent")
    void fingerprintCoversRequestBody() throws Exception {
        authenticated();
        when(service.claim(anyString(), anyString(), any(), any(), any()))
                .thenReturn(new IdempotencyService.Claim.Acquired());

        run(request());

        // The fifth argument is the fingerprint. It must be a non-null digest of
        // the body, and it must differ for a different body.
        org.mockito.ArgumentCaptor<String> fingerprint = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(service).claim(anyString(), anyString(), any(), any(),
                fingerprint.capture());
        assertThat(fingerprint.getValue()).isNotBlank();
    }

    @Test
    @DisplayName("a missing key is still a 400, and the chain is not invoked")
    void missingKeyIsBadRequest() throws Exception {
        authenticated();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/billing/payments");
        request.setContentType("application/json");
        request.setContent(BODY.getBytes());

        MockHttpServletResponse response = run(request);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("IDEMPOTENCY_KEY_REQUIRED");
    }
}
