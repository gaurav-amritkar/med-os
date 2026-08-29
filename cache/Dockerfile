FROM redis:7.4.2-alpine

COPY redis.conf /usr/local/etc/redis/redis.conf

# Read password from secret file if available
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]