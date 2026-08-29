FROM caddy:2-alpine

# Copy Caddyfile
COPY Caddyfile /etc/caddy/Caddyfile

# Expose ports
EXPOSE 80 443

# Run Caddy
CMD ["/usr/bin/caddy", "run", "--config", "/etc/caddy/Caddyfile"]