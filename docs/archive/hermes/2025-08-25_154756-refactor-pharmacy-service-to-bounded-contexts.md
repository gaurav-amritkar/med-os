# MedOS HMS - PharmacyService Refactoring Plan

## Goal
Refactor the monolithic `PharmacyService` (212 lines, 7+ responsibilities) into bounded contexts as per Production Readiness Phase 1 requirements:
- `PrescriptionService` - Prescription management
- `InventoryService` - Medicine inventory and batch management
- `BillingService` - Invoice generation and billing (extracted from PharmacyService)
- `PaymentService` - Payment processing

## Current State Assessment

### PharmacyService - The "God Service"
**Location:** `backend/src/main/java/com/medos/modules/pharmacy/service/PharmacyService.java` (212 lines)

**Current Responsibilities:**
- Medicine catalog management (list/create/get)
- Medicine batch operations (add, update, list by medicine)
- Stock transactions and balancing
- Prescription processing and dispensing
- Charge generation and invoice creation
- Patient balance updates via events

**Problems:**
1. High coupling between different business domains
2. Difficult unit testing
3. Single point of failure
4. Violates Single Responsibility Principle
5. Production readiness gap: "God Services" need splitting

## Proposed Approach

### Phase 1: Extract Core Services

**1. PrescriptionService**
- Move: `processPrescription(DispenseRequest)`, `getPrescriptionDetails(UUID)`, prescription-specific logic
- Location: `backend/src/main/java/com/medos/modules/pharmacy/service/PrescriptionService.java`

**2. InventoryService** 
- Move: `createMedicine(MedicineCatalog)`, `getBatches(UUID)`, `addBatchToStock(MedicineBatch)`, stock-specific operations
- Location: `backend/src/main/java/com/medos/modules/pharmacy/service/InventoryService.java`

**3. BillingService**
- Move: `createChargeForDispense(DispenseRequest, MedicineCatalog, double quantity, LocalDateTime dispenseTime)`, `generateInvoice(Charge, Patient)`
- Location: `backend/src/main/java/com/medos/modules/billing/service/BillingService.java`

**4. PaymentService**
- Move: Payment processing logic
- Location: `backend/src/main/java/com/medos/modules/payment/service/PaymentService.java`

### Phase 2: Update Dependencies

**Controller Updates:**
- `PharmacyController` delegates to appropriate services
- Maintain backward compatibility where needed

**Service Interface Updates:**
- Keep existing `PharmacyService` as facade for existing endpoints
- New code uses extracted services directly

## Files to Modify

### Core Service Files
- ✅ `PharmacyService.java` - Refactor to use extracted services
- ✅ `PharmacyController.java` - Update to delegate appropriately
- ✅ `PrescriptionService.java` - NEW
- ✅ `InventoryService.java` - NEW  
- ✅ `BillingService.java` - NEW
- ✅ `PaymentService.java` - NEW

### Test Updates
- ✅ `PharmacyServiceTest.java` - Update to use interfaces/mocks
- ✅ Add new test files for extracted services

## Validation Steps

### Unit Testing
1. All existing PharmacyService tests pass
2. New service tests cover extracted functionality
3. Integration tests verify end-to-end flows

### Backward Compatibility
1. All existing API endpoints work unchanged
2. Controller methods delegate to appropriate services
3. Service interfaces maintain compatibility

### Production Readiness
1. Each service follows bounded context principles
2. Cross-service dependencies are explicit
3. Testing coverage for new services

## Estimated Effort
- **High Priority:** 2-3 days (Phase 1 extraction)
- **Medium Priority:** 1 day (interface updates)
- **Low Priority:** 1 day (comprehensive testing)

## Technical Notes

### Key Dependencies
- `PharmacyService` currently depends on: Repositories, EventPublisher, AuditLogger, MoneyUtil
- Extracted services should maintain minimal, focused dependencies

### Testing Strategy
- Use interface-based testing with @InjectMocks
- Separate concerns for unit testing each bounded context
- Integration tests for cross-service workflows

### Risk Mitigation
1. Extract gradually - maintain facade for existing code
2. Comprehensive testing before removing old implementation
3. Update incrementally with comprehensive validation

## Success Criteria
- Each extracted service has clear, single responsibility
- All existing functionality preserved
- Code follows SOLID principles
- Tests pass without regressions
- Production readiness gap (#2) is closed