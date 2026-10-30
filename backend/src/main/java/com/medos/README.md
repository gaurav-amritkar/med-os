# Medos

## Overview
Backend Java component in `src/main/java/com/medos`.

## Interface
- `MedOsApplication`
- [Configuration](./config/README.md)
- [Controllers](./controller/README.md)
- [DTOs](./dto/README.md)
- [Entities](./entity/README.md)
- [Exceptions](./exception/README.md)
- [Mappers](./mapper/README.md)
- [Feature modules](./modules/README.md)
- [Repositories](./repository/README.md)
- [Security](./security/README.md)
- [Services](./service/README.md)
- [Utilities](./util/README.md)

## Invariants
- No package-wide invariant is declared directly in these source files.

## Side Effects
- `MedOsApplication.main` starts the Spring application. Imported module configurations and component scanning register request handlers and services; runtime database, Redis, HTTP, and async effects are described in their package contracts.

## Verification
- Run: `cd backend && mvn clean test`
