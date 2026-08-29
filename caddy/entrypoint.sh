#!/bin/sh
# Wrapper script to read secrets and set environment variables for Flyway

# Read password from secret file
if [ -f /run/secrets/db-password ]; then
    export FLYWAY_PASSWORD=$(cat /run/secrets/db-password)
fi

# Set Flyway configuration from environment variables
export FLYWAY_URL="${FLYWAY_URL:-jdbc:postgresql://db:5432/medos}"
export FLYWAY_USER="${FLYWAY_USER:-medos}"

# Execute the original command with full path
exec /flyway/flyway "$@"