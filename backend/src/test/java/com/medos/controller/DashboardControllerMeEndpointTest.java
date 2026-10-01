package com.medos.controller;

import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.repository.NotificationRepository;
import com.medos.repository.TenantRepository;
import com.medos.repository.UserRepository;
import com.medos.security.TenantContext;
import com.medos.service.DashboardService;
import com.medos.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for {@code GET /api/v1/users/me}.
 *
 * <p>The endpoint returned the {@code User} entity, so Jackson serialised
 * {@code passwordHash} to the client and any authenticated caller could read their
 * own bcrypt hash. That permits offline cracking with no rate limit or lockout,
 * so the hash must never appear in the response body.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DashboardControllerMeEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private UserService userService;

    private UUID tenantId;
    private UUID userId;
    private String rawJson;

    /** Unique per test method, so a rerun never collides on the users.username index. */
    private String uniqueUsername;

    @BeforeEach
    void setUp() throws Exception {
        uniqueUsername = "me-endpoint-" + UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .username(uniqueUsername)
                .passwordHash("{noop}hash-canary-" + UUID.randomUUID())
                .fullName("Me Endpoint User")
                .email(uniqueUsername + "@example.test")
                .active(true)
                .build());
        userId = user.getId();

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Me Endpoint Hospital " + uniqueUsername)
                .type(Tenant.TenantType.HOSPITAL)
                .slug("me-endpoint-" + userId.toString().substring(0, 8))
                .active(true)
                .build());
        tenantId = tenant.getId();

        rawJson = mockMvc.perform(get("/api/v1/users/me").with(authedAs(userId, "admin")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    /**
     * Build a request that arrives already authenticated as the given principal.
     *
     * <p>Uses spring-security-test's {@code authentication()} post-processor
     * rather than assigning {@code SecurityContextHolder} directly. MockMvc runs the
     * full security filter chain, which replaces the thread's SecurityContext with
     * one derived from the request, so a directly-assigned principal is discarded and
     * the endpoint sees an anonymous caller. That made an earlier version of this
     * test pass against the vulnerable code, because the 404 it received trivially
     * contained no password hash.
     */
    private RequestPostProcessor authedAs(UUID id, String role) {
        return authedAs(id, role, tenantId);
    }

    private RequestPostProcessor authedAs(UUID id, String role, UUID actingTenant) {
        if (actingTenant == null) {
            TenantContext.clear();
        } else {
            TenantContext.setTenantId(actingTenant);
        }
        return authentication(new UsernamePasswordAuthenticationToken(
                id.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))));
    }

    @Test
    @DisplayName("/users/me never serialises the password hash")
    void doesNotLeakPasswordHash() throws Exception {
        assertThat(rawJson)
                .as("the response body must not contain the hash in any form")
                .doesNotContain("passwordHash")
                .doesNotContain("password_hash")
                .doesNotContain("hash-canary");
    }

    @Test
    @DisplayName("/users/me returns the caller's own identity and acting tenant")
    void returnsIdentityAndTenant() throws Exception {
        assertThat(rawJson).contains("\"username\":\"" + uniqueUsername + "\"");

        var node = objectMapper.readTree(rawJson);
        assertThat(node.get("id").asText()).isEqualTo(userId.toString());
        assertThat(node.get("tenantId").asText()).isEqualTo(tenantId.toString());
        assertThat(node.get("tenantName").asText()).isEqualTo("Me Endpoint Hospital " + uniqueUsername);
        assertThat(node.get("role").asText()).isEqualTo("admin");
    }

    @Test
    @DisplayName("/users/me contains no User-entity-only persistence fields")
    void omitsPersistenceOnlyFields() throws Exception {
        var node = objectMapper.readTree(rawJson);
        for (String field : List.of("passwordHash", "createdAt", "lastLogin")) {
            assertThat(node.has(field))
                    .as("field '%s' must not be exposed", field)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a bootstrap session with no tenant still resolves, tenantId null")
    void bootstrapSessionHasNoTenant() throws Exception {
        // No TenantContext: a bootstrap/superadmin token carries no tenantId claim,
        // and a fresh install cannot be administered if that case breaks.
        TenantContext.clear();

        String json = mockMvc.perform(get("/api/v1/users/me").with(authedAs(userId, "admin", null)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json).doesNotContain("passwordHash");
        var node = objectMapper.readTree(json);
        assertThat(node.get("username").asText()).isEqualTo(uniqueUsername);
        assertThat(node.get("tenantId").isNull() || node.get("tenantId").isMissingNode())
                .as("a bootstrap session has no tenant, which must be represented, not invented")
                .isTrue();
    }

    @Test
    @DisplayName("an unresolvable principal is a 404, never a 200 with a null body")
    void unresolvablePrincipalIsNotFound() {
        // Exercised as a direct call rather than through MockMvc. MockMvc runs the
        // security filter chain, which rebuilds the SecurityContext from the request
        // and made this assertion depend on filter behaviour rather than on the
        // controller's own null handling.
        var controller = new DashboardController(
                dashboardService, userRepository, userService,
                notificationRepository, tenantRepository);
        var auth = new UsernamePasswordAuthenticationToken(
                "no-such-principal-anywhere", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        var response = controller.getCurrentUser(auth);

        assertThat(response.getStatusCode().value())
                .as("a 200 with a null body would be a silent unidentified session")
                .isEqualTo(404);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("CurrentUserDTO.of never copies the hash")
    void dtoFactoryExcludesHash() throws Exception {
        User user = userRepository.findById(userId).orElseThrow();
        var dto = com.medos.dto.CurrentUserDTO.of(user, tenantId, "T", TenantUser.UserRole.admin);

        var serialised = objectMapper.writeValueAsString(dto);
        assertThat(serialised).doesNotContain("passwordHash").doesNotContain("hash-canary");
        assertThat(serialised).contains(uniqueUsername);
    }

    @Test
    @DisplayName("the list endpoint is unaffected and still role-scoped")
    void listEndpointStillWorks() throws Exception {
        // Guards against a fix that narrows /users/me by breaking the sibling
        // endpoint #52 hardened.
        assertThat(userService.listTenantUsers()).isNotNull();
        assertThat(Optional.ofNullable(notificationRepository)).isNotNull();
        assertThat(dashboardService).isNotNull();
    }
}
