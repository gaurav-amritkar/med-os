# Flyway migration job. Build context is the repository root so that this image
# reads database/migrations — the single owner of the schema — from the same
# location the backend application packages onto its classpath.
FROM flyway/flyway:10.22-alpine

COPY database/migrations /flyway/sql
