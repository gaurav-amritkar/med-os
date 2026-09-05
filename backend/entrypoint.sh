#!/bin/sh
# Wrapper script to read secrets and set environment variables

# Read JWT secret from environment variable (preferred) or secret file (fallback)
if [ -n "${JWT_SECRET}" ]; then
    export JWT_SECRET
elif [ -f /run/secrets/jwt-secret ]; then
    export JWT_SECRET=$(cat /run/secrets/jwt-secret)
fi

# Read PII encryption key from environment variable (preferred) or secret file (fallback)
if [ -n "${PII_ENCRYPTION_KEY}" ]; then
    export PII_ENCRYPTION_KEY
elif [ -f /run/secrets/pii-encryption-key ]; then
    export PII_ENCRYPTION_KEY=$(cat /run/secrets/pii-encryption-key)
fi

# Read bootstrap admin password from environment variable (preferred) or secret file (fallback)
if [ -n "${BOOTSTRAP_ADMIN_PASSWORD}" ]; then
    export BOOTSTRAP_ADMIN_PASSWORD
elif [ -f /run/secrets/bootstrap-admin-password ]; then
    export BOOTSTRAP_ADMIN_PASSWORD=$(cat /run/secrets/bootstrap-admin-password)
fi

# Read CORS origins from environment variable (preferred) or secret file (fallback)
if [ -n "${CORS_ORIGINS}" ]; then
    export CORS_ORIGINS
elif [ -f /run/secrets/cors-origins ]; then
    export CORS_ORIGINS=$(cat /run/secrets/cors-origins)
fi

# Read database password from environment variable (preferred) or secret file (fallback)
if [ -n "${DB_PASSWORD}" ]; then
    export DB_PASSWORD
elif [ -f /run/secrets/db-password ]; then
    export DB_PASSWORD=$(cat /run/secrets/db-password)
fi

# Read Redis password from environment variable (preferred) or secret file (fallback)
if [ -n "${REDIS_PASSWORD}" ]; then
    export REDIS_PASSWORD
elif [ -f /run/secrets/redis-password ]; then
    export REDIS_PASSWORD=$(cat /run/secrets/redis-password)
fi

# Execute the original command
exec "$@"