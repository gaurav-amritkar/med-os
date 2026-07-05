package com.medos.controller;

import com.medos.entity.Notification;
import com.medos.entity.User;
import com.medos.repository.NotificationRepository;
import com.medos.repository.UserRepository;
import com.medos.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

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
    public ResponseEntity<User> getCurrentUser(Authentication auth) {
        return ResponseEntity.ok(resolveUser(auth));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> listUsers(@RequestParam(required = false) User.Role role) {
        if (role != null) {
            return ResponseEntity.ok(userRepository.findByRole(role));
        }
        return ResponseEntity.ok(userRepository.findByActiveTrue());
    }
}
