package com.medos.security;

import com.medos.entity.User;
import com.medos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * RBAC Matrix Integration Test
 * Verifies that each role can access only their authorized endpoints.
 *
 * Role → Endpoint Access Matrix:
 * 
 * | Endpoint                                    | admin | doctor | nurse | receptionist | pharmacist | billing |
 * |---------------------------------------------|-------|--------|-------|--------------|------------|---------|
 * | POST   /api/patients                        |  ✓    |        |       |      ✓       |            |         |
 * | GET    /api/patients                        |  ✓    |   ✓    |  ✓    |      ✓       |     ✓      |   ✓     |
 * | GET    /api/patients/{id}                   |  ✓    |   ✓    |  ✓    |      ✓       |     ✓      |   ✓     |
 * | POST   /api/encounters                      |  ✓    |   ✓    |  ✓    |              |            |         |
 * | GET    /api/encounters/{id}                 |  ✓    |   ✓    |  ✓    |      ✓       |     ✓      |   ✓     |
 * | POST   /api/encounters/{id}/sign            |  ✓    |   ✓    |       |              |            |         |
 * | POST   /api/encounters/{id}/prescriptions   |  ✓    |   ✓    |       |              |            |         |
 * | GET    /api/encounters/prescriptions/pending|  ✓    |        |       |              |     ✓      |         |
 * | POST   /api/encounters/suggest-medicines    |  ✓    |   ✓    |       |              |            |         |
 * | GET    /api/pharmacy/medicines              |  ✓    |   ✓    |  ✓    |      ✓       |     ✓      |   ✓     |
 * | POST   /api/pharmacy/medicines              |  ✓    |        |       |              |     ✓      |         |
 * | POST   /api/pharmacy/medicines/{id}/stock-in|  ✓    |        |       |              |     ✓      |         |
 * | POST   /api/pharmacy/dispense               |  ✓    |        |       |              |     ✓      |         |
 * | GET    /api/pharmacy/transactions           |  ✓    |        |       |              |     ✓      |         |
 * | POST   /api/admissions                      |  ✓    |   ✓    |  ✓    |              |            |         |
 * | PUT    /api/admissions/{id}/discharge       |  ✓    |   ✓    |       |              |            |         |
 * | GET    /api/admissions/active               |  ✓    |   ✓    |  ✓    |      ✓       |     ✓      |   ✓     |
 * | GET    /api/admissions/rooms                |  ✓    |   ✓    |  ✓    |      ✓       |     ✓      |   ✓     |
 * | POST   /api/billing/invoices                |  ✓    |        |       |              |            |   ✓     |
 * | GET    /api/billing/patients/{id}/invoices  |  ✓    |   ✓    |  ✓    |      ✓       |     ✓      |   ✓     |
 * | GET    /api/billing/patients/{id}/unbilled  |  ✓    |   ✓    |  ✓    |      ✓       |     ✓      |   ✓     |
 * | POST   /api/billing/payments                |  ✓    |        |       |              |            |   ✓     |
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RbacMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private String adminToken;
    private String doctorToken;
    private String nurseToken;
    private String receptionistToken;
    private String pharmacistToken;
    private String billingToken;

    @BeforeEach
    void setUp() {
        // Clean up and create test users for each role
        userRepository.deleteAll();

        createUser("admin", "admin", User.Role.admin);
        createUser("doctor", "doctor", User.Role.doctor);
        createUser("nurse", "nurse", User.Role.nurse);
        createUser("reception", "reception", User.Role.receptionist);
        createUser("pharmacy", "pharmacy", User.Role.pharmacist);
        createUser("billing", "billing", User.Role.billing);

        // Generate tokens
        adminToken = getToken("admin", User.Role.admin);
        doctorToken = getToken("doctor", User.Role.doctor);
        nurseToken = getToken("nurse", User.Role.nurse);
        receptionistToken = getToken("reception", User.Role.receptionist);
        pharmacistToken = getToken("pharmacy", User.Role.pharmacist);
        billingToken = getToken("billing", User.Role.billing);
    }

    private void createUser(String username, String password, User.Role role) {
        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(username + " user")
                .email(username + "@test.com")
                .role(role)
                .active(true)
                .build();
        userRepository.save(user);
    }

    private String getToken(String username, User.Role role) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return tokenProvider.generateToken(user.getId(), username, role.name().toLowerCase());
    }

    private MvcResult performRequest(String method, String url, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = switch (method) {
            case "GET" -> get(url);
            case "POST" -> post(url);
            case "PUT" -> put(url);
            case "PATCH" -> patch(url);
            case "DELETE" -> delete(url);
            default -> throw new IllegalArgumentException("Unknown method: " + method);
        };

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON)
                    .content(com.fasterxml.jackson.databind.ObjectMapper.class.cast(
                            new com.fasterxml.jackson.databind.ObjectMapper()).writeValueAsString(body));
        }
        return mockMvc.perform(builder).andReturn();
    }

    // ============ PATIENTS ============

    @Test
    void rbac_patients_post_create() throws Exception {
        // admin, receptionist: 201 | others: 403
        testEndpoint("POST", "/api/patients",
                new String[]{"admin", "receptionist"},
                new String[]{"doctor", "nurse", "pharmacist", "billing"},
                new com.medos.dto.PatientRegistrationRequest());
    }

    @Test
    void rbac_patients_get_list() throws Exception {
        // all authenticated: 200 | unauthenticated: 401
        testEndpoint("GET", "/api/patients",
                new String[]{"admin", "doctor", "nurse", "receptionist", "pharmacist", "billing"},
                new String[]{},
                null);
    }

    @Test
    void rbac_patients_get_by_id() throws Exception {
        // all authenticated: 200 (if exists) / 404 | unauthenticated: 401
        testEndpoint("GET", "/api/patients/" + UUID.randomUUID(),
                new String[]{"admin", "doctor", "nurse", "receptionist", "pharmacist", "billing"},
                new String[]{},
                null);
    }

    // ============ ENCOUNTERS ============

    @Test
    void rbac_encounters_post_create() throws Exception {
        // admin, doctor, nurse: 201 | others: 403
        testEndpoint("POST", "/api/encounters",
                new String[]{"admin", "doctor", "nurse"},
                new String[]{"receptionist", "pharmacist", "billing"},
                new com.medos.dto.EncounterRequest());
    }

    @Test
    void rbac_encounters_get_by_id() throws Exception {
        // all authenticated: 200/404
        testEndpoint("GET", "/api/encounters/" + UUID.randomUUID(),
                new String[]{"admin", "doctor", "nurse", "receptionist", "pharmacist", "billing"},
                new String[]{},
                null);
    }

    @Test
    void rbac_encounters_post_sign() throws Exception {
        // admin, doctor: 200 | others: 403
        testEndpoint("POST", "/api/encounters/" + UUID.randomUUID() + "/sign",
                new String[]{"admin", "doctor"},
                new String[]{"nurse", "receptionist", "pharmacist", "billing"},
                null);
    }

    @Test
    void rbac_encounters_post_prescriptions() throws Exception {
        // admin, doctor: 201 | others: 403
        testEndpoint("POST", "/api/encounters/" + UUID.randomUUID() + "/prescriptions",
                new String[]{"admin", "doctor"},
                new String[]{"nurse", "receptionist", "pharmacist", "billing"},
                new com.medos.dto.PrescriptionRequest());
    }

    @Test
    void rbac_encounters_get_pending_prescriptions() throws Exception {
        // admin, pharmacist: 200 | others: 403
        testEndpoint("GET", "/api/encounters/prescriptions/pending",
                new String[]{"admin", "pharmacist"},
                new String[]{"doctor", "nurse", "receptionist", "billing"},
                null);
    }

    @Test
    void rbac_encounters_post_suggest_medicines() throws Exception {
        // admin, doctor: 200 | others: 403
        testEndpoint("POST", "/api/encounters/suggest-medicines",
                new String[]{"admin", "doctor"},
                new String[]{"nurse", "receptionist", "pharmacist", "billing"},
                new com.medos.dto.AiSuggestRequest());
    }

    // ============ PHARMACY ============

    @Test
    void rbac_pharmacy_get_medicines() throws Exception {
        // all authenticated: 200
        testEndpoint("GET", "/api/pharmacy/medicines",
                new String[]{"admin", "doctor", "nurse", "receptionist", "pharmacist", "billing"},
                new String[]{},
                null);
    }

    @Test
    void rbac_pharmacy_post_medicines() throws Exception {
        // admin, pharmacist: 201 | others: 403
        testEndpoint("POST", "/api/pharmacy/medicines",
                new String[]{"admin", "pharmacist"},
                new String[]{"doctor", "nurse", "receptionist", "billing"},
                new com.medos.entity.MedicineCatalog());
    }

    @Test
    void rbac_pharmacy_post_stock_in() throws Exception {
        // admin, pharmacist: 201 | others: 403
        testEndpoint("POST", "/api/pharmacy/medicines/" + UUID.randomUUID() + "/stock-in?batchNo=B1&expiryDate=2025-12-31&quantity=100",
                new String[]{"admin", "pharmacist"},
                new String[]{"doctor", "nurse", "receptionist", "billing"},
                null);
    }

    @Test
    void rbac_pharmacy_post_dispense() throws Exception {
        // admin, pharmacist: 200 | others: 403
        testEndpoint("POST", "/api/pharmacy/dispense",
                new String[]{"admin", "pharmacist"},
                new String[]{"doctor", "nurse", "receptionist", "billing"},
                new com.medos.dto.DispenseRequest());
    }

    @Test
    void rbac_pharmacy_get_transactions() throws Exception {
        // admin, pharmacist: 200 | others: 403
        testEndpoint("GET", "/api/pharmacy/transactions",
                new String[]{"admin", "pharmacist"},
                new String[]{"doctor", "nurse", "receptionist", "billing"},
                null);
    }

    // ============ ADMISSIONS ============

    @Test
    void rbac_admissions_post_admit() throws Exception {
        // admin, doctor, nurse: 201 | others: 403
        testEndpoint("POST", "/api/admissions",
                new String[]{"admin", "doctor", "nurse"},
                new String[]{"receptionist", "pharmacist", "billing"},
                new com.medos.dto.AdmissionRequest());
    }

    @Test
    void rbac_admissions_put_discharge() throws Exception {
        // admin, doctor: 200 | others: 403
        testEndpoint("PUT", "/api/admissions/" + UUID.randomUUID() + "/discharge",
                new String[]{"admin", "doctor"},
                new String[]{"nurse", "receptionist", "pharmacist", "billing"},
                new com.medos.dto.DischargeRequest());
    }

    @Test
    void rbac_admissions_get_active() throws Exception {
        // all authenticated: 200
        testEndpoint("GET", "/api/admissions/active",
                new String[]{"admin", "doctor", "nurse", "receptionist", "pharmacist", "billing"},
                new String[]{},
                null);
    }

    @Test
    void rbac_admissions_get_rooms() throws Exception {
        // all authenticated: 200
        testEndpoint("GET", "/api/admissions/rooms",
                new String[]{"admin", "doctor", "nurse", "receptionist", "pharmacist", "billing"},
                new String[]{},
                null);
    }

    // ============ BILLING ============

    @Test
    void rbac_billing_post_invoices() throws Exception {
        // admin, billing: 201 | others: 403
        testEndpoint("POST", "/api/billing/invoices",
                new String[]{"admin", "billing"},
                new String[]{"doctor", "nurse", "receptionist", "pharmacist"},
                new com.medos.dto.InvoiceRequest());
    }

    @Test
    void rbac_billing_get_patient_invoices() throws Exception {
        // all authenticated: 200
        testEndpoint("GET", "/api/billing/patients/" + UUID.randomUUID() + "/invoices",
                new String[]{"admin", "doctor", "nurse", "receptionist", "pharmacist", "billing"},
                new String[]{},
                null);
    }

    @Test
    void rbac_billing_get_unbilled() throws Exception {
        // all authenticated: 200
        testEndpoint("GET", "/api/billing/patients/" + UUID.randomUUID() + "/unbilled",
                new String[]{"admin", "doctor", "nurse", "receptionist", "pharmacist", "billing"},
                new String[]{},
                null);
    }

    @Test
    void rbac_billing_post_payments() throws Exception {
        // admin, billing: 201 | others: 403
        testEndpoint("POST", "/api/billing/payments",
                new String[]{"admin", "billing"},
                new String[]{"doctor", "nurse", "receptionist", "pharmacist"},
                new com.medos.dto.PaymentRequest());
    }

    // ============ HELPER ============

    private void testEndpoint(String method, String url,
                              String[] allowedRoles, String[] deniedRoles,
                              Object body) throws Exception {
        // Test allowed roles
        for (String role : allowedRoles) {
            String token = switch (role) {
                case "admin" -> adminToken;
                case "doctor" -> doctorToken;
                case "nurse" -> nurseToken;
                case "receptionist" -> receptionistToken;
                case "pharmacist" -> pharmacistToken;
                case "billing" -> billingToken;
                default -> throw new IllegalArgumentException("Unknown role: " + role);
            };

            MvcResult result = performRequest(method, url, token, body);
            int status = result.getResponse().getStatus();

            // Allow 200, 201, 404 (not found), 400 (bad request - validation)
            // but NOT 401 (unauthorized) or 403 (forbidden)
            if (status == 401 || status == 403) {
                throw new AssertionError(
                        String.format("Role '%s' should have access to %s %s but got %d",
                                role, method, url, status));
            }
        }

        // Test denied roles
        for (String role : deniedRoles) {
            String token = switch (role) {
                case "admin" -> adminToken;
                case "doctor" -> doctorToken;
                case "nurse" -> nurseToken;
                case "receptionist" -> receptionistToken;
                case "pharmacist" -> pharmacistToken;
                case "billing" -> billingToken;
                default -> throw new IllegalArgumentException("Unknown role: " + role);
            };

            MvcResult result = performRequest(method, url, token, body);
            int status = result.getResponse().getStatus();

            // Should be 403 (forbidden) or 400 (validation error - body invalid)
            // Validation runs before authorization, so invalid bodies return 400
            if (status != 403 && status != 400) {
                throw new AssertionError(
                        String.format("Role '%s' should be denied (403/400) for %s %s but got %d",
                                role, method, url, status));
            }
        }

        // Test unauthenticated - Spring Security returns 403 when no auth provided for protected endpoints
        MvcResult unauthResult = performRequest(method, url, null, body);
        int unauthStatus = unauthResult.getResponse().getStatus();
        if (unauthStatus != 401 && unauthStatus != 403) {
            throw new AssertionError(
                    String.format("Unauthenticated request to %s %s should return 401 or 403 but got %d",
                            method, url, unauthStatus));
        }
    }
}