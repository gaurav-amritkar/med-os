package com.medos.controller;

import java.util.List;
import java.util.UUID;

import com.medos.dto.TenantDTO;
import com.medos.entity.Tenant;
import com.medos.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-level tenant administration.
 *
 * <p>{@code TenantService.getAllTenants()} has existed with no caller: the
 * capability was written as a service and never exposed, so a super-admin could
 * create organisations one at a time with no record of what already existed.
 * The first sign of a duplicate was a 409 from the slug constraint.
 *
 * <p>SUPER_ADMIN only, and not merely because a hospital admin should not see
 * other hospitals. A platform account holds no tenant membership, so the
 * statement inspector binds no {@code TenantContext} and every row is visible:
 * this response is a cross-tenant read by construction, and the only thing
 * standing between it and every hospital's roster is this annotation.
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantController {

    private final TenantService tenantService;

    /**
     * Every onboarded organisation, including deactivated ones.
     *
     * <p>Deactivated rows stay in the list. Hiding them would make the most
     * common support question — why can this person not sign in — unanswerable
     * from the interface.
     */
    @GetMapping
    public ResponseEntity<List<TenantDTO>> listTenants() {
        List<TenantDTO> tenants = tenantService.getAllTenants().stream()
                .map(TenantController::toDto)
                .toList();
        return ResponseEntity.ok(tenants);
    }

    /**
     * Patch tenant attributes (name, type, contact email/phone, address).
     *
     * <p>Requires {@code SUPER_ADMIN} — the same guard that protects the list.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TenantDTO> updateTenant(@PathVariable UUID id, @RequestBody TenantDTO dto) {
        TenantDTO updated = tenantService.updateTenant(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * Deactivate a tenant: set {@code active} to false.
     */
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deactivateTenant(@PathVariable UUID id) {
        tenantService.deactivateTenant(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Re-activate a previously deactivated tenant.
     */
    @PostMapping("/{id}/enable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> activateTenant(@PathVariable UUID id) {
        tenantService.activateTenant(id);
        return ResponseEntity.noContent().build();
    }

    private static TenantDTO toDto(Tenant t) {
        return TenantDTO.builder()
                .id(t.getId())
                .name(t.getName())
                .slug(t.getSlug())
                .type(t.getType().name())
                .contactEmail(t.getContactEmail())
                .contactPhone(t.getContactPhone())
                .address(t.getAddress())
                .active(t.getActive())
                .build();
    }
}
