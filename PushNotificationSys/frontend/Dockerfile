# ==========================================
# Multi-Stage Dockerfile for Bronx Push Notification Platform Frontend
# ==========================================

# Stage 1: Build Vue.js SPA with pnpm (use slim/glibc for typescript-native-bridge)
FROM node:22-slim AS builder

# Enable corepack for pnpm support
RUN corepack enable && corepack prepare pnpm@latest --activate

WORKDIR /app

# Copy dependency and workspace configuration files first for layer caching
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml* ./

# Install dependencies (frozen lockfile for reproducible builds)
RUN pnpm install --frozen-lockfile

# Copy source code and build
COPY . .

# Build-time environment variables (override via --build-arg)
ARG VITE_API_BASE_URL=/api
ARG VITE_ENABLE_MOCK_DATA=false
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_ENABLE_MOCK_DATA=$VITE_ENABLE_MOCK_DATA

RUN pnpm build

# Stage 2: Nginx Production Server
FROM nginx:1.27-alpine

# Copy built static files from builder
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy Nginx reverse proxy configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# Run as non-root (nginx alpine supports this)
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    touch /run/nginx.pid && chown nginx:nginx /run/nginx.pid

HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
