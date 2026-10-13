package com.medos.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An onboarded organisation, as the platform sees it.
 *
 * <p>Deliberately not the {@code Tenant} entity. {@code Tenant.config} is a
 * free-form map used for integration settings, and a listing endpoint is exactly
 * where a secret ends up being returned: it is read by whoever loads the page,
 * cached by the browser, and written to any client-side error report. The
 * fields here are the ones an administrator needs to identify an organisation
 * and nothing else.
 *
 * <p>{@code active} is included rather than filtered on, so a deactivated
 * organisation stays visible. An administrator asking "why can this person not
 * sign in" needs to see the row that says why.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDTO {

    private UUID id;
    private String name;
    /** URL slug; unique across organisations, so it identifies one unambiguously. */
    private String slug;
    /** {@code HOSPITAL}, {@code CLINIC}, {@code INDIVIDUAL_PRACTITIONER} or {@code PHARMACY}. */
    private String type;
    private String contactEmail;
    private String contactPhone;
    private String address;
    /** {@code null} counts as inactive — the column is a nullable BOOLEAN. */
    private Boolean active;
}
