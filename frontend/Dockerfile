# Multi-stage build for MedOS Frontend
FROM node:20-alpine AS builder
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

# Runtime with nginx
FROM nginx:1.27-alpine
WORKDIR /app

# Copy built assets
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1:80/ >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
