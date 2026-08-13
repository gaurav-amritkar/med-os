FROM redis:7.4.2-alpine

COPY redis.conf /usr/local/etc/redis/redis.conf

CMD ["sh", "-c", "if [ -n \"$REDIS_PASSWORD\" ]; then exec redis-server /usr/local/etc/redis/redis.conf --requirepass \"$REDIS_PASSWORD\"; else exec redis-server /usr/local/etc/redis/redis.conf; fi"]
