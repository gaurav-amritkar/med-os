#!/bin/sh
# Wrapper script to read secrets and set environment variables

# Read JWT secret from secret file
if [ -f /run/secrets/jwt-secret ]; then
    export JWT_SECRET=$(cat /run/secrets/jwt-secret)
fi

# Read PII encryption key from secret file
if [ -f /run/secrets/pii-encryption-key ]; then
    export PII_ENCRYPTION_KEY=$(cat /run/secrets/pii-encryption-key)
fi

# Read bootstrap admin password from secret file
if [ -f /run/secrets/bootstrap-admin-password ]; then
    export BOOTSTRAP_ADMIN_PASSWORD=$(cat /run/secrets/bootstrap-admin-password)
fi

# Read CORS origins from secret file
if [ -f /run/secrets/cors-origins ]; then
    export CORS_ORIGINS=$(cat /run/secrets/cors-origins)
fi

# Read database password from secret file
if [ -f /run/secrets/db-password ]; then
    export DB_PASSWORD=$(cat /run/secrets/db-password)
fi

# Read Redis password from secret file
if [ -f /run/secrets/redis-password ]; then
    export REDIS_PASSWORD=$(cat /run/secrets/redis-password)
fi

# Execute the original command
exec "$@"