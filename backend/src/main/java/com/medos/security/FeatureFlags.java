package com.medos.security;

import com.medos.exception.BusinessException;
import com.medos.service.TenantService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side enforcement of the per-tenant feature set chosen at onboarding
 * (#127). The tenant's config carries {@code features} as a comma-separated allowlist;
 * a module not listed must 403, not merely be hidden in the UI. Nothing stored in
 * config means every module is enabled — a tenant that never set the key is not locked
 * out of a feature it could not have discovered.
 */
@Component
public class FeatureFlags {

    public static final String CONFIG_KEY = "features";

    private static final Set<String> KNOWN = Set.of("admissions", "billing", "encounters", "pharmacy");

    private final TenantService tenantService;

    @Autowired
    public FeatureFlags(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /** Parse a features CSV into a list. Unknown and blank values are dropped. */
    public static List<String> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public boolean isEnabled(java.util.UUID tenantId, String feature) {
        String csv = tenantService.getConfig(tenantId, CONFIG_KEY);
        if (csv == null || csv.isBlank()) {
            return true;
        }
        return parse(csv).contains(feature);
    }

    public void require(UUID tenantId, String feature) {
        if (!isEnabled(tenantId, feature)) {
            throw new com.medos.exception.BusinessException(HttpStatus.FORBIDDEN,
                    "This hospital does not have the '" + feature + "' module enabled");
        }
    }

    public static void validateRequested(List<String> requested) {
        Set<String> bad = new java.util.LinkedHashSet<>();
        for (String f : requested) {
            if (!KNOWN.contains(f)) {
                bad.add(f);
            }
        }
        if (!bad.isEmpty()) {
            throw new com.medos.exception.BusinessException(HttpStatus.BAD_REQUEST,
                    "Unknown feature(s): " + bad + ". Allowed: admissions, billing, encounters, pharmacy");
        }
    }
}
