---
inclusion: fileMatch
fileMatchPattern: "backend/src/main/java/com/medos/security/**,backend/src/main/java/com/medos/config/SecurityConfig.java,backend/src/main/java/com/medos/service/AuthService.java"
---

# MedOS Security — Steering Rules

Applies whenever touching auth, JWT, encryption, or tenant key code.

## Auth flow

1. `POST /api/v1/auth/login` → `AuthService` → validates credentials → builds JWT.
2. Every subsequent request → `JwtAuthenticationFilter` → resolves `GrantedAuthority` from the **live membership row** in `tenant_users`, not from the JWT `role` claim (R02 fix, #236). Token carries `tenantId` and `userId` only.
3. Both `tenant.active` and `tenant_users.active` are checked on every authenticated request. Deactivation takes effect immediately.

## Encryption chain

```
PII_ENCRYPTION_KEY (KEK, env/secret file)
  └─ wraps per-tenant DEK  →  stored in tenant_keys.wrapped_dek
        └─ encrypts PII columns  →  ciphertext format: kv1.d<gen>:<base64>
  └─ wraps per-tenant blind-index key  →  stored in tenant_keys.wrapped_bi_key
        └─ hashes patient names for search
```

Key classes:
- `TenantKeyHolder` — resolves and caches DEK for 5 minutes
- `KeyWrapCipher` — AES-256-GCM KEK↔DEK wrap/unwrap
- `PiiCiphertextFormat` — ciphertext versioning (`kv1.d<gen>:`)
- `BlindIndexUtil` + `BlindIndexKeyService` — name search index
- `DekRotationService` — per-tenant DEK rotation (do not call directly; use the rotation runner)

## Rules when editing this package

- Never add an inline default for `PII_ENCRYPTION_KEY` or `JWT_SECRET`. The app fails closed if they're absent — that's intentional.
- Never change `JwtAuthenticationFilter` to read authority from the token claim. It must come from the membership row.
- Never trust `X-Forwarded-For` for rate limiting. `LoginRateLimiter` uses `HttpServletRequest.getRemoteAddr()`.
- Login brute-force: 5 failures per (user, socket peer) → 15-min lockout. 20 failures username-wide → broader lockout. Redis-backed with in-memory fallback.
- `MEDOS_KEY_ROTATION_MODE` controls KEK rotation (dry-run / apply / verify). Modes are env vars, not CLI flags — a key on a command line is visible in `docker inspect`.
- Tenant provisioning at `/api/v1/onboarding/register` requires a platform secret or `super_admin` token. Blank secret = deny. This endpoint has **no rate limit yet** (G07 — open gap).

## What to check after any change here

```bash
cd backend && mvn clean test -pl . -Dtest="*Security*,*Auth*,*Jwt*,*Tenant*,*Rbac*"
```

Also run the full RBAC matrix test: `RbacMatrixTest`.
