# Multi-stage Dockerfile for Next.js frontend (Node 22 / pnpm)

# Stage 1: Install dependencies
FROM node:22-alpine AS deps
RUN apk add --no-cache libc6-compat
WORKDIR /app

RUN npm install -g pnpm@10.33.0

# Copy manifests and lockfiles to leverage Docker layer caching
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml* ./
RUN pnpm install --frozen-lockfile

# Stage 2: Build the production application
FROM node:22-alpine AS builder
RUN apk add --no-cache libc6-compat
WORKDIR /app

RUN npm install -g pnpm@10.33.0

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Build-time environment variables for Next.js public inlining
ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_GOOGLE_CLIENT_ID
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_GOOGLE_CLIENT_ID=$NEXT_PUBLIC_GOOGLE_CLIENT_ID
ENV NEXT_TELEMETRY_DISABLED=1

RUN pnpm run build

# Stage 3: Minimal production runner
FROM node:22-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

RUN addgroup -g 1001 -S nodejs && \
    adduser -u 1001 -S nextjs -G nodejs

# Copy static assets and standalone server output
COPY --from=builder /app/public ./public

# Ensure permissions for Next.js prerender cache
RUN mkdir -p .next && chown nextjs:nodejs .next

COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD node -e 'fetch("http://localhost:3000/").then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))'

CMD ["node", "server.js"]
