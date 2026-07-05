package com.medos.security;

import com.medos.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class CurrentUserProvider {

    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        try {
            return UUID.fromString(auth.getName());
        } catch (Exception e) {
            return null;
        }
    }

    public String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    public boolean hasRole(User.Role... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        List<String> userAuthorities = auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", "")).toList();
        return Arrays.stream(roles).anyMatch(r -> userAuthorities.contains(r.name()));
    }
}
