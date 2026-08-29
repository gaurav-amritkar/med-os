#!/bin/sh
# Read password from secret file if available
if [ -f /run/secrets/redis-password ]; then
  export REDIS_PASSWORD=$(cat /run/secrets/redis-password)
fi

exec redis-server /usr/local/etc/redis/redis.conf ${REDIS_PASSWORD:+--requirepass "$REDIS_PASSWORD"}