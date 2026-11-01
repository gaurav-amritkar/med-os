package com.medos.service;

import com.medos.dto.CreateLabOrderRequest;
import com.medos.dto.LabOrderDTO;
import com.medos.dto.RecordLabResultRequest;
import com.medos.dto.UpdateLabOrderStatusRequest;
import com.medos.entity.Encounter;
import com.medos.entity.LabOrder;
import com.medos.entity.Patient;
import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.exception.BusinessException;
import com.medos.repository.EncounterRepository;
import com.medos.repository.LabOrderRepository;
import com.medos.repository.PatientRepository;
import com.medos.repository.TenantRepository;
import com.medos.repository.TenantUserRepository;
import com.medos.modules.clinical.service.LabOrderService;
import com.medos.repository.UserRepository;
import com.medos.security.CurrentUserProvider;
import com.medos.security.TenantContext;
import com.medos.util.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lab order service contract: state machine, signed-encounter refusal,
 * tenant isolation, and result recording.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LabOrderServiceContractTest {

    @Autowired
    private LabOrderRepository labOrderRepository;

    @Autowired
    private EncounterRepository encounterRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantUserRepository tenantUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private AuditLogger auditLogger;

    private UUID tenantId;
    private UUID doctorId;
    private UUID patientId;
    private UUID encounterId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        Tenant tenant = Tenant.builder()
                .name("Lab Test Tenant")
                .type(Tenant.TenantType.HOSPITAL)
                .slug("lab-test-" + UUID.randomUUID().toString().substring(0, 8))
                .active(true)
                .build();
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();

        User doctor = User.builder()
                .username("labdoctor")
                .passwordHash(passwordEncoder.encode("password"))
                .fullName("Lab Doctor")
                .email("labdoctor@test.com")
                .active(true)
                .build();
        doctor = userRepository.save(doctor);
        doctorId = doctor.getId();

        tenantUserRepository.save(TenantUser.builder()
                .user(doctor)
                .tenant(tenant)
                .role(TenantUser.UserRole.doctor)
                .build());

        Patient patient = new Patient();
        patient.setUhid("LAB-UHID-" + UUID.randomUUID().toString().substring(0, 8));
        patient.setTenantId(tenantId);
        patient.setName("Lab Patient");
        patient.setDpdpConsent(true);
        patient.setDpdpConsentAt(LocalDateTime.now());
        patient.setCreatedAt(LocalDateTime.now());
        patient.setUpdatedAt(LocalDateTime.now());
        patient = patientRepository.save(patient);
        patientId = patient.getId();

        Encounter encounter = new Encounter();
        encounter.setTenantId(tenantId);
        encounter.setPatientId(patientId);
        encounter.setDoctorId(doctorId);
        encounter.setStatus(Encounter.Status.open);
        encounter.setChiefComplaint("fever");
        encounter.setDiagnosis("viral");
        encounter = encounterRepository.save(encounter);
        encounterId = encounter.getId();

        TenantContext.setTenantId(tenantId);

        // Set up the security context so CurrentUserProvider resolves the doctor.
        var auth = new UsernamePasswordAuthenticationToken(
                doctorId.toString(), null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_DOCTOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private LabOrderService service() {
        return new LabOrderService(
                labOrderRepository,
                encounterRepository,
                currentUserProvider,
                auditLogger);
    }

    @Test
    @DisplayName("a doctor can create a lab order from an open encounter")
    void doctorCanCreateLabOrder() {
        CreateLabOrderRequest request = new CreateLabOrderRequest(
                encounterId, patientId, "CBC", "CBC-001", "normal");

        LabOrderDTO dto = service().createLabOrder(request);

        assertThat(dto.id()).isNotNull();
        assertThat(dto.testName()).isEqualTo("CBC");
        assertThat(dto.status()).isEqualTo("ordered");
        assertThat(dto.priority()).isEqualTo("normal");
        assertThat(dto.doctorId()).isEqualTo(doctorId);
        assertThat(dto.encounterId()).isEqualTo(encounterId);
        assertThat(dto.orderedAt()).isNotNull();
    }

    @Test
    @DisplayName("ordering on a signed encounter is refused")
    void signedEncounterRefused() {
        // Sign the encounter first
        Encounter encounter = encounterRepository.findById(encounterId).orElseThrow();
        encounter.setStatus(Encounter.Status.signed);
        encounter.setSignedAt(LocalDateTime.now());
        encounter.setSignedBy(doctorId);
        encounterRepository.save(encounter);

        CreateLabOrderRequest request = new CreateLabOrderRequest(
                encounterId, patientId, "CBC", null, "normal");

        assertThatThrownBy(() -> service().createLabOrder(request))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getStatus() == HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("state machine: ordered -> collected -> in_progress -> completed")
    void stateMachineTransitions() {
        LabOrder order = LabOrder.builder()
                .tenantId(tenantId)
                .encounterId(encounterId)
                .patientId(patientId)
                .doctorId(doctorId)
                .testName("CBC")
                .status(LabOrder.Status.ordered)
                .build();
        order = labOrderRepository.save(order);

        LabOrderDTO collected = service().updateStatus(order.getId(),
                new UpdateLabOrderStatusRequest("collected"));
        assertThat(collected.status()).isEqualTo("collected");

        LabOrderDTO inProgress = service().updateStatus(collected.id(),
                new UpdateLabOrderStatusRequest("in_progress"));
        assertThat(inProgress.status()).isEqualTo("in_progress");

        LabOrderDTO completed = service().updateStatus(inProgress.id(),
                new UpdateLabOrderStatusRequest("completed"));
        assertThat(completed.status()).isEqualTo("completed");
    }

    @Test
    @DisplayName("invalid transitions are rejected")
    void invalidTransitionsRejected() {
        LabOrder order = LabOrder.builder()
                .tenantId(tenantId)
                .encounterId(encounterId)
                .patientId(patientId)
                .doctorId(doctorId)
                .testName("CBC")
                .status(LabOrder.Status.ordered)
                .build();
        order = labOrderRepository.save(order);
        UUID orderId = order.getId();

        // ordered -> completed is not allowed
        assertThatThrownBy(() -> service().updateStatus(orderId,
                new UpdateLabOrderStatusRequest("completed")))
                .isInstanceOf(BusinessException.class);

        // collected first, then ordered -> completed is not allowed from collected
        LabOrderDTO collected = service().updateStatus(orderId,
                new UpdateLabOrderStatusRequest("collected"));
        assertThat(collected.status()).isEqualTo("collected");
        assertThatThrownBy(() -> service().updateStatus(collected.id(),
                new UpdateLabOrderStatusRequest("ordered")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("result can only be recorded when collected or in_progress")
    void resultRequiresCollectedOrInProgress() {
        LabOrder order = LabOrder.builder()
                .tenantId(tenantId)
                .encounterId(encounterId)
                .patientId(patientId)
                .doctorId(doctorId)
                .testName("CBC")
                .status(LabOrder.Status.ordered)
                .build();
        order = labOrderRepository.save(order);
        UUID orderId = order.getId();

        assertThatThrownBy(() -> service().recordResult(orderId,
                new RecordLabResultRequest("negative")))
                .isInstanceOf(BusinessException.class);

        // Move to collected, then record
        service().updateStatus(orderId, new UpdateLabOrderStatusRequest("collected"));
        LabOrderDTO withResult = service().recordResult(orderId,
                new RecordLabResultRequest("negative"));
        assertThat(withResult.result()).isEqualTo("negative");
        assertThat(withResult.resultAt()).isNotNull();
    }

    @Test
    @DisplayName("result on signed encounter is refused")
    void resultOnSignedEncounterRefused() {
        LabOrder order = LabOrder.builder()
                .tenantId(tenantId)
                .encounterId(encounterId)
                .patientId(patientId)
                .doctorId(doctorId)
                .testName("CBC")
                .status(LabOrder.Status.collected)
                .build();
        order = labOrderRepository.save(order);
        UUID orderId = order.getId();

        // Sign the encounter
        Encounter encounter = encounterRepository.findById(encounterId).orElseThrow();
        encounter.setStatus(Encounter.Status.signed);
        encounter.setSignedAt(LocalDateTime.now());
        encounter.setSignedBy(doctorId);
        encounterRepository.save(encounter);

        assertThatThrownBy(() -> service().recordResult(orderId,
                new RecordLabResultRequest("negative")))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getStatus() == HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("tenant isolation: order from another tenant is invisible")
    void tenantIsolation() {
        // Create order for this tenant
        LabOrder order = LabOrder.builder()
                .tenantId(tenantId)
                .encounterId(encounterId)
                .patientId(patientId)
                .doctorId(doctorId)
                .testName("CBC")
                .status(LabOrder.Status.ordered)
                .build();
        labOrderRepository.save(order);

        // Switch tenant context
        TenantContext.clear();
        TenantContext.setTenantId(UUID.randomUUID());

        // The order should not be visible (TenantStatementInspector scopes the query)
        assertThat(service().listLabOrders(patientId, null, null)).isEmpty();

        TenantContext.clear();
        TenantContext.setTenantId(tenantId);
    }
}
