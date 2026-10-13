package com.medos.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.medos.entity.Tenant;
import com.medos.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A platform administrator needs to see which organisations exist.
 *
 * <p>{@code TenantService.getAllTenants()} has been there all along with no
 * caller: the capability was implemented as a service and never exposed. A
 * super-admin could provision hospitals one at a time with no record of what
 * they had already created, which is how a duplicate slug gets discovered from
 * a 409 rather than from a list.
 *
 * <p>SUPER_ADMIN only. Every tenant is visible in this response, so it must not
 * be reachable by a hospital admin — that would be the cross-tenant read this
 * system is built to prevent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantListAccessTest {

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantService tenantService;

    private Tenant tenant(UUID id, String name, String slug, Tenant.TenantType type, Boolean active) {
        Tenant t = new Tenant();
        t.setId(id);
        t.setName(name);
        t.setSlug(slug);
        t.setType(type);
        t.setActive(active);
        t.setContactEmail("hello@" + slug + ".test");
        t.setContactPhone("+91 90000 00000");
        t.setAddress("1 Test Street");
        return t;
    }

    @BeforeEach
    void setUp() {
        when(tenantService.getAllTenants()).thenReturn(List.of(
                tenant(TENANT_A, "Asha Rao Hospital", "asha-rai", Tenant.TenantType.HOSPITAL, true),
                tenant(TENANT_B, "Bilal's Clinic", "bilal-clinic", Tenant.TenantType.CLINIC, false)));
    }

    @Test
    @DisplayName("a platform super-admin sees every onboarded organisation")
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminListsTenants() throws Exception {
        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Asha Rao Hospital"))
                .andExpect(jsonPath("$[0].slug").value("asha-rai"))
                .andExpect(jsonPath("$[0].type").value("HOSPITAL"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    @DisplayName("a deactivated organisation is listed, not hidden")
    @WithMockUser(roles = "SUPER_ADMIN")
    void inactiveTenantsAreStillListed() throws Exception {
        // Hiding them would make "why can't this user sign in" unanswerable.
        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.active == false)]", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("a hospital admin cannot list other organisations")
    @WithMockUser(roles = "ADMIN")
    void hospitalAdminIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an ordinary user cannot list organisations")
    @WithMockUser(roles = "DOCTOR")
    void doctorIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the response carries no secret or config internals")
    @WithMockUser(roles = "SUPER_ADMIN")
    void responseIsADtoNotTheEntity() throws Exception {
        Tenant withConfig = tenant(TENANT_A, "A", "a", Tenant.TenantType.HOSPITAL, true);
        withConfig.setConfig(java.util.Map.of("secret", "value"));
        when(tenantService.getAllTenants()).thenReturn(List.of(withConfig));

        String body = mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .as("tenant config can hold integration keys; it is not for a listing")
                .doesNotContain("secret")
                .doesNotContain("config");
    }
}
