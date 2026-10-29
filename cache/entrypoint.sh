#!/bin/sh
# Build a private runtime config so the password never appears in redis-server's
# argv. The base config is image-owned; the generated copy disappears with the
# container and is readable only by this user.
if [ -f /run/secrets/redis-password ]; then
  REDIS_PASSWORD=$(cat /run/secrets/redis-password)
  umask 077
  cp /usr/local/etc/redis/redis.conf /tmp/redis-runtime.conf
  printf '\nrequirepass %s\n' "$REDIS_PASSWORD" >> /tmp/redis-runtime.conf
  unset REDIS_PASSWORD
  exec redis-server /tmp/redis-runtime.conf
fi

exec redis-server /usr/local/etc/redis/redis.conf
