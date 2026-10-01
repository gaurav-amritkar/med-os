# Multi-stage build for MedOS Backend
# Build context is the repository root, not backend/.
#
# The migrations are deliberately NOT copied into this image. The dedicated
# migrate container is the single owner of the schema (ADR-0005) and reads
# database/migrations directly. Packaging them onto the application classpath
# created a second copy that only existed inside the built jar — invisible to the
# on-disk drift checks, and the copy the application actually migrated from when
# it started before the migrate container had run.
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy pom.xml first for dependency caching
COPY backend/pom.xml backend/
RUN cd backend && mvn dependency:go-offline -B -q

COPY backend/src backend/src
RUN cd backend && mvn clean package -Dmaven.test.skip=true -B -q

# Runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for healthcheck
RUN apk add --no-cache curl

# Run as non-root (least privilege)
RUN addgroup -S appuser && adduser -S appuser -G appuser

# Copy entrypoint script
COPY backend/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

USER appuser

# Copy jar from builder
COPY --from=builder /app/backend/target/*.jar /app/medos-backend.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/manage/health || exit 1

ENTRYPOINT ["/entrypoint.sh"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/medos-backend.jar"]