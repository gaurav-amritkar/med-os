package com.medos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.security.TenantContext;
import com.medos.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Staff management has to be reachable by a tenant admin and by nobody else.
 *
 * <p>Before this there was no {@code /users} write surface at all, so a hospital could not
 * add a doctor. The risk in adding one is the opposite failure: any authenticated user
 * being able to mint themselves an admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerAccessTest {

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("an admin can list their staff")
    @WithMockUser(roles = "ADMIN")
    void adminListsStaff() throws Exception {
        org.mockito.Mockito.when(userService.listUsers(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an admin can add a doctor")
    @WithMockUser(roles = "ADMIN")
    void adminCreatesDoctor() throws Exception {
        org.mockito.Mockito.when(userService.createUser(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.medos.dto.UserDTO.builder()
                        .username("dr.sharma").role("doctor").active(true)
                        .mustChangePassword(true).build());

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dr.sharma","fullName":"Anita Sharma",
                                 "role":"doctor","password":"Temp@12345",
                                 "email":"anita@example.test"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("dr.sharma"))
                .andExpect(jsonPath("$.role").value("doctor"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    @DisplayName("a doctor cannot add staff")
    @WithMockUser(roles = "DOCTOR")
    void doctorCannotCreateStaff() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"intruder","fullName":"Intruder",
                                 "role":"admin","password":"Temp@12345"}"""))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.verify(userService, org.mockito.Mockito.never())
                .createUser(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a receptionist cannot add staff")
    @WithMockUser(roles = "RECEPTIONIST")
    void receptionistCannotCreateStaff() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"intruder","fullName":"Intruder",
                                 "role":"admin","password":"Temp@12345"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an anonymous caller cannot list staff")
    void anonymousCannotListStaff() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an admin can change a role")
    @WithMockUser(roles = "ADMIN")
    void adminChangesRole() throws Exception {
        UUID userId = UUID.randomUUID();
        org.mockito.Mockito.when(userService.updateRole(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(userId),
                        org.mockito.ArgumentMatchers.eq(TenantUser.UserRole.nurse)))
                .thenReturn(com.medos.dto.UserDTO.builder()
                        .username("dr.sharma").role("nurse").active(true).build());

        mockMvc.perform(put("/api/v1/users/{id}/role", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"nurse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("nurse"));
    }

    @Test
    @DisplayName("an admin can end and restore a member's access")
    @WithMockUser(roles = "ADMIN")
    void adminTogglesAccess() throws Exception {
        UUID userId = UUID.randomUUID();
        org.mockito.Mockito.when(userService.setActive(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(userId),
                        org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(com.medos.dto.UserDTO.builder()
                        .username("dr.sharma").role("doctor").active(false).build());

        mockMvc.perform(put("/api/v1/users/{id}/active", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("a short password is rejected by validation, not by the service")
    @WithMockUser(roles = "ADMIN")
    void shortPasswordRejected() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dr.sharma","fullName":"Anita Sharma",
                                 "role":"doctor","password":"short"}"""))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verify(userService, org.mockito.Mockito.never())
                .createUser(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("an unknown role is rejected rather than silently defaulted")
    @WithMockUser(roles = "ADMIN")
    void unknownRoleRejected() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dr.sharma","fullName":"Anita Sharma",
                                 "role":"superuser","password":"Temp@12345"}"""))
                .andExpect(status().isBadRequest());
    }
}
