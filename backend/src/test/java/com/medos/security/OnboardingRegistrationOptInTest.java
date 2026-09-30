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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The opt-in direction of {@code medos.onboarding.public-signup}.
 *
 * <p>{@link OnboardingRegistrationGuardTest} proves the endpoint is refused
 * when the flag is false. This proves the flag is wired at all: a switch that
 * cannot be shown to open the gate is indistinguishable from a dead one, and
 * someone would reasonably assume setting it to true had enabled self-serve
 * signup when it had not.
 *
 * <p>This is not an endorsement of leaving it on. Registration still has no
 * email verification, captcha or rate limiting, so exposing it is only
 * appropriate on a network an operator controls.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.cache.type=simple",
        "spring.data.redis.repositories.enabled=false",
        "medos.onboarding.public-signup=true",
})
@Transactional
class OnboardingRegistrationOptInTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantUserRepository tenantUserRepository;

    private static final String VALID_BODY = """
            {
              "name": "Opted In Hospital",
              "type": "HOSPITAL",
              "slug": "opted-in-hospital",
              "contactEmail": "contact@opted-in.test",
              "contactPhone": "+919800000001",
              "adminUsername": "optin.admin",
              "adminPassword": "Sup3rSecret!pass",
              "adminEmail": "admin@opted-in.test"
            }
            """;

    @BeforeEach
    void clean() {
        tenantUserRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void anonymousRegistrationSucceedsWhenExplicitlyEnabled() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/register")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void theTenantAndItsAdminAreActuallyCreated() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/register")
                .contentType("application/json")
                .content(VALID_BODY));

        assertThat(tenantRepository.findAll())
                .extracting(t -> t.getSlug())
                .contains("opted-in-hospital");
        assertThat(userRepository.findAll())
                .extracting(u -> u.getUsername())
                .contains("optin.admin");
        // The membership is what makes the new account an admin.
        assertThat(tenantUserRepository.findAll())
                .extracting(m -> m.getRole().name())
                .contains("admin");
    }

    @Test
    void theResponseDoesNotEchoTheAdminPassword() throws Exception {
        String body = mockMvc.perform(post("/api/v1/onboarding/register")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.slug").value("opted-in-hospital"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("Sup3rSecret!pass");
    }
}
