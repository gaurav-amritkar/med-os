# Exception

## Overview
Backend Java component in `src/main/java/com/medos/exception`.

## Interface
- `ApiError`
- `BusinessException`
- `GlobalExceptionHandler`
- `ResourceNotFoundException`

## Invariants
- No package-wide invariant is declared directly in these source files.

## Side Effects
- Spring MVC controllers handle HTTP requests and return response data.

## Verification
- Run: `cd backend && mvn clean test`
