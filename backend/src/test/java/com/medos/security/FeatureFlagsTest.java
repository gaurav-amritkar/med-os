package com.medos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.medos.exception.BusinessException;
import com.medos.service.TenantService;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Hiding a module in the navigation is not the same as refusing to serve it. These
 * tests pin the server-side half: a module the hospital did not buy must 403 at the
 * API boundary even when a client asks for it directly.
 */
@ExtendWith(MockitoExtension.class)
class FeatureFlagsTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock
    private TenantService tenantService;

    private FeatureFlags featureFlags;

    @BeforeEach
    void setUp() {
        featureFlags = new FeatureFlags(tenantService);
    }

    @Test
    @DisplayName("a module in the tenant's bundle is enabled")
    void enabledFeature() {
        when(tenantService.getConfig(TENANT, "features")).thenReturn("billing,pharmacy");

        assertThat(featureFlags.isEnabled(TENANT, "billing")).isTrue();
    }

    @Test
    @DisplayName("a module left out of the bundle is refused")
    void disabledFeatureRequires() {
        when(tenantService.getConfig(TENANT, "features")).thenReturn("billing,pharmacy");

        assertThatThrownBy(() -> featureFlags.require(TENANT, "admissions"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("admissions")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a tenant that never chose a bundle keeps every module")
    void absentBundleMeansEverythingOn() {
        when(tenantService.getConfig(TENANT, "features")).thenReturn(null);

        assertThat(featureFlags.isEnabled(TENANT, "admissions")).isTrue();
        assertThat(featureFlags.isEnabled(TENANT, "billing")).isTrue();
        assertThat(featureFlags.isEnabled(TENANT, "encounters")).isTrue();
        assertThat(featureFlags.isEnabled(TENANT, "pharmacy")).isTrue();
    }

    @Test
    @DisplayName("whitespace in a hand-edited bundle is tolerated")
    void parseTrimsBlanks() {
        assertThat(FeatureFlags.parse(" billing , ,pharmacy ")).containsExactly("billing", "pharmacy");
        assertThat(FeatureFlags.parse(null)).isEmpty();
        assertThat(FeatureFlags.parse("  ")).isEmpty();
    }

    @Test
    @DisplayName("an unknown module is rejected before a tenant is created")
    void unknownFeatureRejected() {
        assertThatThrownBy(() -> FeatureFlags.validateRequested(List.of("billing", "telepathy")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("telepathy")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("a known bundle validates")
    void knownFeaturesAccepted() {
        FeatureFlags.validateRequested(List.of("admissions", "billing", "encounters", "pharmacy"));
    }
}