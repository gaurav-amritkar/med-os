# Key Lifecycle Orchestration Deepening

## Summary
The current key rotation logic suffers from duplication and tight coupling. The re-wrap algorithm is copied in three places (`KeyRewrapService`, `TenantKeyHolder`, `BlindIndexKeyService`), and `KeyRewrapService` directly writes audit rows (`KeyRotation`) while also performing the re-wrap, violating separation of concerns. The `KeyRotation` entity already anticipates both `kek_rewrap` and `dek_rotation` operations, but only `kek_rewrap` is implemented. This deepening consolidates the key lifecycle into a single module that owns both the re-wrap algorithm and audit recording, providing a clean `plan`/`rotate` interface with audit as a side effect.

## Files involved
- `backend/src/main/java/com/medos/security/KeyRewrapService.java`
- `backend/src/main/java/com/medos/entity/KeyRotation.java`
- `backend/src/main/java/com/medos/repository/KeyRotationRepository.java`
- `backend/src/main/java/com/medos/security/TenantKeyStore.java`
- `backend/src/main/java/com/medos/security/TenantKeyHolder.java` (rewrapAll)
- `backend/src/main/java/com/medos/security/BlindIndexKeyService.java` (rewrapAll)
- Test: `backend/src/test/java/com/medos/tools/PiiKeyLifecycleMigrationTest.java`

## Problem (friction evidence with file:line)
1. **Duplicated re-wrap algorithm**: The unwrap/rotate/wrap logic appears in three locations:
   - `KeyRewrapService.rotate()` lines 88-101 (dek and bi rewrapping)
   - `TenantKeyHolder.rewrapAll()` lines 382-404 (dek rewrapping + blind index delegation)
   - `BlindIndexKeyService.rewrapAll()` lines 133-151 (bi rewrapping)
   This violates the DRY principle and increases maintenance burden.

2. **TOCTOU-ish double fetch in KeyRewrapService**:
   - `plan()` lines 47-59: fetches `all = keyStore.tenantIdsWithKeys()` and `pending = keyStore.findTenantsPendingKekVersion(targetKekVersion)`
   - `rotate()` line 67: calls `plan(oldKek, targetKekVersion)` (repeating the same fetches)
   - `rotate()` line 88: calls `keyStore.findTenantsPendingKekVersion(targetKekVersion)` again
   This creates a window where the pending set can change between the plan and rotate phases.

3. **Mixed responsibilities in KeyRewrapService**:
   - Lines 68-74: creates and saves a `KeyRotation` audit row
   - Lines 87-102: performs the actual re-wrap
   - Lines 103-109: updates and saves the audit row
   The service couples audit recording with the re-wrap execution, making it impossible to re-wrap without recording or to record a dry-run.

4. **Missing dek_rotation implementation**:
   - `KeyRotation` entity lines 20-23: javadoc explicitly states `dek_rotation` re-encrypts one tenant's ciphertext (not reversible)
   - No implementation exists for this operation; only `kek_rewrap` is handled.

## Proposed deepened module (interface sketch in Java, seam placement, adapters)
We propose a new module `KeyLifecycleService` that owns both `kek_rewrap` and future `dek_rotation` operations, with audit recording as an internal side effect. The seam is placed at `TenantKeyStore` (already introduced by ADR-0001) with two adapters: the real JPA-backed adapter and an in-memory test fake.

### Interface sketch
```java
package com.medos.security;

/**
 * Orchestrates key lifecycle operations (kek_rewrap, dek_rotation) with built-in audit.
 * All operations are fail-closed and idempotent where applicable.
 */
public interface KeyLifecycleService {

    /** Dry-run report for a kek_rewrap. */
    record KekRewrapPlan(int totalKeyRows, int toRewrap, int alreadyCurrent, List<UUID> cannotUnwrap) {
        boolean safeToProceed() { return cannotUnwrap.isEmpty(); }
    }

    /** Outcome of a kek_rewrap. */
    record KekRewrapResult(int rowsRewritten, Status status, String detail) {}
    enum KekRewrapStatus { COMPLETED, ABORTED }

    /** Dry-run report for a dek_rotation (tenant-scoped). */
    record DekRotationPlan(int totalRows, int toRotate, int alreadyCurrent, List<UUID> cannotDecrypt) {
        boolean safeToProceed() { return cannotDecrypt.isEmpty(); }
    }

    /** Outcome of a dek_rotation. */
    record DekRotationResult(int rowsRewritten, Status status, String detail) {}
    enum DekRotationStatus { COMPLETED, ABORTED }

    KekRewrapPlan planKekRewrap(byte[] oldKek, int targetKekVersion);
    KekRewrapResult rotateKekWrap(byte[] oldKek, int targetKekVersion, byte[] newKek, String initiatedBy);

    DekRotationPlan planDekRotation(UUID tenantId, int currentDekGeneration, int targetDekGeneration);
    DekRotationResult rotateDekWrap(UUID tenantId, int currentDekGeneration, int targetDekGeneration, String initiatedBy);
}
```

### Seam placement and adapters
- **Seam**: `key_lifecycle_seam` (the boundary between orchestration and key-material access)
- **Adapter 1 (real)**: `JpaTenantKeyStore` (the existing `TenantKeyStore` interface, implemented by Spring Data JPA via custom repository methods or a new `KeyLifecycleRepository`)
- **Adapter 2 (test)**: `InMemoryTenantKeyStore` (already implied by existing test doubles for `TenantKeyStore`)

The `KeyLifecycleService` implementation will depend on `TenantKeyStore` (the adapter) and `KeyRotationRepository` (for audit). It will contain the re-wrap algorithm once, delegating key-material access to the adapter.

### Before-after comparison
**Before**: Callers (none currently, but future) would assemble audit rows manually and call three separate methods for dek/bi rewrapping.
**After**: Callers invoke `planKekRewrap`/`rotateKekWrap` (or the dek counterparts) and receive a plan/result that already includes audit side effects. The re-wrap algorithm is centralized.

## Test impact
- **Surviving tests**:
  - `PiiKeyLifecycleMigrationTest` (database migration validation) – unchanged, as it only validates schema.
  - `BlindKeyIndependenceTest` – unchanged, as it tests blind index isolation.
  - Any test that mocks `KeyRewrapService` will need to adapt to the new `KeyLifecycleService` interface, but the core logic (unwrap/wrap) is preserved and thus unit-testable via the adapter.
- **Tests to update/delete**:
  - Direct unit tests of `KeyRewrapService` (if any) will be replaced by tests of `KeyLifecycleService`.
  - The double-fetch TOCTOU issue is eliminated, so tests relying on the old behavior (e.g., interrupting between plan and rotate) are no longer relevant.

## ADR conflicts
- **ADR-0001: Deepen tenant_encryption_seam at key_rewrap_seam** – **No conflict**. This ADR already introduced `TenantKeyStore` as the real adapter at the `key_rewrap_seam`. Our proposal builds on that by placing the `key_lifecycle_seam` *outside* (i.e., deeper than) the `key_rewrap_seam`. The `KeyLifecycleService` uses `TenantKeyStore` as its adapter, so the seam hierarchy is:
  ```
  Application -> KeyLifecycleService (key_lifecycle_seam) -> TenantKeyStore (key_rewrap_seam) -> JPA
  ```
  This is consistent with ADR-0001 and does not modify it.

## Recommendation strength
**Strong**. The deepening addresses clear duplication,TOCTOU risk, and separation-of-concerns issues while aligning with the existing `KeyRotation` entity design. The change is localized to the security package and leverages the already-accepted `TenantKeyStore` adapter. It enables future `dek_rotation` implementation without touching callers and provides a clean audit boundary.