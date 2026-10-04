# Design Spec: TenantKeyHolder Depth Enhancement

## Status
Proposed

## Context
`TenantKeyHolder` is a 491-line God object mixing 6 responsibilities. 14 production + 5 test files depend on it statically. No interface means callers cannot depend on a stable contract. The deletion test fails: removing TenantKeyHolder requires editing 19 files.

ADR-0001 already deepened `key_rewrap_seam` at `TenantKeyStore`. The natural next level is `tenant_key_resolution_seam`.

## Decision
Extract `TenantKeyResolver` interface + `BlindIndexKeyProvider` interface. Make `TenantKeyHolder` implement both. Extract `InMemoryTenantKeyStore` from inner class. Add `TenantKeyResolverFactory` as static bridge for JPA converter constraint.

## Interface: TenantKeyResolver
```java
package com.medos.security;

import java.util.UUID;

public interface TenantKeyResolver {
    byte[] resolveDek(UUID tenantId);
    byte[] findDekOrNull(UUID tenantId);
    int generationFor(UUID tenantId);
    byte[] findBlindIndexKeyOrNull(UUID tenantId);
    void ensureDekExists();
    void ensureBlindIndexKeyExists();
    int currentGeneration();
    int rewrapAll();
    TenantKeyResolver withPreviousKek(String previousKekBase64);
    void clearCache();
}
```

## Interface: BlindIndexKeyProvider
```java
package com.medos.security;

import java.util.UUID;

public interface BlindIndexKeyProvider {
    byte[] findOrNull(UUID tenantId);
    void ensureExists(UUID tenantId);
    void rotateIndependently(UUID tenantId);
    int rewrapAll();
    void clearCache();
}
```

## Factory
```java
package com.medos.security;

public final class TenantKeyResolverFactory {
    private static volatile TenantKeyResolver resolver;

    private TenantKeyResolverFactory() {}

    public static TenantKeyResolver getResolver() {
        TenantKeyResolver r = resolver;
        if (r == null) throw new IllegalStateException("TenantKeyResolver not initialized");
        return r;
    }

    public static void setResolver(TenantKeyResolver resolver) {
        TenantKeyResolverFactory.resolver = resolver;
    }
}
```

## Adapters
- **TenantKeyHolder** (production) — implements `TenantKeyResolver`, composes `BlindIndexKeyProvider` (extracted `BlindIndexKeyService` now implementing interface)
- **InMemoryTenantKeyStore** (test) — top-level class implementing `TenantKeyResolver`, replaces `InMemoryTenantKeyHolder` inner class

## Consequences
- All 14 production callers get `TenantKeyResolver` via factory or constructor injection
- `EncryptionUtil`, `BlindIndexUtil`, `EncryptionConfig` continue using factory (JPA constraint)
- `PatientService`, `PatientOnboardingService`, `EncounterService` inject via constructor
- Tests replace `TenantKeyHolder.inMemoryForTesting()` with `new InMemoryTenantKeyStore()` + `TenantKeyResolverFactory.setResolver()`
- `BlindIndexKeyService` implements `BlindIndexKeyProvider`, package-private stays or opens as needed
- GLOSSARY.md updated with `tenant_key_resolution_seam` and `key_lifecycle_seam`
