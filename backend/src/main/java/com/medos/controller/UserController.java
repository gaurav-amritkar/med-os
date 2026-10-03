package com.medos.controller;

import java.util.List;
import java.util.UUID;

import com.medos.dto.CreateUserRequest;
import com.medos.dto.UserDTO;
import com.medos.entity.TenantUser;
import com.medos.security.TenantContext;
import com.medos.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff management for the acting tenant.
 *
 * <p>Until this existed a hospital could create exactly one user: the admin made at
 * onboarding. There was no way to add the doctor, nurse or receptionist that hospital
 * needed, so nobody else could sign in at all.
 *
 * <p>ADMIN-only on every method. The failure mode worth guarding is not that this is
 * missing, but that any authenticated user could mint themselves an admin.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    /**
     * Staff of the acting tenant, including anyone deactivated.
     *
     * <p>Separate from the dashboard's {@code GET /api/v1/users/me}-adjacent roster: an
     * administration page has to show who has left, or it cannot show a way to bring them
     * back.
     */
    @GetMapping
    public ResponseEntity<List<UserDTO>> listUsers() {
        return ResponseEntity.ok(userService.listUsers(actingTenant()));
    }

    @PostMapping
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUser(actingTenant(), request));
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<UserDTO> updateRole(@PathVariable UUID userId,
                                              @RequestBody RoleRequest request) {
        return ResponseEntity.ok(userService.updateRole(actingTenant(), userId, request.role()));
    }

    @PutMapping("/{userId}/active")
    public ResponseEntity<UserDTO> setActive(@PathVariable UUID userId,
                                             @RequestBody ActiveRequest request) {
        return ResponseEntity.ok(
                userService.setActive(actingTenant(), userId, request.active()));
    }

    /**
     * The tenant this request acts for.
     *
     * <p>Every query is scoped to it rather than trusting a tenant id in the path, so a
     * caller cannot address another hospital's roster by editing a URL.
     */
    private UUID actingTenant() {
        return TenantContext.getTenantId()
                .orElseThrow(() -> new com.medos.exception.BusinessException(
                        "No organisation is selected for this session"));
    }

    public record RoleRequest(TenantUser.UserRole role) {
    }

    public record ActiveRequest(boolean active) {
    }
}
