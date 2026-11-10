# Multi-stage build for MedOS Frontend
# Node 22, matching the CI runtime. Node 20 is end-of-life, and building on a
# different runtime than CI tested on is a build-chain mismatch (#158).
FROM node:22-alpine AS builder
WORKDIR /app

# Build-time API URL
ARG VITE_API_URL=/api
ENV VITE_API_URL=$VITE_API_URL

# Copy package files
COPY package*.json ./
RUN npm ci --no-audit --no-fund

# Copy source and build
COPY . .
RUN npm run build

# Runtime with unprivileged nginx (non-root)
FROM nginxinc/nginx-unprivileged:1.27-alpine
WORKDIR /app

# Copy built assets
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy nginx config (listens on 8080 internally)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/ >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
