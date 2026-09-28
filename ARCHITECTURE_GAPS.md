# LifeOS — Architecture Gaps Analysis

Date: 2026-09-16  
Reference: LifeOS Master Architecture & Implementation Prompt v2

---

## 1. Domain & Backend Structure Gaps

### Current Structure:
```text
backend/app/
├── api/v1/
├── core/
├── models/
└── services/
```

### Required Master Architecture Structure:
```text
backend/app/
├── api/v1/             # HTTP endpoints & routers
├── core/               # Settings, DB session, security, exceptions
├── domain/             # Core business rules & entities
│   ├── finance/        # Pure financial calculations & models
│   ├── work/           # Work sessions & rate analytics
│   ├── memory/         # Semantic memory & embedding contracts
│   └── plans/          # Future plan models & definitions
├── application/        # Application services & use case orchestrators
├── infrastructure/     # External adapters, DB repositories, Redis, HTTP clients
├── workers/            # Async task processor (summaries, notifications, recalculations)
├── ai/                 # AI Gateway, Model Router, Tool Registry, Context Builder
├── analytics/          # Health scoring, snapshot aggregation, metrics
├── forecasting/        # Naive, EWMA, Day-of-week, Confidence bands
├── optimization/       # Mathematical optimization (OR-Tools / Linear Programming)
├── simulation/         # Non-mutating state projection ("What if" engine)
└── integrations/       # Telegram bot, Maps, Health Connect adapters
```

### Key Gaps:
1. **No Domain Isolation**: Business rules and queries are interleaved inside SQLAlchemy models and FastAPI routers.
2. **Missing Engines**: Forecasting, Simulation, Optimization, and Financial Health are entirely missing.
3. **Missing WorkSession Domain**: Tracking time worked, rolling income/hour, and income attribution is missing.
4. **No Redis Layer**: Redis is absent from backend configuration; all state is queried directly from PostgreSQL without caching.

---

## 2. Synchronization & Offline-First Gaps

### Current Behavior:
1. Android writes directly to Room DAO.
2. Repository fires an unmanaged asynchronous Retrofit call (`remoteApi.createTransaction`).
3. If offline, the exception is caught in a `try/catch` and silently ignored.
4. Sync method pulls 100 transactions and blindly inserts them back into Room, risking overwriting local edits.
5. Deleting an item locally leaves no trace in Room; the next sync pulls it back down from the server.

### Required Architecture:
1. **Sync Queue Entity (`sync_queue`)**:
   ```kotlin
   @Entity(tableName = "sync_queue")
   data class SyncQueueEntity(
       @PrimaryKey val id: String,
       val entityType: String,      // "TRANSACTION", "ACCOUNT", "DEBT", "GOAL", "WORK_SESSION"
       val entityId: String,
       val operation: String,       // "CREATE", "UPDATE", "DELETE"
       val payload: String,         // JSON representation
       val createdAt: Long,
       val retryCount: Int = 0,
       val status: String = "PENDING",
       val lastError: String? = null
   )
   ```
2. **Entity Versioning & Soft Deletes**:
   - `version: Long`
   - `deleted_at: Long?`
   - `server_id: String?`
   - `sync_status: String` ("SYNCED", "PENDING", "CONFLICT")
3. **Android WorkManager**:
   - `PeriodicSyncWorker`: Runs every 15–30 minutes with network constraint.
   - `OneTimeSyncWorker`: Dispatched immediately upon enqueueing a mutation.
4. **Deterministic Conflict Resolution**:
   - Version vector or 3-way merge logic.
   - Financial data must never be silently overwritten by timestamps alone.

---

## 3. Financial Truth & Mathematical Engine Gaps

### Gaps in Financial Calculations:
1. **Available vs Protected vs Free Money**:
   - Current system only calculates `total_balance - debts = net_worth`.
   - Master architecture requires:
     - **Protected Money**: Hard constraints (minimum liquidity, upcoming hard obligations, partner allocation, emergency fund reserve).
     - **Free Money**: `Available Cash - Protected Cash`.
2. **Financial Health Index ($H$)**:
   - Missing composite score:
     $$H = w_1 \cdot \text{liquidity} + w_2 \cdot \text{obligation\_coverage} + w_3 \cdot \text{debt\_pressure} + w_4 \cdot \text{savings\_progress} + w_5 \cdot \text{income\_stability}$$
   - Missing deterministic **RED / YELLOW / GREEN** status with human-readable rationales.
3. **Work & Earnings Engine**:
   - Missing `WorkSession` with metrics: hourly income, rolling 7-day/30-day income per hour, day-of-week velocity.
4. **Forecast Engine**:
   - Missing statistical forecast models: Rolling Mean, Exponentially Weighted Moving Average (EWMA), Day-of-week seasonality, and confidence intervals ($[\mu - 2\sigma, \mu + 2\sigma]$).
5. **Simulation Engine**:
   - Missing hypothetical branching:
     $$S(t+1) = f(S(t), \Delta X)$$
     Calculates changes in goal achievement dates, cash reserves, and risk scores without writing to the database.
6. **Optimization Engine**:
   - Missing Google OR-Tools integration for debt payoff sequencing (Avalanche vs Snowball with constrained cash) and budget allocation vectors.

---

## 4. AI Gateway & Memory Gaps

### Current Implementation:
- Single endpoint calls Gemini Flash directly with a monolithic JSON dump of the dashboard.
- Telegram bot calls Gemini directly with a single prompt.

### Required Architecture:
1. **AI Gateway & Provider Abstraction**:
   - Interface `LLMProvider` with implementations `GeminiProvider` and `OpenAIProvider`.
   - Interface `EmbeddingProvider` for generating vector embeddings.
2. **Typed Tools Registry**:
   - Deterministic execution sandbox with tools:
     - `get_current_balance()`
     - `get_month_income()`
     - `get_month_expenses()`
     - `get_obligations()`
     - `get_debt_status()`
     - `get_goal_progress()`
     - `get_income_forecast()`
     - `get_expense_forecast()`
     - `estimate_plan_cost()`
     - `simulate_expense()`
     - `calculate_work_required()`
     - `get_financial_health()`
     - `get_recent_transactions()`
     - `get_personal_preferences()`
3. **Structured & Semantic Memory**:
   - PostgreSQL `pgvector` extension for storing semantic memories, user preferences, and financial habits.
   - Long-term structured user config vs short-term contextual memory.
4. **Structured JSON Output**:
   - Pydantic schema validation for all LLM outputs (facts, calculations, warnings, status).

---

## 5. Infrastructure & Cloud Deployment Gaps

### Current Setup:
- Configured for Render free web service (`render.yaml`) with external PostgreSQL (e.g. Neon.tech).
- No Docker Compose stack for self-hosting.
- No background worker daemon.
- No automated backup or disaster recovery plan.

### Required OCI Stack:
1. **Self-Contained Docker Compose Stack**:
   - `reverse-proxy`: Caddy (automated HTTPS) or Nginx.
   - `api`: FastAPI running under Uvicorn with Gunicorn workers.
   - `worker`: Lightweight async worker processing jobs from Redis.
   - `scheduler`: Background scheduler triggering periodic aggregations.
   - `postgres`: PostgreSQL 16 with `pgvector` installed (`pgvector/pgvector:pg16`).
   - `redis`: Redis 7 Alpine for queue, rate-limiting, and cache.
2. **OCI ARM64 Compatibility**:
   - Ensure all container images are compiled for `linux/arm64` (Ampere A1 architecture).
   - Low resource footprint (< 2 GB RAM baseline for the entire stack).
3. **Automated Backups**:
   - Daily encrypted `pg_dump` with off-site or object storage sync.
