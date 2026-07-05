# MedOS HMS v3.0

**MedOS** is an ultra-premium, role-based Hospital Management System designed to digitize, streamline, and intelligently assist end-to-end hospital workflows — from patient registration and clinical documentation to pharmacy dispensing and financial reconciliation.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 17 + Spring Boot 3.2 |
| **Frontend** | React 19 + Vite |
| **Database** | PostgreSQL (primary) + Redis (cache) |
| **Auth** | JWT (stateless) + BCrypt |
| **Security** | Spring Security, RBAC |
| **API** | RESTful (JSON) + WebSocket (real-time) |
| **Persistence** | JPA/Hibernate + Flyway Migrations |
| **AI** | Built-in keyword-based medicine advisor |

## Architecture

```
frontend/ (React + Vite)
  ├── src/
  │   ├── api/        # Axios client & API modules
  │   ├── components/ # Layout, Sidebar, Header, Toast
  │   ├── pages/      # Login, Dashboard, Patients, Encounters,
  │   │               # Pharmacy, Admissions, Billing
  │   └── store/      # Zustand (auth, toast)
  └── frontend/

backend/ (Spring Boot)
  ├── src/main/java/com/medos/
  │   ├── config/     # Security, Redis Cache, WebSocket
  │   ├── controller/ # REST controllers
  │   ├── dto/        # Request/Response DTOs
  │   ├── entity/     # JPA entities (19 tables)
  │   ├── exception/  # Global error handling
  │   ├── repository/ # Spring Data JPA repos
  │   ├── security/   # JWT, UserDetails, Auth filter
  │   ├── service/    # Business logic
  │   └── util/       # Audit logger
  └── src/main/resources/
      └── db/migration/  # Flyway SQL migrations
```

## Database Schema (19 Tables)

- `users` - Role-based login (admin, doctor, nurse, receptionist, pharmacist, billing)
- `patients` - Demographics, UHID, DPDP consent, outstanding balance
- `appointments` - Scheduling, check-in/out tracking
- `encounters` - Clinical visits, vitals, diagnosis, AI notes
- `prescriptions` - Medicine orders with status tracking
- `rooms` - Ward/bed inventory with daily rates
- `admissions` - IPD bed allocation, discharge, room charges
- `medicine_catalog` - Drug master with pricing, keywords
- `medicine_batches` - Lot tracking with expiry (FEFO key)
- `stock_transactions` - Complete inventory ledger
- `charges` - Auto-generated billing line items
- `invoices` - GST-compliant invoices
- `payments` - Cash, card, UPI, insurance
- `lab_orders` - Diagnostic test tracking
- `disease_medicine_map` - AI suggestion engine
- `audit_log` - Full audit trail
- `consents` - DPDP compliance records
- `opd_queue` - Appointment queue management
- `notifications` - Real-time alerts

## Getting Started

### Prerequisites

- Java 17+
- Node.js 18+
- PostgreSQL 15+
- Redis 7+
- Maven 3.8+

### Backend Setup

```bash
cd backend

# Create PostgreSQL database
createdb medos

# Configure application.yml (update DB credentials)

# Run with Maven
mvn spring-boot:run
```

### Frontend Setup

```bash
cd frontend
npm install
npm run dev
```

### Default Credentials

| Role | Username | Password |
|------|----------|----------|
| Admin | `admin` | `password` |
| Doctor | `doctor` | `password` |
| Nurse | `nurse` | `password` |
| Receptionist | `reception` | `password` |
| Pharmacist | `pharmacy` | `password` |
| Billing | `billing` | `password` |

## API Endpoints

### Auth
- `POST /api/auth/login` - Authenticate & get JWT

### Patients
- `GET /api/patients` - List/search patients
- `POST /api/patients` - Register new patient
- `GET /api/patients/{id}` - Get patient details
- `GET /api/patients/uhid/{uhid}` - Lookup by UHID

### Encounters (OPD)
- `POST /api/encounters` - Create encounter with vitals
- `GET /api/encounters/{id}` - Get encounter
- `POST /api/encounters/{id}/sign` - Sign & close
- `POST /api/encounters/suggest-medicines` - AI advisor
- `GET /api/encounters/prescriptions/pending` - Pending Rx

### Pharmacy
- `GET /api/pharmacy/medicines` - Medicine catalog
- `POST /api/pharmacy/medicines` - Add medicine
- `POST /api/pharmacy/medicines/{id}/stock-in` - Add stock batch
- `POST /api/pharmacy/dispense` - FEFO dispense
- `GET /api/pharmacy/transactions` - Stock ledger

### Admissions (IPD)
- `POST /api/admissions` - Admit patient to room
- `PUT /api/admissions/{id}/discharge` - Discharge & auto-bill
- `GET /api/admissions/active` - Active admissions
- `GET /api/admissions/rooms` - All rooms
- `GET /api/admissions/rooms/available` - Available beds

### Billing
- `POST /api/billing/invoices` - Generate GST invoice
- `POST /api/billing/payments` - Record payment
- `GET /api/billing/patients/{id}/invoices` - Patient invoices
- `GET /api/billing/patients/{id}/unbilled` - Unbilled charges

### Dashboard & Notifications
- `GET /api/dashboard` - Role-based analytics
- `GET /api/notifications` - User notifications
- `GET /api/notifications/unread-count` - Unread count

## Key Features

1. **FEFO Dispensing** - First-Expired-First-Out algorithm auto-selects oldest batches when dispensing
2. **Auto-billing** - Pharmacy dispenses and room charges auto-post to patient ledger
3. **AI Medicine Advisor** - Keyword/catalog matching suggests medicines from hospital formulary
4. **DPDP Compliance** - Patient consent tracking for data privacy
5. **Audit Trail** - All mutations logged with user, IP, and diff
6. **Real-time Notifications** - WebSocket-based alerts for critical events
7. **Role-based Access** - 6 roles with granular route and API protection
