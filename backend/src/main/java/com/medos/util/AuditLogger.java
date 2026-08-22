package com.medos.util;

import com.medos.entity.AuditLog;
import com.medos.repository.AuditLogRepository;
import com.medos.repository.UserRepository;
import com.medos.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public void log(String action, String entityType, String entityId, String oldValue, String newValue) {
        UUID userId = currentUserProvider.getCurrentUserId();
        
        // Check if the user exists in the database to avoid FK constraint violation
        // If user doesn't exist (e.g., token from old DB state), save with null user_id
        if (userId != null && !userRepository.existsById(userId)) {
            userId = null;
        }
        
        AuditLog log = AuditLog.builder()
                .userId(userId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId == null ? null : parseUuid(entityId))
                .oldValue(oldValue)
                .newValue(newValue)
                .ipAddress(getClientIp())
                .userAgent(getUserAgent())
                .build();
        auditLogRepository.save(log);
    }

    public void log(String action, String entityType, String entityId) {
        log(action, entityType, entityId, null, null);
    }

    private UUID parseUuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    private String getClientIp() {
        try {
            HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            return xff != null ? xff.split(",")[0].trim() : req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private String getUserAgent() {
        try {
            HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            return req.getHeader("User-Agent");
        } catch (Exception e) {
            return null;
        }
    }
}
