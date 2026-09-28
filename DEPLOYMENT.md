# LifeOS — OCI Deployment & Infrastructure Guide

Date: 2026-09-16  
Platform: Oracle Cloud Infrastructure (OCI) Always Free / Standard Compute  
Target Architecture: ARM64 (Ampere A1) or AMD x86_64

---

## 1. Tenancy Sizing & Philosophy

LifeOS follows a **modular monolith** container strategy designed to fit comfortably within modest VM resources (even a single 1–2 OCPU, 2–4 GB RAM instance on OCI Always Free).

### Resource Guardrails:
- **No Kubernetes**: Docker Compose is standard.
- **No Heavy JVM Services**: No Kafka, no Elasticsearch.
- **Unified Storage**: PostgreSQL 16 handles both relational data and semantic vectors (`pgvector`).
- **In-Memory Cache & Queue**: Single Redis Alpine container handles rate limiting, short-term caching, and worker coordination.

---

## 2. Container Topology

```text
               INTERNET
                  │ (HTTPS :443)
                  ▼
         ┌─────────────────┐
         │  Reverse Proxy  │ (Caddy 2 Alpine with Auto-Let's Encrypt)
         └────────┬────────┘
                  │ (Internal Docker Network)
                  ▼
         ┌─────────────────┐
         │  FastAPI API    │ (Python 3.12-slim ARM64)
         └────────┬────────┘
                  │
        ┌─────────┴─────────┐
        │                   │
        ▼                   ▼
┌───────────────┐   ┌───────────────┐
│ PostgreSQL 16 │   │    Redis 7    │
│  + pgvector   │   │    Alpine     │
└───────────────┘   └───────────────┘
        ▲                   ▲
        │                   │
        └─────────┬─────────┘
                  │
         ┌─────────────────┐
         │ Background      │ (Python 3.12 Worker + Scheduler)
         │ Worker Daemon   │
         └─────────────────┘
```

---

## 3. Production Docker Compose (`docker-compose.yml`)

```yaml
version: "3.8"

services:
  caddy:
    image: caddy:2-alpine
    container_name: lifeos_caddy
    restart: always
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    depends_on:
      - api
    networks:
      - lifeos_net

  api:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: lifeos_api
    restart: always
    environment:
      - DATABASE_URL=postgresql+asyncpg://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/${POSTGRES_DB}
      - REDIS_URL=redis://redis:6379/0
      - SECRET_KEY=${SECRET_KEY}
      - ADMIN_EMAIL=${ADMIN_EMAIL}
      - ADMIN_PASSWORD=${ADMIN_PASSWORD}
      - TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
      - TELEGRAM_ALLOWED_USER_ID=${TELEGRAM_ALLOWED_USER_ID}
      - CRON_SECRET=${CRON_SECRET}
      - AI_PROVIDER=${AI_PROVIDER:-gemini}
      - GEMINI_API_KEY=${GEMINI_API_KEY}
      - OPENAI_API_KEY=${OPENAI_API_KEY}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_started
    networks:
      - lifeos_net

  worker:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: lifeos_worker
    restart: always
    command: ["python", "-m", "app.workers.main"]
    environment:
      - DATABASE_URL=postgresql+asyncpg://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/${POSTGRES_DB}
      - REDIS_URL=redis://redis:6379/0
      - TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
      - TELEGRAM_ALLOWED_USER_ID=${TELEGRAM_ALLOWED_USER_ID}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_started
    networks:
      - lifeos_net

  postgres:
    image: pgvector/pgvector:pg16
    container_name: lifeos_postgres
    restart: always
    environment:
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: ${POSTGRES_DB}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - lifeos_net

  redis:
    image: redis:7-alpine
    container_name: lifeos_redis
    restart: always
    volumes:
      - redis_data:/data
    networks:
      - lifeos_net

volumes:
  postgres_data:
  redis_data:
  caddy_data:
  caddy_config:

networks:
  lifeos_net:
    driver: bridge
```

---

## 4. Reverse Proxy Configuration (`Caddyfile`)

```caddyfile
{$DOMAIN:api.lifeos.yourdomain.com} {
    encode gzip zstd

    # Security headers
    header {
        Strict-Transport-Security "max-age=31536000; includeSubDomains; preload"
        X-Content-Type-Options "nosniff"
        X-Frame-Options "DENY"
        Referrer-Policy "strict-origin-when-cross-origin"
    }

    # Proxy to FastAPI
    reverse_proxy api:8000 {
        header_up Host {host}
        header_up X-Real-IP {remote_host}
    }
}
```

---

## 5. ARM64 Compatibility Checklist

| Component | Base Image | ARM64 Support (`linux/arm64`) | Notes |
| :--- | :--- | :--- | :--- |
| **Reverse Proxy** | `caddy:2-alpine` | Yes | Native multi-arch |
| **Backend API** | `python:3.12-slim` | Yes | Precompiled wheels available for asyncpg & bcrypt |
| **Database** | `pgvector/pgvector:pg16` | Yes | Official image provides native ARM64 |
| **Cache** | `redis:7-alpine` | Yes | Extremely lightweight (< 15 MB RAM) |
| **Worker** | `python:3.12-slim` | Yes | Shares API base image |

---

## 6. Automated Backup & Disaster Recovery

An automated cron script on the OCI host ensures daily encrypted database dumps:

```bash
#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="/opt/lifeos/backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
FILENAME="lifeos_backup_${TIMESTAMP}.sql.gz"
ENCRYPTED_FILE="${FILENAME}.enc"

mkdir -p "${BACKUP_DIR}"

# 1. Run pg_dump inside container
docker exec lifeos_postgres pg_dump -U "${POSTGRES_USER}" "${POSTGRES_DB}" | gzip > "${BACKUP_DIR}/${FILENAME}"

# 2. Encrypt using openssl AES-256-CBC
openssl enc -aes-256-cbc -salt -pbkdf2 -in "${BACKUP_DIR}/${FILENAME}" -out "${BACKUP_DIR}/${ENCRYPTED_FILE}" -pass env:BACKUP_ENCRYPTION_KEY
rm "${BACKUP_DIR}/${FILENAME}"

# 3. Retain last 14 days locally
find "${BACKUP_DIR}" -type f -name "*.enc" -mtime +14 -delete

# 4. Optional: rsync or oci-cli upload to OCI Object Storage bucket
# oci os object put -bn lifeos-backups --file "${BACKUP_DIR}/${ENCRYPTED_FILE}" --name "${ENCRYPTED_FILE}"
```

### Restore Procedure:
1. Decrypt: `openssl enc -d -aes-256-cbc -pbkdf2 -in backup.sql.gz.enc -out backup.sql.gz -pass env:BACKUP_ENCRYPTION_KEY`
2. Unpack & restore: `gunzip -c backup.sql.gz | docker exec -i lifeos_postgres psql -U ${POSTGRES_USER} -d ${POSTGRES_DB}`
