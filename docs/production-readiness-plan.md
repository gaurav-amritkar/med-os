# MedOS HMS — Production Readiness Plan (2026-09-27)

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Make MedOS HMS production-ready by fixing all open GitHub issues, hardening security, and ensuring the multi-tenant modular architecture is complete and tested.

**Architecture:** Multi-tenant Spring Boot 3.2 monolith with bounded contexts (pharmacy, billing, payment), event-driven balance synchronization, row-level tenant isolation via TenantStatementInspector.

**Tech Stack:** Java 17, Spring Boot 3.2, PostgreSQL, Redis, React 19 + Vite frontend, Docker Compose.

**Current State:** 85 backend tests passing (1 skipped), compilation clean. 15 frontend tests passing. Open GitHub issues: 8 risk/nit items requiring fixes.

---

## Phase 1: Fix All Open GitHub Issues (P0 — blockers)

### Task 1: Fix partial dispense stock leak (Issue #32)
**Objective:** Prevent stock deductions from persisting when insufficient stock is detected mid-dispense.

**Files:**
- Modify: `backend/src/main/java/com/medos/modules/pharmacy/service/DispenseService.java:67-93`
- Test: `backend/src/test/java/com/medos/service/PharmacyServiceTest.java`

**Step 1: Write failing test**
The existing `dispense_insufficientStock_acrossBatchesThrows` test asserts batch was reduced before failure. Need a new test that verifies NO batches are mutated when total available < required.

**Step 2: Run test to verify failure**
Run: `cd backend && mvn test -Dtest=PharmacyServiceTest#dispense_insufficientStock_lastBatch_rollsBackAllDeductions -q`

**Step 3: Implement fix**
Move the totalAvailable check BEFORE the deduction loop:

```java
int totalAvailable = batches.stream().mapToInt(MedicineBatch::getRemainingQty).sum();
if (totalAvailable < requiredQty) {
    throw new BusinessException("Insufficient stock: required " + requiredQty + ", available " + totalAvailable);
}
// Now apply deductions — all or nothing
int remaining = requiredQty;
for (MedicineBatch batch : batches) {
    if (remaining <= 0) break;
    int deduction = Math.min(remaining, batch.getRemainingQty());
    batch.setRemainingQty(batch.getRemainingQty() - deduction);
    remaining -= deduction;
    medicineBatchRepository.save(batch);
    // ... create StockTransaction
}
```

**Step 4: Run test to verify pass**
Expected: PASS, all 85 tests pass.

**Step 5: Commit**
```bash
git add backend/src/main/java/com/medos/modules/pharmacy/service/DispenseService.java backend/src/test/java/com/medos/service/PharmacyServiceTest.java
git commit -m "fix(pharmacy): prevent partial stock deduction on insufficient stock (closes #32)"
```

---

### Task 2: Add JWT validation error differentiation (Issue #36)
**Objective:** Differentiate between expired, invalid signature, malformed tokens in validateToken().

**Files:**
- Modify: `backend/src/main/java/com/medos/security/JwtTokenProvider.java:84-89`
- Modify: `backend/src/main/java/com/medos/security/JwtAuthenticationFilter.java`
- Test: `backend/src/test/java/com/medos/security/JwtTokenProviderTest.java`

**Step 1: Create TokenValidationResult enum**
```java
public enum TokenValidationResult {
    VALID, EXPIRED, INVALID_SIGNATURE, MALFORMED, WRONG_ISSUER, WRONG_KEY
}
```

**Step 2: Update validateToken()**
```java
public TokenValidationResult validateToken(String token) {
    try {
        parseToken(token);
        return TokenValidationResult.VALID;
    } catch (ExpiredJwtException e) {
        return TokenValidationResult.EXPIRED;
    } catch (SignatureException e) {
        return TokenValidationResult.INVALID_SIGNATURE;
    } catch (MalformedJwtException e) {
        return TokenValidationResult.MALFORMED;
    } catch (InvalidClaimException e) {
        return TokenValidationResult.WRONG_ISSUER;
    } catch (Exception e) {
        return TokenValidationResult.WRONG_KEY;
    }
}
```

**Step 3: Update JwtAuthenticationFilter**
Return specific 401 sub-codes via response header or differentiate logging.

**Step 4: Add tests for each failure mode**
**Step 5: Commit and close #36**

---

### Task 3: Fix login rate limiter username enumeration (Issue #38)
**Objective:** Prevent username enumeration via rate limit timing differences.

**Files:**
- Modify: `backend/src/main/java/com/medos/service/AuthService.java:30-57`

**Step 1: Fix — always run rate limiter check**
```java
// Check rate limit for ALL attempts (including invalid usernames)
if (rateLimiter.isBlocked(request.getUsername())) {
    throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again later.");
}

User user = userRepository.findByUsername(request.getUsername())
        .or(() -> userRepository.findByEmail(request.getUsername()))
        .orElseThrow(() -> {
            rateLimiter.recordFailure(request.getUsername()); // Record for invalid usernames too
            return new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        });
```

**Step 2: Add test for invalid username rate limiting**
**Step 3: Commit and close #38**

---

### Task 4: Fix balance event timing — use AFTER_COMMIT (Issue #40)
**Objective:** Ensure PatientBalanceEvent is published only after transaction commits.

**Files:**
- Modify: `backend/src/main/java/com/medos/modules/billing/event/PatientBalanceEventListener.java:18-24`

**Step 1: Add @TransactionalEventListener**
```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleBalanceUpdate(PatientBalanceEvent event) {
    log.debug("Async balance recalculation for patient: {}", event.getPatientId());
    patientBalanceService.recalculateBalance(event.getPatientId());
}
```

**Step 2: Remove @Transactional from listener (runs in new tx after commit)**
**Step 3: Verify PatientBalanceService.recalculateBalance uses MANDATORY propagation**
**Step 4: Commit and close #40**

---

### Task 5: Restrict CORS allowedHeaders (Issue #41)
**Objective:** Replace wildcard CORS headers with specific allowlist.

**Files:**
- Modify: `backend/src/main/java/com/medos/config/SecurityConfig.java:79`

**Step 1: Replace wildcard**
```java
cfg.setAllowedHeaders(List.of(
    "Authorization", "Content-Type", "Idempotency-Key",
    "X-Requested-With", "Accept", "Origin", "X-Tenant-Id"
));
```

**Step 2: Commit and close #41**

---

### Task 6: Lock charges when marking paid (Issue #43)
**Objective:** Prevent concurrent payment from double-marking charges.

**Files:**
- Modify: `backend/src/main/java/com/medos/modules/billing/service/BillingService.java:94, 123`
- Modify: `backend/src/main/java/com/medos/modules/payment/service/PaymentService.java` (if charges updated there)

**Step 1: Add locked charge query**
```java
// In ChargeRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM Charge c WHERE c.invoiceId = :invoiceId AND c.status = 'billed'")
List<Charge> findByInvoiceIdForUpdate(@Param("invoiceId") UUID invoiceId);
```

**Step 2: Use locked query in payment flow**
**Step 3: Commit and close #43**

---

### Task 7: Make idempotency TTL configurable (Issue #44)
**Objective:** Allow per-endpoint TTL configuration for idempotency keys.

**Files:**
- Modify: `backend/src/main/java/com/medos/service/IdempotencyService.java`

**Step 1: Add TTL map**
```java
private static final Map<String, Duration> TTL_BY_ENDPOINT = Map.of(
    "billing/payments", Duration.ofHours(24),
    "billing/invoices", Duration.ofHours(24),
    "pharmacy/dispense", Duration.ofHours(48)
);
```

**Step 2: Use per-endpoint TTL**
**Step 3: Commit and close #44**

---

### Task 8: Fix health endpoint show-details for K8s (Issue #45)
**Objective:** Allow K8s probes to get health details while keeping public endpoint secure.

**Files:**
- Modify: `backend/src/main/resources/application-prod.yml:61`

**Step 1: Change show-details**
```yaml
management:
  endpoint:
    health:
      show-details: when_authorized
```

**Step 2: Verify /manage/health secured for non-admin**
**Step 3: Commit and close #45**

---

## Phase 2: Security Hardening (P1)

### Task 9: Add structured logging with correlation IDs
**Objective:** Add traceId/userId to MDC in JwtAuthenticationFilter, configure JSON logging.

**Files:**
- Modify: `backend/src/main/java/com/medos/security/JwtAuthenticationFilter.java`
- Modify: `backend/src/main/resources/logback-spring.xml` (create if missing)

### Task 10: Add Prometheus metrics endpoint
**Objective:** Expose /manage/prometheus with HTTP timings, DB pool metrics, dispensing throughput.

**Files:**
- Modify: `backend/src/main/resources/application-prod.yml`
- Add: Micrometer dependencies in pom.xml if missing

### Task 11: Implement circuit breaker for external calls
**Objective:** Add Resilience4j circuit breaker for payment gateway calls.

**Files:**
- Add: Resilience4j dependency
- Modify: PaymentService to wrap external calls

---

## Phase 3: Frontend Hardening (P1)

### Task 12: Add token refresh mechanism
**Objective:** Implement refresh token flow (access token 15min + refresh token 7d in httpOnly cookie).

**Files:**
- Modify: `frontend/src/store/authStore.js`
- Add: Refresh token endpoint in backend

### Task 13: Add CSP headers in nginx
**Objective:** Add Content-Security-Policy, Permissions-Policy, HSTS headers.

**Files:**
- Modify: `frontend/nginx.conf`

### Task 14: Add React error boundaries
**Objective:** Add error boundaries on all pages, global toast for 5xx errors.

**Files:**
- Modify: `frontend/src/components/ErrorBoundary.jsx`
- Add: Page-level error boundaries

---

## Phase 4: Production Deployment (P2)

### Task 15: Add CI/CD pipeline
**Objective:** GitHub Actions workflow for build, test, lint, security scan.

**Files:**
- Create: `.github/workflows/ci.yml`

### Task 16: Add Docker secrets for production
**Objective:** Use Docker secrets for DB_PASSWORD, JWT_SECRET in production compose.

**Files:**
- Modify: `docker-compose.yml`
- Create: `docker-compose.prod.yml`

### Task 17: Configure TLS termination
**Objective:** Add Caddy/Traefik for TLS with auto-LE certs.

**Files:**
- Create: `Caddyfile` or `traefik.yml`

---

## Phase 5: Documentation & Cleanup (P2)

### Task 18: Create production deployment runbook
**Objective:** Document deploy steps, rollback, backup/restore, secret rotation.

**Files:**
- Create: `docs/operations.md` (or update existing)

### Task 19: Archive outdated documents
**Objective:** Move superseded planning docs to docs/archive/.

**Files:**
- Archive: `docs/PRODUCTION_READINESS_VERDICT.md` → `docs/archive/`
- Archive: `docs/DIAGRAMS.md` → `docs/archive/`
- Archive: `REFACTORING_REPORT.md` → `docs/archive/`
- Archive: `SUPABASE_OPTION_C_STEPS.md` → `docs/archive/`

### Task 20: Update README with current state
**Objective:** Reflect multi-tenant architecture, bounded contexts, production readiness status.

**Files:**
- Modify: `README.md`

---

## Verification Gates

After each phase:
1. `cd backend && mvn test` — all 85+ tests pass
2. `cd frontend && npm test && npm run lint` — all pass
3. `cd backend && mvn -DskipTests package` — JAR builds successfully
4. No new compilation warnings introduced

---

## Risks & Mitigations

1. **Multi-tenant row-level isolation:** Tested via TenantIsolationTest (3 tests passing). Risk: missing tenant filter on some queries. Mitigation: audit all repository methods.

2. **Event-driven balance:** Async listener with AFTER_COMMIT. Risk: listener failures. Mitigation: dead letter queue for balance events.

3. **Payment concurrency:** PESSIMISTIC_WRITE on invoice. Risk: deadlocks under high contention. Mitigation: retry with backoff.

---

## GitHub Issues Mapping

| Issue | Phase | Status |
|-------|-------|--------|
| #32 Partial dispense stock leak | 1 | OPEN → fix |
| #36 JWT validation differentiation | 1 | OPEN → fix |
| #38 Rate limiter username enumeration | 1 | OPEN → fix |
| #40 Balance event timing | 1 | OPEN → fix |
| #41 CORS wildcard headers | 1 | OPEN → fix |
| #43 Payment concurrency charge lock | 1 | OPEN → fix |
| #44 Idempotency TTL configurable | 1 | OPEN → fix |
| #45 Health show-details K8s | 1 | OPEN → fix |
| #42 getRoleFromToken() NPE | 1 | CLOSED (fixed) |
| #39 Encryption legacy tolerance | 1 | CLOSED (fixed) |
| #37 Swagger UI auth | 1 | CLOSED (fixed) |
| #35 Migration service Caddy | 1 | CLOSED (fixed) |
| #34 Idempotency filter path | 1 | CLOSED (fixed) |
| #33 HTTPS self-signed | 1 | CLOSED (fixed) |
| #31 Encounter RBAC | 1 | CLOSED (fixed) |
| #30 Billing Idempotency | 1 | CLOSED (fixed) |
| #29 Pharmacy Dispense Auth | 1 | CLOSED (fixed) |
| #28 Auth Login 401 | 1 | CLOSED (fixed) |
| #27 Bad Input Handling | 1 | CLOSED (fixed) |
| #26 Duplicate UHID | 1 | CLOSED (fixed) |
| #25 Bad Input Handling | 1 | CLOSED (fixed) |
| #24/#21 Patient Balance | 1 | CLOSED (fixed) |
| #23/#20 UHID Race Condition | 1 | CLOSED (fixed) |
| #22/#19 Invoice Race Condition | 1 | CLOSED (fixed) |
| #50 Appointment scheduling | 3 | OPEN (feature) |
| #49 Payment gateway abstraction | 3 | OPEN (feature) |
| #48 Organization onboarding | 3 | OPEN (feature) |
| #47 Multi-tenant Facility | 3 | OPEN (feature) |
| #46 Generic provider types | 3 | OPEN (feature) |

---

## Implementation Order

1. **Phase 1 Tasks 1-8** (all GitHub issue fixes, sequential)
2. **Phase 2 Tasks 9-11** (security hardening)
3. **Phase 3 Tasks 12-14** (frontend hardening)
4. **Phase 4 Tasks 15-17** (deployment)
5. **Phase 5 Tasks 18-20** (documentation)
6. **Phase 3 Feature Issues** (#46-50) — deferred, not blocking production

---

*Last updated: 2026-09-27. Track progress by closing GitHub issues as each task completes.*
