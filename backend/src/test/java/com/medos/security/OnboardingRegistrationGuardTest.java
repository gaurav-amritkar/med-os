package com.medos.security;

import com.medos.repository.TenantRepository;
import com.medos.repository.TenantUserRepository;
import com.medos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant self-registration must not be anonymously reachable.
 *
 * <p>{@code POST /api/v1/onboarding/register} was listed in {@code permitAll}
 * with no {@code @PreAuthorize}, and the handler creates a tenant, a user and
 * an {@code admin} membership straight from the request body. Any unauthenticated
 * caller could therefore provision a hospital with full administrative rights,
 * without limit, without an audit entry and without being rate limited.
 *
 * <p>The fix gates the endpoint on {@code medos.onboarding.public-signup},
 * which defaults to false per ADR-0003. With signup closed, an anonymous request
 * must be refused and a tenant admin must be refused too — only a platform
 * super-admin may register a tenant, and that role does not exist yet, so
 * today the correct answer for every caller is "no".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.cache.type=simple",
        "spring.data.redis.repositories.enabled=false",
        // medos.onboarding.public-signup comes from application-test.yml, which
        // pins it to false so this test asserts the shipped default rather than
        // whatever a developer has configured locally.
})
@Transactional
class OnboardingRegistrationGuardTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantUserRepository tenantUserRepository;

    /**
     * Satisfies every constraint on {@code OnboardingRequest} — name, type,
     * slug, contact details and admin credentials — so the request is refused on
     * authorisation rather than on validation. A body that fails validation
     * would let the "no tenant created" assertion pass for the wrong reason.
     */
    private static final String VALID_BODY = """
            {
              "name": "Rogue Hospital",
              "type": "HOSPITAL",
              "slug": "rogue-hospital",
              "contactEmail": "contact@rogue.test",
              "contactPhone": "+919800000000",
              "adminUsername": "rogue.admin",
              "adminPassword": "Sup3rSecret!pass",
              "adminEmail": "rogue.admin@rogue.test"
            }
            """;

    @BeforeEach
    void clean() {
        tenantUserRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void anonymousRegistrationIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/register")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noTenantIsCreatedByAnAnonymousCall() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/register")
                        .contentType("application/json")
                        .content(VALID_BODY));

        org.assertj.core.api.Assertions.assertThat(tenantRepository.findAll())
                .as("a refused registration must leave no tenant behind")
                .isEmpty();
        org.assertj.core.api.Assertions.assertThat(userRepository.findAll()).isEmpty();
    }
}
