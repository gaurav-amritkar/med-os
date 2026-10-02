package com.medos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.medos.dto.CreateUserRequest;
import com.medos.dto.UserDTO;
import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.exception.BusinessException;
import com.medos.repository.TenantRepository;
import com.medos.repository.TenantUserRepository;
import com.medos.repository.UserRepository;
import com.medos.util.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * A tenant admin must be able to add the staff who work in their hospital — a doctor, a
 * nurse, a receptionist — so those people can sign in and reach the endpoints their role
 * allows.
 *
 * <p>Before this there was no way to create a user at all: no {@code UserController}, and
 * the only creator was onboarding, hardcoded to a single {@code admin}. A new hospital
 * could not staff itself.
 *
 * <p>Roles live on {@code tenant_users}, not on {@code users}, because one clinician may
 * work at two clinics and hold a different role at each. So "create a doctor" means adding
 * a membership in the acting tenant, and every read and write here is scoped to it.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceStaffManagementTest {

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantUserRepository tenantUserRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private UserService service;

    private CreateUserRequest request;

    @BeforeEach
    void setUp() {
        request = new CreateUserRequest();
        request.setUsername("dr.sharma");
        request.setFullName("Anita Sharma");
        request.setRole(TenantUser.UserRole.doctor);
        request.setPassword("Temp@12345");
        request.setEmail("anita.sharma@example.test");

        lenient().when(passwordEncoder.encode(anyString())).thenReturn("ENCODED_HASH");
        // Same reason: save() returns the managed row, and a bare mock returns null.
        lenient().when(tenantUserRepository.save(any(TenantUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // A JPA save returns the managed row, id included; a bare mock returns null, which
        // made every assertion about the created user fail for the wrong reason.
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }

    private Tenant tenant(UUID id) {
        Tenant t = new Tenant();
        t.setId(id);
        t.setName("Hospital " + id.toString().substring(0, 4));
        return t;
    }

    @Test
    @DisplayName("an admin can create a doctor in their own tenant")
    void adminCreatesDoctor() {
        when(userRepository.findByUsername("dr.sharma")).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_A)).thenReturn(Optional.of(tenant(TENANT_A)));
        when(tenantUserRepository.findByUserIdAndTenantId(any(), any())).thenReturn(Optional.empty());

        UserDTO created = service.createUser(TENANT_A, request);

        assertThat(created.getUsername()).isEqualTo("dr.sharma");
        assertThat(created.getFullName()).isEqualTo("Anita Sharma");
        assertThat(created.getRole()).isEqualTo("doctor");
        assertThat(created.getActive()).isTrue();
    }

    @Test
    @DisplayName("the initial password is hashed, never stored or returned in the clear")
    void passwordIsHashedAndNeverReturned() {
        when(userRepository.findByUsername("dr.sharma")).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_A)).thenReturn(Optional.of(tenant(TENANT_A)));
        when(tenantUserRepository.findByUserIdAndTenantId(any(), any())).thenReturn(Optional.empty());

        UserDTO created = service.createUser(TENANT_A, request);

        verify(passwordEncoder).encode("Temp@12345");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash())
                .isEqualTo("ENCODED_HASH")
                .isNotEqualTo("Temp@12345");
        assertThat(created.toString())
                .as("a user DTO must not be able to render a password hash")
                .doesNotContain("ENCODED_HASH");
    }

    @Test
    @DisplayName("a new user is told to change the temporary password at first sign-in")
    void newUserMustChangePassword() {
        when(userRepository.findByUsername("dr.sharma")).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_A)).thenReturn(Optional.of(tenant(TENANT_A)));
        when(tenantUserRepository.findByUserIdAndTenantId(any(), any())).thenReturn(Optional.empty());

        UserDTO created = service.createUser(TENANT_A, request);

        assertThat(created.getMustChangePassword())
                .as("an admin-set password is a shared secret until its owner changes it")
                .isTrue();
    }

    @Test
    @DisplayName("adding someone already in this organisation is rejected")
    void duplicateMembershipRejected() {
        User existing = User.builder().id(UUID.randomUUID()).username("dr.sharma")
                .fullName("Anita Sharma").build();
        when(userRepository.findByUsername("dr.sharma")).thenReturn(Optional.of(existing));
        when(tenantUserRepository.findByUserIdAndTenantId(existing.getId(), TENANT_A))
                .thenReturn(Optional.of(new TenantUser()));

        assertThatThrownBy(() -> service.createUser(TENANT_A, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already part of this organisation");
        verify(tenantUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("an email already held by another account is refused with a clear message")
    void duplicateEmailRejected() {
        // users.email is UNIQUE, so without this check the insert would fail at flush as an
        // opaque constraint violation rather than a message an admin can act on.
        User other = User.builder().id(UUID.randomUUID()).username("dr.other")
                .fullName("Someone Else").email("anita.sharma@example.test").build();
        // The email is checked first, so the username lookup is never reached.
        lenient().when(userRepository.findByUsername("dr.sharma")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("anita.sharma@example.test")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.createUser(TENANT_A, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("an existing account is given a membership instead of a second identity")
    void existingUserGetsMembershipOnly() {
        when(tenantRepository.findById(TENANT_A)).thenReturn(Optional.of(tenant(TENANT_A)));
        User existing = User.builder()
                .id(UUID.randomUUID()).username("dr.sharma").fullName("Anita Sharma").build();
        when(userRepository.findByUsername("dr.sharma")).thenReturn(Optional.of(existing));
        when(tenantUserRepository.findByUserIdAndTenantId(existing.getId(), TENANT_A))
                .thenReturn(Optional.empty());

        UserDTO created = service.createUser(TENANT_A, request);

        assertThat(created.getId()).isEqualTo(existing.getId());
        verify(userRepository, never()).save(any());
        verify(tenantUserRepository).save(any(TenantUser.class));
    }

    @Test
    @DisplayName("listing staff returns only the acting tenant's members")
    void listIsTenantScoped() {
        TenantUser membership = new TenantUser();
        membership.setUser(User.builder().id(UUID.randomUUID()).username("dr.sharma")
                .fullName("Anita Sharma").active(true).build());
        membership.setTenant(tenant(TENANT_A));
        membership.setRole(TenantUser.UserRole.doctor);
        when(tenantUserRepository.findByTenantId(TENANT_A)).thenReturn(List.of(membership));
        when(userRepository.findAllById(any())).thenReturn(List.of(membership.getUser()));

        List<UserDTO> staff = service.listUsers(TENANT_A);

        assertThat(staff).hasSize(1);
        assertThat(staff.get(0).getUsername()).isEqualTo("dr.sharma");
        verify(tenantUserRepository, never()).findByTenantId(TENANT_B);
    }

    @Test
    @DisplayName("an admin can change a member's role")
    void roleCanBeChanged() {
        TenantUser membership = membershipIn(TENANT_A, TenantUser.UserRole.receptionist);
        givenMembership(membership);

        service.updateRole(TENANT_A, membership.getUser().getId(), TenantUser.UserRole.doctor);

        ArgumentCaptor<TenantUser> saved = ArgumentCaptor.forClass(TenantUser.class);
        verify(tenantUserRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(TenantUser.UserRole.doctor);
    }

    @Test
    @DisplayName("a member from another tenant cannot be seen, so cannot be changed")
    void crossTenantMemberIsInvisible() {
        UUID outsider = UUID.randomUUID();
        when(tenantUserRepository.findByUserIdAndTenantId(outsider, TENANT_A))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRole(TENANT_A, outsider, TenantUser.UserRole.doctor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("deactivating a member blocks their sign-in for this tenant")
    void deactivateBlocksAccess() {
        TenantUser membership = membershipIn(TENANT_A, TenantUser.UserRole.doctor);
        givenMembership(membership);

        UserDTO result = service.setActive(TENANT_A, membership.getUser().getId(), false);

        assertThat(result.getActive()).isFalse();
        ArgumentCaptor<TenantUser> saved = ArgumentCaptor.forClass(TenantUser.class);
        verify(tenantUserRepository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isFalse();
    }

    @Test
    @DisplayName("deactivating one membership leaves the person's other clinic untouched")
    void deactivateIsPerTenantNotGlobal() {
        TenantUser membership = membershipIn(TENANT_A, TenantUser.UserRole.doctor);
        givenMembership(membership);

        service.setActive(TENANT_A, membership.getUser().getId(), false);

        verify(userRepository, never()).save(any());
        assertThat(membership.getUser().getActive())
                .as("the shared user row must stay active; only this hospital's access ends")
                .isTrue();
    }

    @Test
    @DisplayName("the last active admin cannot be deactivated, which would lock the tenant out")
    void lastAdminCannotBeDeactivated() {
        TenantUser lastAdmin = membershipIn(TENANT_A, TenantUser.UserRole.admin);
        givenMembership(lastAdmin);
        when(tenantUserRepository.findByTenantId(TENANT_A)).thenReturn(List.of(lastAdmin));

        assertThatThrownBy(() -> service.setActive(TENANT_A, lastAdmin.getUser().getId(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("last active admin");
    }

    @Test
    @DisplayName("the last active admin cannot be demoted, by the same reasoning")
    void lastAdminCannotBeDemoted() {
        TenantUser lastAdmin = membershipIn(TENANT_A, TenantUser.UserRole.admin);
        givenMembership(lastAdmin);
        when(tenantUserRepository.findByTenantId(TENANT_A)).thenReturn(List.of(lastAdmin));

        assertThatThrownBy(() ->
                service.updateRole(TENANT_A, lastAdmin.getUser().getId(), TenantUser.UserRole.doctor))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("last active admin");
    }

    @Test
    @DisplayName("an admin can be deactivated while another admin remains")
    void adminDeactivatableWhenAnotherRemains() {
        TenantUser target = membershipIn(TENANT_A, TenantUser.UserRole.admin);
        TenantUser other = membershipIn(TENANT_A, TenantUser.UserRole.admin);
        givenMembership(target);
        when(tenantUserRepository.findByTenantId(TENANT_A)).thenReturn(List.of(target, other));

        UserDTO result = service.setActive(TENANT_A, target.getUser().getId(), false);

        assertThat(result.getActive()).isFalse();
    }

    private void givenMembership(TenantUser membership) {
        when(tenantUserRepository.findByUserIdAndTenantId(
                membership.getUser().getId(), membership.getTenant().getId()))
                .thenReturn(Optional.of(membership));
    }

    private TenantUser membershipIn(UUID tenantId, TenantUser.UserRole role) {
        TenantUser membership = new TenantUser();
        membership.setUser(User.builder().id(UUID.randomUUID()).username("u" + role)
                .fullName("User " + role).active(true).build());
        membership.setTenant(tenant(tenantId));
        membership.setRole(role);
        return membership;
    }

    @Test
    @DisplayName("the last-admin refusal reads as a sentence an admin can act on")
    void lastAdminMessageIsWellFormed() {
        TenantUser lastAdmin = membershipIn(TENANT_A, TenantUser.UserRole.admin);
        givenMembership(lastAdmin);
        when(tenantUserRepository.findByTenantId(TENANT_A)).thenReturn(List.of(lastAdmin));

        // Caught by reading live output, not by a test: the message was assembled from a
        // past participle and read "Cannot deactivated the last active admin".
        assertThatThrownBy(() -> service.setActive(TENANT_A, lastAdmin.getUser().getId(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot deactivate the last active admin")
                .hasMessageNotContaining("Cannot deactivated");
    }
}
