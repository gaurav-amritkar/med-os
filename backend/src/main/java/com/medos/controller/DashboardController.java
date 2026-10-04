package com.medos.controller;

import com.medos.entity.Notification;
import com.medos.entity.Tenant;
import com.medos.entity.TenantUser;
import com.medos.entity.User;
import com.medos.repository.NotificationRepository;
import com.medos.repository.TenantRepository;
import com.medos.repository.UserRepository;
import com.medos.dto.CurrentUserDTO;
import com.medos.dto.UserDTO;
import com.medos.service.DashboardService;
import com.medos.service.TenantService;
import com.medos.service.TenantService;
import com.medos.service.UserService;
import com.medos.security.TenantContext;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
        return ResponseEntity.ok().build();
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
          return ResponseEntity.ok(CurrentUserDTO.of(
                  user,
                  tenantId,
                  tenantId == null ? null : tenantName(tenantId),
                  role == null ? null : TenantUser.UserRole.valueOf(role.toLowerCase()),
                  features));
      }

    private String tenantName(UUID tenantId) {
        return tenantRepository.findById(tenantId).map(Tenant::getName).orElse(null);
    }
}
