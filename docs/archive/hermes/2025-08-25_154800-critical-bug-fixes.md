# MedOS HMS - Phase 1 Critical Bug Fixes Plan

## Goal
Address the critical bugs identified in the MedOS HMS codebase blocking production readiness:
1. **PharmacyService God Service** - Split into bounded contexts
2. **JWT Secret Validation** - Missing validation in config
3. **Test Failures** - Propagation changes and mock issues

## Current State Assessment

### Critical Production Gaps
1. **PharmacyService (212 lines)** - Monolithic service handling prescriptions, inventory, billing, patient ops
2. **BillingService (176 lines)** - Invoice generation, payment processing, reporting
3. **JWT Secret** - Hardcoded fallback in `application-prod.yml`
4. **Test Failures** - Propagation changes masking real issues

## Proposed Approach

### Phase 1: Critical Bug Fixes (P0)

**1. PharmacyService Refactoring**
- **Problem**: 212-line monolithic service violating Single Responsibility
- **Solution**: Extract into `PrescriptionService`, `InventoryService`, `BillingService`, `PaymentService`
- **Impact**: High - affects all pharmacy operations

**2. BillingService Refactoring**  
- **Problem**: 176-line monolithic service mixing invoice, payment, reporting
- **Solution**: Extract into focused services per bounded context
- **Impact**: High - affects billing and payment flows

**3. JWT Secret Validation**
- **Problem**: Missing `JWT_SECRET` validation in production config
- **Solution**: Add validation to ensure secret is ≥32 bytes Base64
- **Impact**: Medium - security hardening

### Phase 2: Test Suite Cleanup (P1)

**4. Propagation Guard Fix**
- **Problem**: `@Transactional(propagation=...)` changes to satisfy tests can break production
- **Solution**: Verify all callers have their own `@Transactional` before changing propagation
- **Impact**: Medium - prevents production bugs

**5. Mockito Strict Stubbing**
- **Problem**: `UnnecessaryStubbingException` from shared helper stubs
- **Solution**: Use `lenient()` for error-path stubs that may never execute
- **Impact**: Low - test stability

## Files to Modify

### Service Refactoring
- ✅ `backend/src/main/java/com/medos/modules/pharmacy/service/PharmacyService.java`
- ✅ `backend/src/main/java/com/medos/modules/billing/service/BillingService.java`
- ✅ `backend/src/main/java/com/medos/modules/pharmacy/controller/PharmacyController.java`
- ✅ `backend/src/main/java/com/medos/modules/billing/controller/BillingController.java`

### New Service Files
- ✅ `backend/src/main/java/com/medos/modules/pharmacy/service/PrescriptionService.java`
- ✅ `backend/src/main/java/com/medos/modules/pharmacy/service/InventoryService.java`
- ✅ `backend/src/main/java/com/medos/modules/billing/service/BillingService.java`
- ✅ `backend/src/main/java/com/medos/modules/payment/service/PaymentService.java`

### Configuration
- ✅ `backend/src/main/resources/application-prod.yml`
- ✅ `backend/src/main/resources/application-dev.yml`

### Test Updates
- ✅ `backend/src/test/java/com/medos/service/PharmacyServiceTest.java`
- ✅ `backend/src/test/java/com/medos/service/BillingServiceTest.java`

## Validation Steps

### Unit Testing
1. All existing service tests pass (33/33)
2. New extracted service tests added
3. Integration tests verify end-to-end flows

### Production Readiness
1. JWT secret validation implemented
2. Each service follows bounded context principles
3. Cross-service dependencies are explicit

## Estimated Effort
- **Phase 1 (Critical)**: 3-4 days
- **Phase 2 (Tests)**: 1-2 days

## Risk Mitigation
1. Extract gradually with facade pattern
2. Maintain backward compatibility
3. Comprehensive testing at each step
4. Verify propagation changes don't break production