#!/usr/bin/env bash
# MedOS End-to-End API Integration Test
# Tests all REST APIs through the Docker stack
#
# Usage:
#   ./tests/e2e/api-test.sh [--skip-start]
#
# Prerequisites:
#   - Docker and docker-compose installed
#   - curl, jq installed
#   - .env file configured with required variables

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="${BASE_URL:-http://localhost:8080}"
API_URL="$BASE_URL/api"
TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$TEST_DIR/../.." && pwd)"
LOG_DIR="$TEST_DIR/logs"
REPORT_FILE="$TEST_DIR/test-report.md"

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_SKIPPED=0

# Store tokens for different roles (using temp files)
TOKEN_ADMIN=""
TOKEN_DOCTOR=""
TOKEN_NURSE=""
TOKEN_RECEPTION=""
TOKEN_PHARMACY=""
TOKEN_BILLING=""

# Store created resources for cleanup
PATIENT_ID=""
ENCOUNTER_ID=""
PRESCRIPTION_ID=""
MEDICINE_ID=""
BATCH_ID=""
ADMISSION_ID=""
INVOICE_ID=""

# Logging functions
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_error() { echo -e "${RED}[FAIL]${NC} $1"; }
log_skip() { echo -e "${YELLOW}[SKIP]${NC} $1"; }
log_test() { echo -e "\n${YELLOW}=== TEST: $1 ===${NC}"; }

# Cleanup function
cleanup() {
    log_info "Cleaning up test artifacts..."
    rm -f "$TEST_DIR"/*.json 2>/dev/null || true
}

trap cleanup EXIT

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    local missing=0
    
    if ! command -v curl &> /dev/null; then
        log_error "curl is required but not installed"
        missing=1
    fi
    
    if ! command -v jq &> /dev/null; then
        log_error "jq is required but not installed"
        missing=1
    fi
    
    if ! command -v docker &> /dev/null; then
        log_error "docker is required but not installed"
        missing=1
    fi
    
    if [[ ! -f "$PROJECT_ROOT/.env" ]]; then
        log_error ".env file not found. Copy .env.example to .env and configure it."
        missing=1
    fi
    
    if [[ $missing -eq 1 ]]; then
        exit 1
    fi
    
    log_success "All prerequisites met"
}

# Start Docker stack
start_stack() {
    if [[ "${1:-}" == "--skip-start" ]]; then
        log_info "Skipping stack start (--skip-start flag provided)"
        return 0
    fi
    
    log_info "Starting MedOS Docker stack..."
    cd "$PROJECT_ROOT"
    
    # Stop any existing containers
    docker compose down --remove-orphans 2>/dev/null || true
    
    # Start the stack
    docker compose up -d --build
    
    log_info "Waiting for services to be healthy..."
    local max_wait=300
    local waited=0
    local interval=10
    
    while [[ $waited -lt $max_wait ]]; do
        if curl -sf "$BASE_URL/manage/health" > /dev/null 2>&1; then
            log_success "Backend is healthy"
            break
        fi
        log_info "Waiting for backend... (${waited}s/${max_wait}s)"
        sleep $interval
        waited=$((waited + interval))
    done
    
    if [[ $waited -ge $max_wait ]]; then
        log_error "Backend did not become healthy within ${max_wait}s"
        docker compose logs backend --tail=50
        exit 1
    fi
    
    # Seed test data
    log_info "Seeding test data..."
    "$PROJECT_ROOT/tools/seed-dev.sh" 2>/dev/null || log_info "Seed may have already run"
    
    log_success "Docker stack is ready"
}

# Make API request
api_call() {
    local method="$1"
    local endpoint="$2"
    local data="${3:-}"
    local token="${4:-}"
    local expected_status="${5:-200}"
    
    local args=(-s -w "\n%{http_code}" -X "$method" "$API_URL$endpoint")
    
    if [[ -n "$token" ]]; then
        args+=(-H "Authorization: Bearer $token")
    fi
    
    if [[ -n "$data" ]]; then
        args+=(-H "Content-Type: application/json" -d "$data")
    fi
    
    local response
    response=$(curl "${args[@]}" 2>/dev/null)
    
    local body
    local status
    body=$(echo "$response" | sed '$d')
    status=$(echo "$response" | tail -n1)
    
    echo "$body"
    
    if [[ "$status" != "$expected_status" ]]; then
        return 1
    fi
    
    return 0
}

# Test result recording
record_test() {
    local name="$1"
    local result="$2"
    local details="${3:-}"
    
    echo "- [$result] $name" >> "$REPORT_FILE"
    if [[ -n "$details" ]]; then
        echo "  - $details" >> "$REPORT_FILE"
    fi
}

# ============================================================================
# AUTH TESTS
# ============================================================================

test_auth() {
    log_test "Authentication APIs"
    
    # Test login for each role
    local roles="admin doctor nurse reception pharmacy billing"
    
    for role in $roles; do
        local response
        response=$(api_call POST "/auth/login" "{\"username\":\"$role\",\"password\":\"password\"}" "" 200)
        
        if [[ $? -eq 0 ]]; then
            local token
            token=$(echo "$response" | jq -r '.token // empty')
            
            if [[ -n "$token" ]]; then
                # Store token in appropriate variable
                case "$role" in
                    admin) TOKEN_ADMIN="$token" ;;
                    doctor) TOKEN_DOCTOR="$token" ;;
                    nurse) TOKEN_NURSE="$token" ;;
                    reception) TOKEN_RECEPTION="$token" ;;
                    pharmacy) TOKEN_PHARMACY="$token" ;;
                    billing) TOKEN_BILLING="$token" ;;
                esac
                log_success "Login as $role"
                record_test "Login as $role" "PASS"
                ((TESTS_PASSED++))
            else
                log_error "Login as $role - no token returned"
                record_test "Login as $role" "FAIL" "No token in response"
                ((TESTS_FAILED++))
            fi
        else
            log_error "Login as $role"
            record_test "Login as $role" "FAIL"
            ((TESTS_FAILED++))
        fi
    done
    
    # Test invalid login
    local response
    response=$(api_call POST "/auth/login" '{"username":"invalid","password":"wrong"}' "" 401)
    
    if [[ $? -eq 0 ]]; then
        log_success "Invalid login rejected (401)"
        record_test "Invalid login rejected" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Invalid login - unexpected response"
        record_test "Invalid login rejected" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Test missing auth on protected endpoint
    response=$(api_call GET "/patients" "" "" 401)
    
    if [[ $? -eq 0 ]]; then
        log_success "Unauthenticated request rejected (401)"
        record_test "Unauthenticated request rejected" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Unauthenticated request - unexpected response"
        record_test "Unauthenticated request rejected" "FAIL"
        ((TESTS_FAILED++))
    fi
}

# ============================================================================
# PATIENT TESTS
# ============================================================================

test_patients() {
    log_test "Patient APIs"
    local token="$TOKEN_ADMIN"
    local reception_token="$TOKEN_RECEPTION"
    
    # List patients (all authenticated)
    local response
    response=$(api_call GET "/patients" "" "$token" 200)
    
    if [[ $? -eq 0 ]]; then
        local count
        count=$(echo "$response" | jq '.content | length')
        log_success "List patients ($count found)"
        record_test "List patients" "PASS" "Found $count patients"
        ((TESTS_PASSED++))
    else
        log_error "List patients"
        record_test "List patients" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Get patient by UHID
    response=$(api_call GET "/patients/uhid/UHID000001" "" "$token" 200)
    
    if [[ $? -eq 0 ]]; then
        PATIENT_ID=$(echo "$response" | jq -r '.id')
        log_success "Get patient by UHID (id: $PATIENT_ID)"
        record_test "Get patient by UHID" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Get patient by UHID"
        record_test "Get patient by UHID" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Get patient by ID
    if [[ -n "$PATIENT_ID" ]]; then
        response=$(api_call GET "/patients/$PATIENT_ID" "" "$token" 200)
        
        if [[ $? -eq 0 ]]; then
            log_success "Get patient by ID"
            record_test "Get patient by ID" "PASS"
            ((TESTS_PASSED++))
        else
            log_error "Get patient by ID"
            record_test "Get patient by ID" "FAIL"
            ((TESTS_FAILED++))
        fi
    fi
    
    # Create new patient (receptionist only)
    local new_patient='{
        "name": "Test Patient",
        "age": 30,
        "gender": "male",
        "phone": "9999999999",
        "email": "test@example.com",
        "bloodGroup": "O+",
        "address": "Test Address"
    }'
    
    response=$(api_call POST "/patients" "$new_patient" "$reception_token" 201)
    
    if [[ $? -eq 0 ]]; then
        local new_patient_id
        new_patient_id=$(echo "$response" | jq -r '.id')
        log_success "Create new patient (id: $new_patient_id)"
        record_test "Create patient" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Create new patient"
        record_test "Create patient" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Test RBAC - receptionist can create, doctor cannot
    response=$(api_call POST "/patients" "$new_patient" "$TOKEN_DOCTOR" "" 403)
    
    if [[ $? -eq 0 ]]; then
        log_success "RBAC: Doctor cannot create patient (403)"
        record_test "RBAC - patient creation" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "RBAC: Doctor patient creation check"
        record_test "RBAC - patient creation" "FAIL"
        ((TESTS_FAILED++))
    fi
}

# ============================================================================
# ENCOUNTER TESTS
# ============================================================================

test_encounters() {
    log_test "Encounter APIs"
    local doctor_token="$TOKEN_DOCTOR"
    local nurse_token="$TOKEN_NURSE"
    
    if [[ -z "$PATIENT_ID" ]]; then
        log_skip "Encounter tests - no patient ID available"
        return
    fi
    
    # Create encounter (doctor/nurse only)
    local encounter_data="{
        \"patientId\": \"$PATIENT_ID\",
        \"vitals\": {
            \"bloodPressure\": \"120/80\",
            \"pulse\": 72,
            \"temperature\": 98.6,
            \"weight\": 70.5,
            \"height\": 175
        },
        \"chiefComplaint\": \"Headache and fever\",
        \"diagnosis\": \"Viral fever\",
        \"notes\": \"Patient presenting with mild symptoms\"
    }"
    
    local response
    response=$(api_call POST "/encounters" "$encounter_data" "$doctor_token" 201)
    
    if [[ $? -eq 0 ]]; then
        ENCOUNTER_ID=$(echo "$response" | jq -r '.id')
        log_success "Create encounter (id: $ENCOUNTER_ID)"
        record_test "Create encounter" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Create encounter"
        record_test "Create encounter" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Get encounter by ID
    if [[ -n "$ENCOUNTER_ID" ]]; then
        response=$(api_call GET "/encounters/$ENCOUNTER_ID" "" "$doctor_token" 200)
        
        if [[ $? -eq 0 ]]; then
            log_success "Get encounter by ID"
            record_test "Get encounter by ID" "PASS"
            ((TESTS_PASSED++))
        else
            log_error "Get encounter by ID"
            record_test "Get encounter by ID" "FAIL"
            ((TESTS_FAILED++))
        fi
        
        # List encounters by patient
        response=$(api_call GET "/encounters/patient/$PATIENT_ID?page=0&size=10" "" "$doctor_token" 200)
        
        if [[ $? -eq 0 ]]; then
            local count
            count=$(echo "$response" | jq '.content | length')
            log_success "List encounters by patient ($count found)"
            record_test "List encounters by patient" "PASS"
            ((TESTS_PASSED++))
        else
            log_error "List encounters by patient"
            record_test "List encounters by patient" "FAIL"
            ((TESTS_FAILED++))
        fi
        
        # Sign encounter (doctor only)
        response=$(api_call POST "/encounters/$ENCOUNTER_ID/sign" "" "$doctor_token" 200)
        
        if [[ $? -eq 0 ]]; then
            log_success "Sign encounter"
            record_test "Sign encounter" "PASS"
            ((TESTS_PASSED++))
        else
            log_error "Sign encounter"
            record_test "Sign encounter" "FAIL"
            ((TESTS_FAILED++))
        fi
    fi
    
    # AI medicine suggestion (doctor only)
    local suggest_data='{"keywords": ["fever", "headache"]}'
    response=$(api_call POST "/encounters/suggest-medicines" "$suggest_data" "$doctor_token" 200)
    
    if [[ $? -eq 0 ]]; then
        log_success "AI medicine suggestion"
        record_test "AI medicine suggestion" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "AI medicine suggestion"
        record_test "AI medicine suggestion" "FAIL"
        ((TESTS_FAILED++))
    fi
}

# ============================================================================
# PHARMACY TESTS
# ============================================================================

test_pharmacy() {
    log_test "Pharmacy APIs"
    local pharmacy_token="$TOKEN_PHARMACY"
    local doctor_token="$TOKEN_DOCTOR"
    
    # List medicines
    local response
    response=$(api_call GET "/pharmacy/medicines" "" "$pharmacy_token" 200)
    
    if [[ $? -eq 0 ]]; then
        local count
        count=$(echo "$response" | jq 'length')
        log_success "List medicines ($count found)"
        record_test "List medicines" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "List medicines"
        record_test "List medicines" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Create medicine (pharmacist only)
    local medicine_data='{
        "name": "Test Paracetamol",
        "genericName": "Paracetamol",
        "form": "Tablet",
        "strength": "500mg",
        "unitPrice": 10.00,
        "reorderLevel": 100,
        "keywords": ["fever", "pain", "headache"]
    }'
    
    response=$(api_call POST "/pharmacy/medicines" "$medicine_data" "$pharmacy_token" 201)
    
    if [[ $? -eq 0 ]]; then
        MEDICINE_ID=$(echo "$response" | jq -r '.id')
        log_success "Create medicine (id: $MEDICINE_ID)"
        record_test "Create medicine" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Create medicine"
        record_test "Create medicine" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Stock in (pharmacist only)
    if [[ -n "$MEDICINE_ID" ]]; then
        local stock_data="{
            \"medicineId\": \"$MEDICINE_ID\",
            \"batchNumber\": \"BATCH001\",
            \"expiryDate\": \"2027-12-31\",
            \"quantity\": 100,
            \"purchasePrice\": 5.00
        }"
        
        response=$(api_call POST "/pharmacy/medicines/$MEDICINE_ID/stock-in" "$stock_data" "$pharmacy_token" 201)
        
        if [[ $? -eq 0 ]]; then
            BATCH_ID=$(echo "$response" | jq -r '.id')
            log_success "Stock in (batch: $BATCH_ID)"
            record_test "Stock in" "PASS"
            ((TESTS_PASSED++))
        else
            log_error "Stock in"
            record_test "Stock in" "FAIL"
            ((TESTS_FAILED++))
        fi
        
        # Get batches for medicine
        response=$(api_call GET "/pharmacy/medicines/$MEDICINE_ID/batches" "" "$pharmacy_token" 200)
        
        if [[ $? -eq 0 ]]; then
            log_success "Get medicine batches"
            record_test "Get medicine batches" "PASS"
            ((TESTS_PASSED++))
        else
            log_error "Get medicine batches"
            record_test "Get medicine batches" "FAIL"
            ((TESTS_FAILED++))
        fi
    fi
    
    # Test stock transactions
    response=$(api_call GET "/pharmacy/transactions?limit=10" "" "$pharmacy_token" 200)
    
    if [[ $? -eq 0 ]]; then
        log_success "Get stock transactions"
        record_test "Get stock transactions" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Get stock transactions"
        record_test "Get stock transactions" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Test RBAC - doctor cannot create medicine
    response=$(api_call POST "/pharmacy/medicines" "$medicine_data" "$doctor_token" "" 403)
    
    if [[ $? -eq 0 ]]; then
        log_success "RBAC: Doctor cannot create medicine (403)"
        record_test "RBAC - medicine creation" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "RBAC: Doctor medicine creation check"
        record_test "RBAC - medicine creation" "FAIL"
        ((TESTS_FAILED++))
    fi
}

# ============================================================================
# ADMISSION TESTS
# ============================================================================

test_admissions() {
    log_test "Admission APIs"
    local doctor_token="$TOKEN_DOCTOR"
    
    if [[ -z "$PATIENT_ID" ]]; then
        log_skip "Admission tests - no patient ID available"
        return
    fi
    
    # Get available rooms
    local response
    response=$(api_call GET "/admissions/rooms/available" "" "$doctor_token" 200)
    
    if [[ $? -eq 0 ]]; then
        local count
        count=$(echo "$response" | jq 'length')
        log_success "Get available rooms ($count found)"
        record_test "Get available rooms" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Get available rooms"
        record_test "Get available rooms" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Get all rooms
    response=$(api_call GET "/admissions/rooms" "" "$doctor_token" 200)
    
    if [[ $? -eq 0 ]]; then
        local count
        count=$(echo "$response" | jq 'length')
        log_success "Get all rooms ($count found)"
        record_test "Get all rooms" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Get all rooms"
        record_test "Get all rooms" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Find an available room for admission
    local room_id
    room_id=$(api_call GET "/admissions/rooms/available" "" "$doctor_token" 200 | jq -r '.[0].id // empty')
    
    if [[ -n "$room_id" && "$room_id" != "null" ]]; then
        # Create admission (doctor/nurse only)
        local admission_data="{
            \"patientId\": \"$PATIENT_ID\",
            \"roomId\": \"$room_id\",
            \"admissionType\": \"routine\",
            \"notes\": \"Test admission for observation\"
        }"
        
        response=$(api_call POST "/admissions" "$admission_data" "$doctor_token" 201)
        
        if [[ $? -eq 0 ]]; then
            ADMISSION_ID=$(echo "$response" | jq -r '.id')
            log_success "Create admission (id: $ADMISSION_ID)"
            record_test "Create admission" "PASS"
            ((TESTS_PASSED++))
        else
            log_error "Create admission"
            record_test "Create admission" "FAIL"
            ((TESTS_FAILED++))
        fi
        
        # Get active admissions
        response=$(api_call GET "/admissions/active" "" "$doctor_token" 200)
        
        if [[ $? -eq 0 ]]; then
            log_success "Get active admissions"
            record_test "Get active admissions" "PASS"
            ((TESTS_PASSED++))
        else
            log_error "Get active admissions"
            record_test "Get active admissions" "FAIL"
            ((TESTS_FAILED++))
        fi
        
        # Get patient admission history
        response=$(api_call GET "/admissions/patient/$PATIENT_ID" "" "$doctor_token" 200)
        
        if [[ $? -eq 0 ]]; then
            log_success "Get patient admission history"
            record_test "Get patient admission history" "PASS"
            ((TESTS_PASSED++))
        else
            log_error "Get patient admission history"
            record_test "Get patient admission history" "FAIL"
            ((TESTS_FAILED++))
        fi
        
        # Discharge patient
        if [[ -n "$ADMISSION_ID" ]]; then
            local discharge_data='{
                "dischargeNotes": "Patient recovered well",
                "dischargeSummary": "Patient discharged in stable condition"
            }'
            
            response=$(api_call PUT "/admissions/$ADMISSION_ID/discharge" "$discharge_data" "$doctor_token" 200)
            
            if [[ $? -eq 0 ]]; then
                log_success "Discharge patient"
                record_test "Discharge patient" "PASS"
                ((TESTS_PASSED++))
            else
                log_error "Discharge patient"
                record_test "Discharge patient" "FAIL"
                ((TESTS_FAILED++))
            fi
        fi
    else
        log_skip "Admission creation - no available rooms"
        ((TESTS_SKIPPED++))
    fi
}

# ============================================================================
# BILLING TESTS
# ============================================================================

test_billing() {
    log_test "Billing APIs"
    local billing_token="$TOKEN_BILLING"
    local admin_token="$TOKEN_ADMIN"
    
    if [[ -z "$PATIENT_ID" ]]; then
        log_skip "Billing tests - no patient ID available"
        return
    fi
    
    # Get unbilled charges
    local response
    response=$(api_call GET "/billing/patients/$PATIENT_ID/unbilled" "" "$billing_token" 200)
    
    if [[ $? -eq 0 ]]; then
        local count
        count=$(echo "$response" | jq 'length')
        log_success "Get unbilled charges ($count found)"
        record_test "Get unbilled charges" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Get unbilled charges"
        record_test "Get unbilled charges" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Generate invoice (billing only) - requires idempotency key
    local idempotency_key
    idempotency_key="test-$(date +%s)"
    
    local invoice_data="{
        \"patientId\": \"$PATIENT_ID\",
        \"dueDate\": \"2026-09-27\"
    }"
    
    # Use custom curl to include idempotency key header
    response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/billing/invoices" \
        -H "Authorization: Bearer $billing_token" \
        -H "Content-Type: application/json" \
        -H "Idempotency-Key: $idempotency_key" \
        -d "$invoice_data" 2>/dev/null)
    
    local status
    status=$(echo "$response" | tail -n1)
    response=$(echo "$response" | sed '$d')
    
    if [[ "$status" == "201" ]]; then
        INVOICE_ID=$(echo "$response" | jq -r '.id // empty')
        log_success "Generate invoice (id: $INVOICE_ID)"
        record_test "Generate invoice" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Generate invoice (status: $status)"
        record_test "Generate invoice" "FAIL" "Status: $status"
        ((TESTS_FAILED++))
    fi
    
    # Get patient invoices
    response=$(api_call GET "/billing/patients/$PATIENT_ID/invoices" "" "$billing_token" 200)
    
    if [[ $? -eq 0 ]]; then
        log_success "Get patient invoices"
        record_test "Get patient invoices" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Get patient invoices"
        record_test "Get patient invoices" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Get invoice charges
    if [[ -n "$INVOICE_ID" ]]; then
        response=$(api_call GET "/billing/invoices/$INVOICE_ID/charges" "" "$billing_token" 200)
        
        if [[ $? -eq 0 ]]; then
            log_success "Get invoice charges"
            record_test "Get invoice charges" "PASS"
            ((TESTS_PASSED++))
        else
            log_error "Get invoice charges"
            record_test "Get invoice charges" "FAIL"
            ((TESTS_FAILED++))
        fi
        
        # Record payment (billing only) - requires idempotency key
        local payment_idempotency_key
        payment_idempotency_key="payment-$(date +%s)"
        
        local payment_data="{
            \"invoiceId\": \"$INVOICE_ID\",
            \"amount\": 100.00,
            \"method\": \"cash\",
            \"reference\": \"TEST-PAYMENT-001\"
        }"
        
        response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/billing/payments" \
            -H "Authorization: Bearer $billing_token" \
            -H "Content-Type: application/json" \
            -H "Idempotency-Key: $payment_idempotency_key" \
            -d "$payment_data" 2>/dev/null)
        
        status=$(echo "$response" | tail -n1)
        response=$(echo "$response" | sed '$d')
        
        if [[ "$status" == "201" ]]; then
            log_success "Record payment"
            record_test "Record payment" "PASS"
            ((TESTS_PASSED++))
        else
            log_error "Record payment (status: $status)"
            record_test "Record payment" "FAIL" "Status: $status"
            ((TESTS_FAILED++))
        fi
        
        # Get invoice payments
        response=$(api_call GET "/billing/invoices/$INVOICE_ID/payments" "" "$billing_token" 200)
        
        if [[ $? -eq 0 ]]; then
            log_success "Get invoice payments"
            record_test "Get invoice payments" "PASS"
            ((TESTS_PASSED++))
        else
            log_error "Get invoice payments"
            record_test "Get invoice payments" "FAIL"
            ((TESTS_FAILED++))
        fi
    fi
    
    # Test RBAC - doctor cannot generate invoice
    response=$(api_call POST "/billing/invoices" "$invoice_data" "$TOKEN_DOCTOR" "" 403)
    
    if [[ $? -eq 0 ]]; then
        log_success "RBAC: Doctor cannot generate invoice (403)"
        record_test "RBAC - invoice generation" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "RBAC: Doctor invoice generation check"
        record_test "RBAC - invoice generation" "FAIL"
        ((TESTS_FAILED++))
    fi
}

# ============================================================================
# DASHBOARD & NOTIFICATION TESTS
# ============================================================================

test_dashboard() {
    log_test "Dashboard and Notification APIs"
    local token="$TOKEN_ADMIN"
    
    # Get dashboard
    local response
    response=$(api_call GET "/dashboard" "" "$token" 200)
    
    if [[ $? -eq 0 ]]; then
        log_success "Get dashboard data"
        record_test "Get dashboard" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Get dashboard data"
        record_test "Get dashboard" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Get notifications
    response=$(api_call GET "/notifications?limit=10" "" "$token" 200)
    
    if [[ $? -eq 0 ]]; then
        log_success "Get notifications"
        record_test "Get notifications" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Get notifications"
        record_test "Get notifications" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Get unread count
    response=$(api_call GET "/notifications/unread-count" "" "$token" 200)
    
    if [[ $? -eq 0 ]]; then
        local count
        count=$(echo "$response" | jq -r '.unreadCount // .count // 0')
        log_success "Get unread notification count ($count)"
        record_test "Get unread count" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Get unread notification count"
        record_test "Get unread count" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Get current user
    response=$(api_call GET "/users/me" "" "$token" 200)
    
    if [[ $? -eq 0 ]]; then
        local username
        username=$(echo "$response" | jq -r '.username')
        log_success "Get current user ($username)"
        record_test "Get current user" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Get current user"
        record_test "Get current user" "FAIL"
        ((TESTS_FAILED++))
    fi
}

# ============================================================================
# USER MANAGEMENT TESTS
# ============================================================================

test_users() {
    log_test "User Management APIs"
    local admin_token="$TOKEN_ADMIN"
    local doctor_token="$TOKEN_DOCTOR"
    
    # List users (admin only)
    local response
    response=$(api_call GET "/users" "" "$admin_token" 200)
    
    if [[ $? -eq 0 ]]; then
        local count
        count=$(echo "$response" | jq 'length')
        log_success "List users ($count found)"
        record_test "List users" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "List users"
        record_test "List users" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Filter users by role
    response=$(api_call GET "/users?role=doctor" "" "$admin_token" 200)
    
    if [[ $? -eq 0 ]]; then
        local count
        count=$(echo "$response" | jq 'length')
        log_success "Filter users by role ($count doctors)"
        record_test "Filter users by role" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Filter users by role"
        record_test "Filter users by role" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Test RBAC - doctor cannot list users
    response=$(api_call GET "/users" "" "$doctor_token" "" 403)
    
    if [[ $? -eq 0 ]]; then
        log_success "RBAC: Doctor cannot list users (403)"
        record_test "RBAC - list users" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "RBAC: Doctor list users check"
        record_test "RBAC - list users" "FAIL"
        ((TESTS_FAILED++))
    fi
}

# ============================================================================
# HEALTH CHECK TESTS
# ============================================================================

test_health() {
    log_test "Health Check APIs"
    
    # Public health endpoint
    local response
    response=$(curl -sf "$BASE_URL/manage/health" 2>/dev/null)
    
    if [[ $? -eq 0 ]]; then
        log_success "Health check endpoint"
        record_test "Health check" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Health check endpoint"
        record_test "Health check" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Info endpoint (public)
    response=$(curl -sf "$BASE_URL/manage/info" 2>/dev/null)
    
    if [[ $? -eq 0 ]]; then
        log_success "Info endpoint"
        record_test "Info endpoint" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Info endpoint"
        record_test "Info endpoint" "FAIL"
        ((TESTS_FAILED++))
    fi
}

# ============================================================================
# NEGATIVE TESTS
# ============================================================================

test_negative_scenarios() {
    log_test "Negative Scenario Tests"
    local token="$TOKEN_ADMIN"
    
    # Test 404 - non-existent patient
    local response
    response=$(api_call GET "/patients/00000000-0000-0000-0000-000000000000" "" "$token" 404)
    
    if [[ $? -eq 0 ]]; then
        log_success "404 for non-existent patient"
        record_test "404 non-existent patient" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "404 non-existent patient"
        record_test "404 non-existent patient" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Test 400 - invalid JSON
    response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/patients" \
        -H "Authorization: Bearer $token" \
        -H "Content-Type: application/json" \
        -d "invalid json" 2>/dev/null)
    
    local status
    status=$(echo "$response" | tail -n1)
    
    if [[ "$status" == "400" ]]; then
        log_success "400 for malformed JSON"
        record_test "400 malformed JSON" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "400 malformed JSON (got $status)"
        record_test "400 malformed JSON" "FAIL" "Got status: $status"
        ((TESTS_FAILED++))
    fi
    
    # Test 400 - validation error (missing required fields)
    response=$(api_call POST "/patients" "{}" "$TOKEN_RECEPTION" 400)
    
    if [[ $? -eq 0 ]]; then
        log_success "400 for validation error"
        record_test "400 validation error" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "400 validation error"
        record_test "400 validation error" "FAIL"
        ((TESTS_FAILED++))
    fi
    
    # Test 401 - expired/invalid token
    response=$(api_call GET "/patients" "" "invalid-token" 401)
    
    if [[ $? -eq 0 ]]; then
        log_success "401 for invalid token"
        record_test "401 invalid token" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "401 invalid token"
        record_test "401 invalid token" "FAIL"
        ((TESTS_FAILED++))
    fi
}

# ============================================================================
# IDEMPOTENCY TESTS
# ============================================================================

test_idempotency() {
    log_test "Idempotency Tests"
    local billing_token="$TOKEN_BILLING"
    
    if [[ -z "$PATIENT_ID" ]]; then
        log_skip "Idempotency tests - no patient ID available"
        return
    fi
    
    local idempotency_key
    idempotency_key="idempotency-test-$(date +%s)"
    
    local invoice_data="{
        \"patientId\": \"$PATIENT_ID\",
        \"dueDate\": \"2026-09-27\"
    }"
    
    # First request
    local response1
    response1=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/billing/invoices" \
        -H "Authorization: Bearer $billing_token" \
        -H "Content-Type: application/json" \
        -H "Idempotency-Key: $idempotency_key" \
        -d "$invoice_data" 2>/dev/null)
    
    local status1
    status1=$(echo "$response1" | tail -n1)
    
    # Second request with same idempotency key
    local response2
    response2=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/billing/invoices" \
        -H "Authorization: Bearer $billing_token" \
        -H "Content-Type: application/json" \
        -H "Idempotency-Key: $idempotency_key" \
        -d "$invoice_data" 2>/dev/null)
    
    local status2
    status2=$(echo "$response2" | tail -n1)
    
    if [[ "$status1" == "201" && "$status2" == "201" ]]; then
        log_success "Idempotency: Same key returns cached result"
        record_test "Idempotency replay" "PASS"
        ((TESTS_PASSED++))
    else
        log_error "Idempotency: Unexpected status codes ($status1, $status2)"
        record_test "Idempotency replay" "FAIL" "Status codes: $status1, $status2"
        ((TESTS_FAILED++))
    fi
}

# ============================================================================
# MAIN EXECUTION
# ============================================================================

main() {
    mkdir -p "$LOG_DIR"
    
    # Initialize report
    echo "# MedOS End-to-End API Test Report" > "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "**Date:** $(date -u +"%Y-%m-%d %H:%M:%S UTC")" >> "$REPORT_FILE"
    echo "**Base URL:** $BASE_URL" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "## Test Results" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    
    log_info "Starting MedOS End-to-End API Tests"
    log_info "Base URL: $BASE_URL"
    
    check_prerequisites
    start_stack "$@"
    
    echo ""
    log_info "Running API tests..."
    echo ""
    
    # Run all test suites
    test_health
    test_auth
    test_patients
    test_pharmacy
    test_encounters
    test_admissions
    test_billing
    test_dashboard
    test_users
    test_negative_scenarios
    test_idempotency
    
    # Summary
    echo ""
    echo "=========================================="
    log_info "Test Summary"
    echo "=========================================="
    log_success "Passed: $TESTS_PASSED"
    log_error "Failed: $TESTS_FAILED"
    log_skip "Skipped: $TESTS_SKIPPED"
    echo ""
    
    # Add summary to report
    echo "" >> "$REPORT_FILE"
    echo "## Summary" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "| Status | Count |" >> "$REPORT_FILE"
    echo "|--------|-------|" >> "$REPORT_FILE"
    echo "| Passed | $TESTS_PASSED |" >> "$REPORT_FILE"
    echo "| Failed | $TESTS_FAILED |" >> "$REPORT_FILE"
    echo "| Skipped | $TESTS_SKIPPED |" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "---" >> "$REPORT_FILE"
    echo "Report generated at: $(date -u +"%Y-%m-%d %H:%M:%S UTC")" >> "$REPORT_FILE"
    
    log_info "Full report: $REPORT_FILE"
    
    # Exit with appropriate code
    if [[ $TESTS_FAILED -gt 0 ]]; then
        exit 1
    fi
    
    exit 0
}

main "$@"
