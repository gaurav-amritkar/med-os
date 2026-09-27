# MedOS End-to-End API Test Report

**Date:** 2026-09-27 10:50:59 UTC
**Base URL:** http://localhost:8080

## Test Results

- [PASS] Health check endpoint
- [PASS] Info endpoint
- [PASS] Login as admin
- [PASS] Login as doctor
- [PASS] Login as nurse
- [PASS] Login as reception
- [PASS] Login as pharmacy
- [PASS] Login as billing
- [PASS] Invalid login rejected (401)
- [PASS] Unauthenticated request rejected (401)
- [PASS] List patients (5 found)
- [PASS] Get patient by UHID (id: 00000000-0000-4000-8000-000000000201)
- [PASS] Get patient by ID
- [PASS] Create patient (receptionist)
- [PASS] RBAC: Doctor cannot create patient (403)
- [PASS] List medicines (0 found)
- [PASS] Create medicine (id: e5b13aad-1357-411b-a018-38e3d4afd52a)
- [PASS] Stock in (batch BATCH-E2E-1790506259, 100 units)
- [PASS] Get medicine batches
- [PASS] Get stock transactions
- [PASS] RBAC: Doctor cannot create medicine (403)
- [PASS] Create encounter (id: 95189ffa-07fa-451c-934f-d7bd6259d9e2)
- [PASS] Get encounter by ID
- [PASS] List encounters by patient (1 found)
- [PASS] AI medicine suggestion (advisor disabled, returns list)
- [PASS] Pending prescriptions (pharmacist)
- [PASS] Create prescription (id: 19a6d34b-f40c-4caf-866a-4c1f45e34d85)
- [PASS] FEFO dispense (5 units, returns 200)
- [PASS] Get second patient by UHID (id: 00000000-0000-4000-8000-000000000202)
- [PASS] Create encounter for second patient
- [PASS] FEFO dispense for second patient
- [PASS] Sign encounter (closed after Rx/dispense)
- [PASS] Get available rooms (5 found)
- [PASS] Get all rooms
- [PASS] Create admission (id: d50fa855-a5dd-4c0f-914d-89d0d0c5477b)
- [PASS] Get active admissions
- [PASS] Get patient admission history
- [PASS] Discharge patient (room charges auto-posted)
- [PASS] Get unbilled charges (2 found)
- [PASS] Generate invoice (id: 9b717509-8828-419e-aba7-3d146c14d1dd)
- [PASS] Get patient invoices
- [PASS] Get invoice charges
- [PASS] Record payment (amount=1552.50)
- [PASS] Get invoice payments
- [PASS] RBAC: Doctor cannot generate invoice (403)
- [PASS] Get dashboard data
- [PASS] Get notifications
- [PASS] Get unread count
- [PASS] Mark read: unknown notification is a no-op (200)
- [PASS] Get current user (admin)
- [PASS] List users (7 found)
- [PASS] Filter users by role (7 doctors)
- [PASS] RBAC: Doctor cannot list users (403)
- [PASS] 404 for non-existent patient
- [PASS] 400 for malformed JSON
- [PASS] 400 for validation error (missing fields)
- [PASS] 401 for invalid token
- [PASS] 400 when Idempotency-Key header missing
- [PASS] Invoice with Idempotency-Key -> 201
- [PASS] Replay with same key -> 200 + Idempotency-Key-Replayed: true

## Summary

| Status | Count |
|--------|-------|
| Passed | 60 |
| Failed | 0 |
| Skipped | 0 |
