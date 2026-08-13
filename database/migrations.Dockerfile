FROM flyway/flyway:10.22-alpine

COPY migrations /flyway/sql
