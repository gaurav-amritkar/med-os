# MedOS Backend Blueprint

## System Overview
Spring Boot service providing the MedOS HTTP API, security, persistence integration, and clinical modules.

## Interface & Subdirectories
- [src](./src/README.md) - Component contract.

## Invariants
- Flyway owns schema evolution; Hibernate validates the mapped schema.
- Backend verification is defined by the Maven project in `backend/pom.xml`.

## Side Effects
- The service integrates with PostgreSQL, Redis, and HTTP clients through configured application components.

## System Verification
- Run: `cd backend && mvn clean test`
