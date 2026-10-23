package com.medos.controller;

import com.medos.entity.Notification;
import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.repository.NotificationRepository;
import com.medos.repository.TenantRepository;
import com.medos.repository.TenantUserRepository;
import com.medos.repository.UserRepository;
import com.medos.dto.CurrentUserDTO;
import com.medos.dto.UserDTO;
import com.medos.service.DashboardService;
import com.medos.service.TenantService;
import com.medos.service.TenantService;
import com.medos.service.UserService;
import com.medos.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final NotificationRepository notificationRepository;
    private final TenantRepository tenantRepository;
    private final TenantService tenantService;
    private final TenantUserRepository tenantUserRepository;

    private User resolveUser(Authentication auth) {
        try {
            UUID uid = UUID.fromString(auth.getName());
            return userRepository.findById(uid).orElse(null);
        } catch (Exception e) {
            return userRepository.findByUsername(auth.getName()).orElse(null);
        }
    }

    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getDashboard(Authentication auth) {
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("ADMIN");
        return ResponseEntity.ok(dashboardService.getDashboardByRole(role));
    }

    @GetMapping("/notifications")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Notification>> getNotifications(
            Authentication auth,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        User user = resolveUser(auth);
        if (user == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(dashboardService.getNotifications(user.getId(), unreadOnly));
    }

    @GetMapping("/notifications/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication auth) {
        User user = resolveUser(auth);
        if (user == null) return ResponseEntity.ok(Map.of("count", 0L));
        return ResponseEntity.ok(Map.of("count", dashboardService.getUnreadCount(user.getId())));
    }

    @PutMapping("/notifications/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, Authentication auth) {
        User user = resolveUser(auth);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Scoped by recipient. `notifications` is not in the statement inspector's
        // tenant table set, so nothing else narrows this row: findById(id) let any
        // signed-in account mutate any notification by iterating ids. The old
        // ifPresent also answered 200 for a miss, which made the endpoint an
        // existence oracle, so a non-owned id now reads as 404.
        //
        // Deliberately not written as an Optional.map chain through save(): that
        // collapses to empty when save() yields null, which turns a completed
        // write into a 404 and hides it.
        var owned = notificationRepository.findByIdAndRecipientId(id, user.getId());
        if (owned.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Notification notification = owned.get();
        notification.setRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CurrentUserDTO> getCurrentUser(Authentication auth) {
        User user = resolveUser(auth);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        // The acting tenant comes from TenantContext, which JwtAuthenticationFilter
        // set from the token's tenantId claim, and the role from the authority it
        // granted. Both therefore describe the session this request is actually
        // running in. Re-deriving them here could disagree with that session.
        UUID tenantId = TenantContext.getTenantId().orElse(null);
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
          java.util.List<String> features = java.util.List.of();
          if (tenantId != null) {
              String csv = tenantService.getConfig(tenantId, "features");
              if (csv != null && !csv.isBlank()) {
                  features = java.util.Arrays.stream(csv.split(","))
                          .map(String::trim).filter(f -> !f.isEmpty()).toList();
              }
          }
          String permissions = null;
          if (tenantId != null) {
              var membership = tenantUserRepository.findByUserIdAndTenantId(user.getId(), tenantId).orElse(null);
              if (membership != null) {
                  permissions = membership.getPermissions();
              }
          }
          return ResponseEntity.ok(CurrentUserDTO.of(
                  user,
                  tenantId,
                  tenantId == null ? null : tenantName(tenantId),
                  role == null ? null : TenantUser.UserRole.valueOf(role.toLowerCase()),
                  features,
                  permissions));
      }

    private String tenantName(UUID tenantId) {
        return tenantRepository.findById(tenantId).map(Tenant::getName).orElse(null);
    }
}
