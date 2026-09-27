#!/usr/bin/env bash
# MedOS End-to-End API Integration Test (Docker stack)
#
# Usage:
#   ./tests/e2e/api-test.sh --skip-start   # assume stack already running
#   ./tests/e2e/api-test.sh                # start stack first
#
# Covers: health, auth (6 roles), RBAC matrix, patient/encounter/prescription/
# pharmacy FEFO dispense/admission/billing happy paths, idempotency replay,
# and negative scenarios (401/403/404/400 contracts).
set -uo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

BASE_URL="${BASE_URL:-http://localhost:8080}"
API_URL="$BASE_URL/api/v1"
TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$TEST_DIR/../.." && pwd)"
REPORT_FILE="$TEST_DIR/test-report.md"

TESTS_PASSED=0; TESTS_FAILED=0; TESTS_SKIPPED=0
RUN_TS="$(date +%s)"   # run-scoped suffix for unique-constrained names

TOKEN_ADMIN=""; TOKEN_DOCTOR=""; TOKEN_NURSE=""; TOKEN_RECEPTION=""; TOKEN_PHARMACY=""; TOKEN_BILLING=""
PATIENT_ID=""; PATIENT2_ID=""; ENCOUNTER_ID=""; ENCOUNTER2_ID=""; PRESCRIPTION_ID=""; MEDICINE_ID=""; ADMISSION_ID=""; INVOICE_ID=""

log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_error()   { echo -e "${RED}[FAIL]${NC} $1"; }
log_skip()    { echo -e "${YELLOW}[SKIP]${NC} $1"; }
log_test()    { echo -e "\n${YELLOW}=== TEST: $1 ===${NC}"; }

record_test() {
    echo "- [$2] $1" >> "$REPORT_FILE"
    [[ -n "${3:-}" ]] && echo "  - $3" >> "$REPORT_FILE"
}
pass() { log_success "$1"; record_test "$1" "PASS" "${2:-}"; TESTS_PASSED=$((TESTS_PASSED+1)); }
fail() { log_error "$1"; record_test "$1" "FAIL" "${2:-}"; TESTS_FAILED=$((TESTS_FAILED+1)); }
skip() { log_skip "$1"; record_test "$1" "SKIP" "${2:-}"; TESTS_SKIPPED=$((TESTS_SKIPPED+1)); }

cleanup() { rm -f "$TEST_DIR"/*.json 2>/dev/null || true; }
trap cleanup EXIT

check_prerequisites() {
    local missing=0
    for tool in curl jq docker; do
        command -v "$tool" &>/dev/null || { log_error "$tool is required"; missing=1; }
    done
    [[ ! -f "$PROJECT_ROOT/.env" ]] && { log_error ".env file not found"; missing=1; }
    [[ $missing -eq 1 ]] && exit 1
    log_info "All prerequisites met"
}

start_stack() {
    [[ "${1:-}" == "--skip-start" ]] && { log_info "Skipping stack start"; return 0; }
    log_info "Starting MedOS Docker stack..."
    cd "$PROJECT_ROOT"
    docker compose down --remove-orphans 2>/dev/null || true
    docker compose up -d --build || { log_error "docker compose up failed"; exit 1; }

    local max_wait=300 waited=0
    while [[ $waited -lt $max_wait ]]; do
        curl -sf "$BASE_URL/manage/health" >/dev/null 2>&1 && break
        sleep 10; waited=$((waited + 10))
        log_info "Waiting for backend... (${waited}s/${max_wait}s)"
    done
    if [[ $waited -ge $max_wait ]]; then
        log_error "Backend did not become healthy within ${max_wait}s"
        docker compose logs backend --tail=50; exit 1
    fi
    "$PROJECT_ROOT/tools/seed-dev.sh" 2>/dev/null || log_info "Seed may have already run"
    log_info "Docker stack is ready"
}

# api METHOD ENDPOINT [DATA] [TOKEN] [EXPECTED_STATUS] [EXTRA_HEADER] -> body on stdout, rc!=0 on status mismatch
api_call() {
    local method="$1" endpoint="$2" data="${3:-}" token="${4:-}" expected="${5:-200}" extra="${6:-}"
    local args=(-s -w $'\n%{http_code}' -X "$method" "$API_URL$endpoint")
    [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
    [[ -n "$data" ]] && args+=(-H "Content-Type: application/json" -d "$data")
    [[ -n "$extra" ]] && args+=(-H "$extra")
    local response body status
    response=$(curl "${args[@]}" 2>/dev/null)
    status=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    echo "$body"
    [[ "$status" != "$expected" ]] && return 1
    return 0
}

# status_only METHOD ENDPOINT DATA TOKEN -> "status|body"
status_only() {
    local method="$1" endpoint="$2" data="${3:-}" token="${4:-}"
    local response status body
    response=$(curl -s -w $'\n%{http_code}' -X "$method" "$API_URL$endpoint" \
        ${token:+-H "Authorization: Bearer $token"} \
        ${data:+-H "Content-Type: application/json" -d "$data"} 2>/dev/null)
    status=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    echo "$status|$body"
}

test_health() {
    log_test "Health Check APIs"
    local s
    s=$(curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/manage/health" 2>/dev/null || true)
    [[ "$s" == "200" ]] && pass "Health check endpoint" || fail "Health check endpoint" "status=$s"
    s=$(curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/manage/info" 2>/dev/null || true)
    [[ "$s" == "200" ]] && pass "Info endpoint" || fail "Info endpoint" "status=$s"
}

test_auth() {
    log_test "Authentication APIs"
    local role response token
    for role in admin doctor nurse reception pharmacy billing; do
        response=$(api_call POST "/auth/login" "{\"username\":\"$role\",\"password\":\"password\"}" "" 200)
        if [[ $? -eq 0 ]]; then
            token=$(echo "$response" | jq -r '.token // empty')
            case "$role" in
                admin) TOKEN_ADMIN="$token" ;; doctor) TOKEN_DOCTOR="$token" ;;
                nurse) TOKEN_NURSE="$token" ;; reception) TOKEN_RECEPTION="$token" ;;
                pharmacy) TOKEN_PHARMACY="$token" ;; billing) TOKEN_BILLING="$token" ;;
            esac
            [[ -n "$token" ]] && pass "Login as $role" || fail "Login as $role" "no token"
        else
            fail "Login as $role" "response: $(echo "$response" | head -c 120)"
        fi
    done
    response=$(api_call POST "/auth/login" '{"username":"invalid","password":"wrong"}' "" 401) \
        && pass "Invalid login rejected (401)" || fail "Invalid login rejected"
    response=$(api_call GET "/patients" "" "" 401) \
        && pass "Unauthenticated request rejected (401)" || fail "Unauthenticated request rejected"
}

test_patients() {
    log_test "Patient APIs"
    local run_id response
    run_id="$(date +%s)"

    response=$(api_call GET "/patients" "" "$TOKEN_ADMIN" 200)
    if [[ $? -eq 0 ]]; then
        pass "List patients ($(echo "$response" | jq '.content | length') found)"
    else fail "List patients"; fi

    response=$(api_call GET "/patients/uhid/UHID000001" "" "$TOKEN_ADMIN" 200)
    if [[ $? -eq 0 ]]; then
        PATIENT_ID=$(echo "$response" | jq -r '.id')
        pass "Get patient by UHID (id: $PATIENT_ID)"
    else fail "Get patient by UHID"; fi

    [[ -z "$PATIENT_ID" ]] && { skip "Remaining patient tests (no patient)"; return; }

    response=$(api_call GET "/patients/$PATIENT_ID" "" "$TOKEN_ADMIN" 200) \
        && pass "Get patient by ID" || fail "Get patient by ID"

    local new_patient="{\"name\":\"Test Patient\",\"age\":30,\"gender\":\"male\",\"phone\":\"9999${run_id: -6}\",\"email\":\"test-$run_id@example.com\",\"bloodGroup\":\"O+\",\"address\":\"Test Address\",\"dpdpConsent\":true}"
    response=$(api_call POST "/patients" "$new_patient" "$TOKEN_RECEPTION" 201)
    [[ $? -eq 0 ]] && pass "Create patient (receptionist)" || fail "Create patient (receptionist)"

    response=$(api_call POST "/patients" "$new_patient" "$TOKEN_DOCTOR" 403) \
        && pass "RBAC: Doctor cannot create patient (403)" || fail "RBAC: Doctor cannot create patient"
}

test_encounters() {
    log_test "Encounter APIs"
    [[ -z "$PATIENT_ID" ]] && { skip "Encounter tests - no patient"; return; }

    local encounter_data="{\"patientId\":\"$PATIENT_ID\",\"vitals\":{\"bloodPressure\":\"120/80\",\"pulse\":72,\"temperature\":98.6,\"weight\":70.5,\"height\":175},\"chiefComplaint\":\"Headache and fever\",\"diagnosis\":\"Viral fever\",\"clinicalNotes\":\"Mild symptoms\"}"
    local response
    response=$(api_call POST "/encounters" "$encounter_data" "$TOKEN_DOCTOR" 201)
    if [[ $? -eq 0 ]]; then
        ENCOUNTER_ID=$(echo "$response" | jq -r '.id')
        pass "Create encounter (id: $ENCOUNTER_ID)"
    else fail "Create encounter"; fi
    [[ -z "$ENCOUNTER_ID" || "$ENCOUNTER_ID" == "null" ]] && { skip "Remaining encounter tests"; return; }

    api_call GET "/encounters/$ENCOUNTER_ID" "" "$TOKEN_DOCTOR" 200 >/dev/null \
        && pass "Get encounter by ID" || fail "Get encounter by ID"

    response=$(api_call GET "/encounters/patient/$PATIENT_ID?page=0&size=10" "" "$TOKEN_DOCTOR" 200)
    [[ $? -eq 0 ]] && pass "List encounters by patient ($(echo "$response" | jq '.content | length') found)" || fail "List encounters by patient"

    # NOTE: encounter is signed at the end of test_dispense — prescriptions and
    # dispensing must happen while the encounter is still open.

    response=$(api_call POST "/encounters/suggest-medicines" '{"diseaseDescription":"fever and headache"}' "$TOKEN_DOCTOR" 200)
    [[ $? -eq 0 ]] && pass "AI medicine suggestion (advisor disabled, returns list)" || fail "AI medicine suggestion"

    api_call GET "/encounters/prescriptions/pending" "" "$TOKEN_PHARMACY" 200 >/dev/null \
        && pass "Pending prescriptions (pharmacist)" || fail "Pending prescriptions (pharmacist)"
}

test_pharmacy() {
    # Phase 1: catalog + stock (no clinical dependencies)
    log_test "Pharmacy APIs"
    local response

    response=$(api_call GET "/pharmacy/medicines" "" "$TOKEN_PHARMACY" 200)
    [[ $? -eq 0 ]] && pass "List medicines ($(echo "$response" | jq 'length') found)" || fail "List medicines"

    local medicine_data="{\"name\":\"Test Paracetamol $RUN_TS\",\"genericName\":\"Paracetamol\",\"manufacturer\":\"Test Pharma\",\"category\":\"Analgesic\",\"unit\":\"tablet\",\"unitPrice\":10.00,\"reorderLevel\":100,\"keywords\":\"fever,pain,headache\"}"
    response=$(api_call POST "/pharmacy/medicines" "$medicine_data" "$TOKEN_PHARMACY" 201)
    if [[ $? -eq 0 ]]; then
        MEDICINE_ID=$(echo "$response" | jq -r '.id')
        pass "Create medicine (id: $MEDICINE_ID)"
    else fail "Create medicine"; fi
    [[ -z "$MEDICINE_ID" || "$MEDICINE_ID" == "null" ]] && { skip "Remaining pharmacy tests"; return; }

    # stock-in uses QUERY PARAMS (not a JSON body)
    response=$(api_call POST "/pharmacy/medicines/$MEDICINE_ID/stock-in?batchNo=BATCH-E2E-$RUN_TS&expiryDate=2027-12-31&quantity=100&purchasePrice=5.00&supplier=TestSupplier" "" "$TOKEN_PHARMACY" 201)
    [[ $? -eq 0 ]] && pass "Stock in (batch BATCH-E2E-$RUN_TS, 100 units)" || fail "Stock in" "response: $(echo "$response" | head -c 120)"

    response=$(api_call GET "/pharmacy/medicines/$MEDICINE_ID/batches" "" "$TOKEN_PHARMACY" 200)
    [[ $? -eq 0 ]] && pass "Get medicine batches" || fail "Get medicine batches"

    response=$(api_call GET "/pharmacy/transactions" "" "$TOKEN_PHARMACY" 200)
    [[ $? -eq 0 ]] && pass "Get stock transactions" || fail "Get stock transactions"

    response=$(api_call POST "/pharmacy/medicines" "$medicine_data" "$TOKEN_DOCTOR" 403) \
        && pass "RBAC: Doctor cannot create medicine (403)" || fail "RBAC: Doctor cannot create medicine"
}

test_dispense() {
    # Phase 2: prescribe on the (open) encounter, then FEFO dispense.
    log_test "Dispense Workflow (prescription + FEFO)"
    local response

    if [[ -z "$ENCOUNTER_ID" || -z "$MEDICINE_ID" ]]; then
        skip "Dispense workflow (missing encounter/medicine)"; return
    fi

    local rx_data="{\"encounterId\":\"$ENCOUNTER_ID\",\"patientId\":\"$PATIENT_ID\",\"medicineId\":\"$MEDICINE_ID\",\"dosage\":\"500mg\",\"frequency\":\"3 times a day\",\"duration\":\"5 days\",\"instructions\":\"After food\"}"
    response=$(api_call POST "/encounters/$ENCOUNTER_ID/prescriptions" "$rx_data" "$TOKEN_DOCTOR" 201)
    if [[ $? -eq 0 ]]; then
        PRESCRIPTION_ID=$(echo "$response" | jq -r '.id')
        pass "Create prescription (id: $PRESCRIPTION_ID)"
    else
        fail "Create prescription" "response: $(echo "$response" | head -c 150)"
        return
    fi

    local dispense_data="{\"patientId\":\"$PATIENT_ID\",\"prescriptionId\":\"$PRESCRIPTION_ID\",\"quantity\":5,\"notes\":\"e2e dispense\"}"
    response=$(api_call POST "/pharmacy/dispense" "$dispense_data" "$TOKEN_PHARMACY" 200 "Idempotency-Key: e2e-disp-$(date +%s)-$RANDOM")
    [[ $? -eq 0 ]] && pass "FEFO dispense (5 units, returns 200)" || fail "FEFO dispense" "response: $(echo "$response" | head -c 120)"

    # Second patient: fresh encounter + Rx + dispense, so the idempotency test
    # has its own patient with an unbilled charge (patient 1's are invoiced later).
    local p2 response2
    p2=$(api_call GET "/patients/uhid/UHID000002" "" "$TOKEN_ADMIN" 200)
    if [[ $? -eq 0 ]]; then
        PATIENT2_ID=$(echo "$p2" | jq -r '.id')
        pass "Get second patient by UHID (id: $PATIENT2_ID)"
        response2=$(api_call POST "/encounters" "{\"patientId\":\"$PATIENT2_ID\",\"vitals\":{\"pulse\":80},\"chiefComplaint\":\"Cough\",\"diagnosis\":\"Bronchitis\"}" "$TOKEN_DOCTOR" 201)
        if [[ $? -eq 0 ]]; then
            ENCOUNTER2_ID=$(echo "$response2" | jq -r '.id')
            pass "Create encounter for second patient"
            local rx2="{\"encounterId\":\"$ENCOUNTER2_ID\",\"patientId\":\"$PATIENT2_ID\",\"medicineId\":\"$MEDICINE_ID\",\"dosage\":\"500mg\",\"frequency\":\"2 times a day\",\"duration\":\"7 days\"}"
            response2=$(api_call POST "/encounters/$ENCOUNTER2_ID/prescriptions" "$rx2" "$TOKEN_DOCTOR" 201)
            local rx2_id
            rx2_id=$(echo "$response2" | jq -r '.id // empty')
            if [[ -n "$rx2_id" && "$rx2_id" != "null" ]]; then
                response2=$(api_call POST "/pharmacy/dispense" "{\"patientId\":\"$PATIENT2_ID\",\"prescriptionId\":\"$rx2_id\",\"quantity\":3}" "$TOKEN_PHARMACY" 200 "Idempotency-Key: e2e-disp2-$(date +%s)-$RANDOM")
                [[ $? -eq 0 ]] && pass "FEFO dispense for second patient" || fail "FEFO dispense for second patient"
            else
                skip "Second patient dispense (prescription failed)"
            fi
        else
            skip "Second patient Rx chain (encounter failed)"
        fi
    else
        skip "Second patient chain (UHID000002 lookup failed)"
    fi

    # Sign LAST: a signed encounter is a closed clinical record, so no more
    # prescriptions/dispenses can hang off it.
    [[ -n "$ENCOUNTER_ID" ]] && api_call POST "/encounters/$ENCOUNTER_ID/sign" "" "$TOKEN_DOCTOR" 200 >/dev/null \
        && pass "Sign encounter (closed after Rx/dispense)" || fail "Sign encounter"
}
test_admissions() {
    log_test "Admission APIs"
    local response room_id

    response=$(api_call GET "/admissions/rooms/available" "" "$TOKEN_DOCTOR" 200)
    [[ $? -eq 0 ]] && pass "Get available rooms ($(echo "$response" | jq 'length') found)" || fail "Get available rooms"

    response=$(api_call GET "/admissions/rooms" "" "$TOKEN_DOCTOR" 200)
    [[ $? -eq 0 ]] && pass "Get all rooms" || fail "Get all rooms"

    room_id=$(api_call GET "/admissions/rooms/available" "" "$TOKEN_DOCTOR" 200 | jq -r '.[0].id // empty')
    if [[ -z "$room_id" || "$room_id" == "null" ]]; then
        skip "Admission creation - no rooms seeded"; return
    fi

    local admission_data="{\"patientId\":\"$PATIENT_ID\",\"roomId\":\"$room_id\",\"doctorId\":null}"
    response=$(api_call POST "/admissions" "$admission_data" "$TOKEN_DOCTOR" 201)
    if [[ $? -eq 0 ]]; then
        ADMISSION_ID=$(echo "$response" | jq -r '.id')
        pass "Create admission (id: $ADMISSION_ID)"
    else fail "Create admission" "response: $(echo "$response" | head -c 150)"; fi

    api_call GET "/admissions/active" "" "$TOKEN_DOCTOR" 200 >/dev/null \
        && pass "Get active admissions" || fail "Get active admissions"

    api_call GET "/admissions/patient/$PATIENT_ID" "" "$TOKEN_DOCTOR" 200 >/dev/null \
        && pass "Get patient admission history" || fail "Get patient admission history"

    response=$(api_call PUT "/admissions/$ADMISSION_ID/discharge" '{"dischargeDiagnosis":"Recovered","notes":"Stable at discharge"}' "$TOKEN_DOCTOR" 200)
    [[ $? -eq 0 ]] && pass "Discharge patient (room charges auto-posted)" || fail "Discharge patient"
}

test_billing() {
    log_test "Billing APIs"
    local response

    response=$(api_call GET "/billing/patients/$PATIENT_ID/unbilled" "" "$TOKEN_BILLING" 200)
    [[ $? -eq 0 ]] && pass "Get unbilled charges ($(echo "$response" | jq 'length') found)" || fail "Get unbilled charges"

    local idem_key="e2e-inv-$(date +%s)-$RANDOM"
    response=$(api_call POST "/billing/invoices" "{\"patientId\":\"$PATIENT_ID\",\"notes\":\"e2e\"}" "$TOKEN_BILLING" 201 "Idempotency-Key: $idem_key")
    if [[ $? -eq 0 ]]; then
        INVOICE_ID=$(echo "$response" | jq -r '.id')
        pass "Generate invoice (id: $INVOICE_ID)"
    else
        # No unbilled charges is a legitimate business rejection (409)
        fail "Generate invoice" "response: $(echo "$response" | head -c 150)"
    fi

    api_call GET "/billing/patients/$PATIENT_ID/invoices" "" "$TOKEN_BILLING" 200 >/dev/null \
        && pass "Get patient invoices" || fail "Get patient invoices"

    [[ -z "$INVOICE_ID" || "$INVOICE_ID" == "null" ]] && { skip "Remaining billing tests (no invoice)"; return; }

    api_call GET "/billing/invoices/$INVOICE_ID/charges" "" "$TOKEN_BILLING" 200 >/dev/null \
        && pass "Get invoice charges" || fail "Get invoice charges"

    local total
    total=$(api_call GET "/billing/patients/$PATIENT_ID/invoices" "" "$TOKEN_BILLING" 200 | jq -r --arg id "$INVOICE_ID" '.[] | select(.id==$id) | .totalAmount // 0')
    local payment_data="{\"invoiceId\":\"$INVOICE_ID\",\"amount\":$total,\"paymentMethod\":\"cash\",\"transactionRef\":\"E2E-PAY-001\"}"
    response=$(api_call POST "/billing/payments" "$payment_data" "$TOKEN_BILLING" 201 "Idempotency-Key: e2e-pay-$(date +%s)-$RANDOM")
    [[ $? -eq 0 ]] && pass "Record payment (amount=$total)" || fail "Record payment" "response: $(echo "$response" | head -c 150)"

    api_call GET "/billing/invoices/$INVOICE_ID/payments" "" "$TOKEN_BILLING" 200 >/dev/null \
        && pass "Get invoice payments" || fail "Get invoice payments"

    # Idempotency filter runs BEFORE authorization, so a key is required even to get 403
    response=$(api_call POST "/billing/invoices" "{\"patientId\":\"$PATIENT_ID\"}" "$TOKEN_DOCTOR" 403 "Idempotency-Key: e2e-rbac-$(date +%s)-$RANDOM") \
        && pass "RBAC: Doctor cannot generate invoice (403)" || fail "RBAC: Doctor cannot generate invoice"
}

test_dashboard() {
    log_test "Dashboard and Notification APIs"
    local response
    response=$(api_call GET "/dashboard" "" "$TOKEN_ADMIN" 200)
    [[ $? -eq 0 ]] && pass "Get dashboard data" || fail "Get dashboard data" "$(echo "$response" | head -c 120)"
    api_call GET "/notifications?limit=10" "" "$TOKEN_ADMIN" 200 >/dev/null && pass "Get notifications" || fail "Get notifications"
    api_call GET "/notifications/unread-count" "" "$TOKEN_ADMIN" 200 >/dev/null && pass "Get unread count" || fail "Get unread count"
    api_call PUT "/notifications/00000000-0000-0000-0000-000000000000/read" "" "$TOKEN_ADMIN" 200 >/dev/null \
        && pass "Mark read: unknown notification is a no-op (200)" || fail "Mark read: unknown notification"
    response=$(api_call GET "/users/me" "" "$TOKEN_ADMIN" 200)
    [[ $? -eq 0 ]] && pass "Get current user ($(echo "$response" | jq -r '.username'))" || fail "Get current user"
}

test_users() {
    log_test "User Management APIs"
    local response
    response=$(api_call GET "/users" "" "$TOKEN_ADMIN" 200)
    [[ $? -eq 0 ]] && pass "List users ($(echo "$response" | jq 'length') found)" || fail "List users"
    response=$(api_call GET "/users?role=doctor" "" "$TOKEN_ADMIN" 200)
    [[ $? -eq 0 ]] && pass "Filter users by role ($(echo "$response" | jq 'length') doctors)" || fail "Filter users by role"
    response=$(api_call GET "/users" "" "$TOKEN_DOCTOR" 403) \
        && pass "RBAC: Doctor cannot list users (403)" || fail "RBAC: Doctor cannot list users"
}

test_negative_scenarios() {
    log_test "Negative Scenario Tests"
    local response status body

    response=$(api_call GET "/patients/00000000-0000-0000-0000-000000000000" "" "$TOKEN_ADMIN" 404) \
        && pass "404 for non-existent patient" || fail "404 for non-existent patient"

    status=$(status_only POST "/patients" "invalid json" "$TOKEN_RECEPTION"); status="${status%%|*}"
    [[ "$status" == "400" ]] && pass "400 for malformed JSON" || fail "400 for malformed JSON" "got $status"

    response=$(api_call POST "/patients" "{}" "$TOKEN_RECEPTION" 400) \
        && pass "400 for validation error (missing fields)" || fail "400 for validation error"

    api_call GET "/patients" "" "invalid-token" 401 >/dev/null \
        && pass "401 for invalid token" || fail "401 for invalid token"

    # Idempotency contract: missing header on configured endpoint -> 400
    status=$(status_only POST "/billing/payments" '{}' "$TOKEN_BILLING"); status="${status%%|*}"
    [[ "$status" == "400" ]] && pass "400 when Idempotency-Key header missing" || fail "400 when Idempotency-Key header missing" "got $status"
}

test_idempotency() {
    log_test "Idempotency Tests"
    [[ -z "$PATIENT2_ID" ]] && { skip "Idempotency tests - no second patient"; return; }

    local idem_key="e2e-idem-$(date +%s)-$RANDOM"
    local invoice_data="{\"patientId\":\"$PATIENT2_ID\",\"notes\":\"idempotency test\"}"

    # First POST -> 201 created
    local r1 s1 body1
    r1=$(curl -s -w $'\n%{http_code}' -X POST "$API_URL/billing/invoices" \
        -H "Authorization: Bearer $TOKEN_BILLING" -H "Content-Type: application/json" \
        -H "Idempotency-Key: $idem_key" -d "$invoice_data" 2>/dev/null)
    s1=$(echo "$r1" | tail -n1); body1=$(echo "$r1" | sed '$d')
    [[ "$s1" == "201" ]] && pass "Invoice with Idempotency-Key -> 201" || fail "Invoice with Idempotency-Key" "status=$s1 body=$(echo "$body1" | head -c 120)"

    # Replay with SAME key -> 200 + Idempotency-Key-Replayed: true (never re-executed)
    local r2 s2 hdr2
    r2=$(curl -s -D /tmp/e2e-headers.txt -o /tmp/e2e-body.txt -w "%{http_code}" -X POST "$API_URL/billing/invoices" \
        -H "Authorization: Bearer $TOKEN_BILLING" -H "Content-Type: application/json" \
        -H "Idempotency-Key: $idem_key" -d "$invoice_data" 2>/dev/null)
    s2="$r2"; hdr2=$(grep -i "Idempotency-Key-Replayed" /tmp/e2e-headers.txt | tr -d '\r' || true)
    if [[ "$s2" == "200" && "$hdr2" == *true* ]]; then
        pass "Replay with same key -> 200 + Idempotency-Key-Replayed: true"
    else
        fail "Replay with same key" "status=$s2 header=[$hdr2]"
    fi
    rm -f /tmp/e2e-headers.txt /tmp/e2e-body.txt
}

main() {
    echo "# MedOS End-to-End API Test Report" > "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "**Date:** $(date -u +"%Y-%m-%d %H:%M:%S UTC")" >> "$REPORT_FILE"
    echo "**Base URL:** $BASE_URL" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "## Test Results" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"

    log_info "Starting MedOS End-to-End API Tests (base: $BASE_URL)"
    check_prerequisites
    start_stack "${1:-}"

    test_health
    test_auth
    test_patients
    test_pharmacy
    test_encounters
    test_dispense
    test_admissions
    test_billing
    test_dashboard
    test_users
    test_negative_scenarios
    test_idempotency

    echo ""
    echo "=========================================="
    log_info "Test Summary"
    echo "=========================================="
    log_success "Passed: $TESTS_PASSED"
    log_error "Failed: $TESTS_FAILED"
    log_skip "Skipped: $TESTS_SKIPPED"

    echo "" >> "$REPORT_FILE"
    echo "## Summary" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "| Status | Count |" >> "$REPORT_FILE"
    echo "|--------|-------|" >> "$REPORT_FILE"
    echo "| Passed | $TESTS_PASSED |" >> "$REPORT_FILE"
    echo "| Failed | $TESTS_FAILED |" >> "$REPORT_FILE"
    echo "| Skipped | $TESTS_SKIPPED |" >> "$REPORT_FILE"

    [[ $TESTS_FAILED -gt 0 ]] && exit 1
    exit 0
}

main "$@"
