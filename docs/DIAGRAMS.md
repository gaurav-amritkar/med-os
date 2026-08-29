# MedOS HMS — Structure & Sequence Diagrams

Actual diagrams of the current running system (Spring Boot 3.2 + React 19 + PostgreSQL 16 + Redis 7, Docker Compose).

> Mermaid diagrams — render in GitHub, VS Code (Markdown Preview Mermaid), or any Mermaid viewer.

## 1. System Architecture (Structure)

```mermaid
flowchart TB
    subgraph Client["Client (Browser / Mobile)"]
        React["React 19 SPA<br/>Vite + nginx"]
        Stores["Zustand Stores<br/>(authStore, toastStore, loadingStore)"]
        API["Axios API Client<br/>(Bearer JWT interceptor)"]
    end

    subgraph Backend["Spring Boot 3.2 — :8080"]
        subgraph Controllers["Controllers"]
            AuthC[AuthController]
            PatC[PatientController]
            EncC[EncounterController]
            BillC[BillingController]
            AdmC[AdmissionController]
            PharC[PharmacyController]
            DashC[DashboardController]
        end
        subgraph Security["Security Layer"]
            IdemF[IdempotencyFilter - Redis]
            JWTF[JwtAuthenticationFilter]
            SecCfg[SecurityConfig - RBAC]
        end
        subgraph Services["Services"]
            AuthS[AuthService]
            PatS[PatientService]
            EncS[EncounterService]
            BillS[BillingService]
            AdmS[AdmissionService]
            PharS[PharmacyService - FEFO dispense]
            DashS[DashboardService]
            AiS[AiMedicineService - keyword + cache]
            IdemS[IdempotencyService]
            BalS[PatientBalanceService]
        end
    end

    subgraph Data["Storage"]
        PG[(PostgreSQL 16<br/>19 tables)]
        RD[(Redis 7<br/>catalog / idempotency / rate-limit)]
    end

    React --> Stores --> API
    API -->|"HTTPS /api/*"| Controllers
    Controllers --> Security
    Security --> Services
    Services --> PG
    Services --> RD
    AiS -.cache.-> RD
    IdemS --> RD
    AuthS --> RD
```

## 2. Authentication Sequence

```mermaid
sequenceDiagram
    actor U as User (Browser)
    participant LC as Login.jsx
    participant API as Axios Client
    participant F as Idempotency + JWT Filters
    participant AC as AuthController
    participant AS as AuthService
    participant DB as PostgreSQL
    participant RD as Redis (rate limit)

    U->>LC: Enter username/password
    LC->>API: POST /auth/login
    API->>F: Public endpoint (no auth)
    F->>AC: login(request)
    AC->>AS: login(request)
    AS->>RD: checkLoginAttempts(username + IP)
    AS->>DB: findByUsername
    AS->>AS: passwordEncoder.matches()
    AS->>AS: JwtTokenProvider.generateToken(uid, role)
    AS->>DB: update lastLogin
    AS-->>LC: {token, userId, role, fullName}
    LC->>LC: authStore.login(token, user) -> sessionStorage
    LC->>U: navigate /dashboard
```

## 3. Clinical Flow — Encounter -> Prescription -> Dispense -> Charge

```mermaid
sequenceDiagram
    actor D as Doctor
    participant E as Encounters.jsx
    participant API as Axios
    participant EC as EncounterController
    participant ES as EncounterService
    participant AI as AiMedicineService
    participant PC as PharmacyController
    participant PS as PharmacyService
    participant DB as PostgreSQL
    participant RD as Redis (catalog cache)

    D->>E: Select patient -> Start Encounter
    E->>API: POST /encounters
    API->>EC: createEncounter
    EC->>ES: createEncounter
    ES->>DB: save encounter (status open)

    D->>E: Click "Suggest" (AI Advisor)
    E->>API: POST /encounters/suggest-medicines
    API->>EC: suggestMedicines
    EC->>AI: suggestMedicines(disease, complaint)
    AI->>DB: DiseaseMedicineMap + MedicineCatalog
    AI->>RD: @Cacheable medicineCatalog
    AI-->>E: ranked medicine list

    D->>E: + Prescribe (medicine)
    E->>API: POST /encounters/{id}/prescriptions
    API->>EC: addPrescription
    EC->>ES: addPrescription
    ES->>DB: save prescription (status pending)

    Note over D,PS: Pharmacist dispenses (separate session)
    D->>PC: POST /pharmacy/dispense
    PC->>PS: dispense(request)
    PS->>DB: findAvailableBatchesByFefoForUpdate (PESSIMISTIC_WRITE)
    PS->>DB: deduct qty batch-by-batch (oldest expiry first)
    PS->>DB: StockTransaction (OUT) per batch
    PS->>DB: prescription -> dispensed
    PS->>DB: Charge + GST auto-generated
    PS->>DB: PatientBalanceService.recalculateBalance()
```

## 4. Patient Registration Sequence

```mermaid
sequenceDiagram
    actor R as Receptionist
    participant P as Patients.jsx
    participant API as Axios
    participant PC as PatientController
    participant PS as PatientService
    participant DB as PostgreSQL

    R->>P: Fill form + DPDP consent -> Register
    P->>API: POST /patients
    API->>PC: registerPatient
    PC->>PS: registerPatient
    PS->>DB: nextval(uhid_seq) -> UHID
    PS->>DB: encrypt PII, save patient
    PS->>DB: save ConsentRecord (DPDP)
    PS-->>P: patient created
    P->>API: GET /patients (refetch list)
```

## 5. Billing — Invoice + Payment Sequence

```mermaid
sequenceDiagram
    actor B as Billing Clerk
    participant Bi as Billing.jsx
    participant API as Axios
    participant BC as BillingController
    participant BS as BillingService
    participant DB as PostgreSQL
    participant ID as IdempotencyService (Redis)

    B->>Bi: Select patient
    Bi->>API: GET unbilled + invoices (parallel)
    API->>BC: getUnbilled / getInvoices
    BC->>BS: query charges / invoices

    B->>Bi: Select charges -> Generate Invoice
    Bi->>API: POST /billing/invoices
    API->>BC: generateInvoice
    BC->>BS: generateInvoice
    BS->>DB: Invoice (findByIdForUpdate lock)
    BS->>DB: charges -> status billed
    BS-->>Bi: invoice created

    B->>Bi: Record Payment
    Bi->>API: POST /billing/payments
    API->>ID: idempotency key check
    API->>BC: recordPayment
    BC->>BS: recordPayment (pessimistic lock)
    BS->>DB: Payment (status success)
    BS->>DB: PatientBalanceService.recalculateBalance()
```

## 6. Admission -> Discharge Sequence

```mermaid
sequenceDiagram
    actor N as Nurse / Doctor
    participant A as Admissions.jsx
    participant API as Axios
    participant AC as AdmissionController
    participant AS as AdmissionService
    participant DB as PostgreSQL

    N->>A: Select available room -> Admit
    A->>API: POST /admissions
    API->>AC: admitPatient
    AC->>AS: admitPatient
    AS->>DB: RoomRepository.findByIdForUpdate (lock)
    AS->>DB: save admission (status admitted)
    AS->>DB: room -> occupied

    Note over N,AS: Later - Discharge
    N->>A: Enter discharge diagnosis -> Process
    A->>API: PUT /admissions/{id}/discharge
    API->>AC: dischargePatient
    AC->>AS: dischargePatient
    AS->>DB: calc room charges (days x rate)
    AS->>DB: Charge auto-posted to ledger
    AS->>DB: admission -> discharged, room freed
    AS->>DB: PatientBalanceService.recalculateBalance()
```

## Cross-Cutting Mechanisms

| Mechanism | Implementation | Where |
|-----------|----------------|-------|
| **Auth** | JWT HS256, `uid` + `role` claims, stateless | `JwtAuthenticationFilter` -> `SecurityContext` |
| **RBAC** | `@PreAuthorize("hasRole(...)")` | All controllers |
| **Idempotency** | Redis key `idempotency:{ep}:{key}`, 24h TTL | payments, invoices, dispense |
| **Caching** | `@Cacheable medicineCatalog` (4h), rooms (30m) | `AiMedicineService`, `AdmissionService` |
| **FEFO** | `ORDER BY expiryDate ASC` + `PESSIMISTIC_WRITE` | `PharmacyService.dispense` |
| **Balance** | `outstanding = SUM(charges) - SUM(payments)` (atomic) | `PatientBalanceService` |
| **Rate limit** | Max 5 fails / 15 min per user + IP | `LoginRateLimiter` (Redis) |

## API Versioning

All REST endpoints are versioned under `/api/v1/...`.

### Versioning Scheme

- **Current version**: `v1`
- **Base path**: `/api/v1/{domain}/{resource}`
- **Example**: `POST /api/v1/encounters`, `GET /api/v1/patients/{id}`

### When to bump the version

| Change type | Action |
|-------------|--------|
| New endpoint | Add to current version |
| New optional field | Add to current version |
| New required field | Consider new version |
| Breaking change (rename/remove field, change behavior) | New version `/api/v2/...` |

### OpenAPI Documentation

- **Swagger UI**: `http://localhost:8080/swagger-ui.html` (requires `ADMIN` role)
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs` (requires `ADMIN` role)
- The API docs include JWT Bearer authentication scheme — click "Authorize" and paste your token

### Controllers by Domain

| Controller | Base Path | Description |
|------------|-----------|-------------|
| `AuthController` | `/api/v1/auth` | Login |
| `PatientController` | `/api/v1/patients` | Patient CRUD |
| `EncounterController` | `/api/v1/encounters` | OPD encounters, prescriptions, AI suggestions |
| `AdmissionController` | `/api/v1/admissions` | IPD admission/discharge, rooms |
| `PharmacyController` | `/api/v1/pharmacy` | Medicine catalog, stock, FEFO dispense |
| `BillingController` | `/api/v1/billing` | Invoices, payments |
| `DashboardController` | `/api/v1/dashboard`, `/api/v1/notifications`, `/api/v1/users` | Dashboard stats, notifications |

## Docker Compose Topology

```mermaid
flowchart LR
    BR[Browser] -->|:80| NGINX[nginx<br/>React SPA + proxy]
    NGINX -->|/api/v1/*| BE[backend:8080<br/>Spring Boot]
    NGINX -->|/ws/*| BE
    BE -->|JDBC| PG[(db:5432<br/>PostgreSQL 16)]
    BE -->|Redis protocol| RD[(redis:6379<br/>Redis 7)]
    BE -->|HTTP| SELF[own health endpoints]
```