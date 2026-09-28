# LifeOS Finance — Current State Audit (Phase 0)

Date: 2026-09-16  
Repository: `Andresfinanzas` / `LifeOS`  
Status: Phase 0 Complete — Comprehensive Architecture Audit

---

## 1. Executive Summary

LifeOS Finance is currently in an intermediate stage of transition from a personal finance tracker into a personal operating system. The codebase has working foundational blocks:
- A functional **FastAPI backend** with async SQLAlchemy 2.0 and PostgreSQL support.
- A functional **Android Kotlin/Jetpack Compose client** with Room database and Material 3 UI.
- Basic **offline-first local reading** via Room and Flow.
- Initial deterministic algorithms for **debt and goal priority scoring**.
- A hybrid **Telegram bot** that interprets natural language via Gemini Flash or regex fallbacks.
- Optional AI recommendations via Gemini / OpenAI.

However, significant gaps exist between the current state and the **LifeOS Master Architecture v2**:
- Android lacks a robust synchronization engine (no `sync_queue`, no background `WorkManager`, no tombstone deletion handling, no conflict resolution).
- Backend lacks modular domain boundaries (analytics, forecasting, simulation, optimization, work sessions, semantic memory with pgvector, background workers, and Redis).
- Alembic migrations have not been versioned in Git (tables were generated dynamically via `init_db()`).
- Deployment is currently tied to Render free-tier configurations rather than a production-ready, resource-efficient OCI ARM64 Docker Compose stack.

---

## 2. Android Architecture & State

### 2.1 What Already Works
- **UI & Presentation**: Built entirely in Jetpack Compose, Material 3, dynamic color theme preferences persisted in DataStore, Vico charting for monthly expense trends.
- **Local Persistence (Room)**:
  - Database: `LifeOSDatabase` (Version 3).
  - Entities: `AccountEntity`, `TransactionEntity`, `DebtEntity`, `GoalEntity`, `WishlistEntity`.
  - DAOs: Full CRUD support returning Kotlin `Flow` for reactive UI updates.
- **Dependency Injection**: Hilt is properly configured across Application, Activity, ViewModels, DatabaseModule, NetworkModule, and SecurityModule.
- **Security**: EncryptedSharedPreferences (`SecurityModule.kt`) for secure JWT token storage.
- **External Intents**: Share intent receiver configured for URLs (e.g., Mercado Libre wishlist items) and Google Maps location opening.

### 2.2 What Is Incomplete or Missing
- **Clean Architecture Hierarchy**: Currently structured as `data/`, `ui/`, `di/`. Missing explicit `domain/` layer (UseCases, pure domain entities) and separation of `presentation/`.
- **Work Session Engine**: No entity, DAO, repository, or UI for `WorkSession` tracking (hours, earnings, hourly rate).
- **Plan Engine**: No entity or screens for future activity/budget planning.
- **Financial Health & Simulations**: No local calculations for liquidity, debt pressure, or scenario simulations.

### 2.3 Offline-First Violations & Sync Weaknesses
- **Missing `sync_queue`**: Mutations are executed directly in repositories (`addTransaction`, `addAccount`) with an inline try/catch HTTP call. If the phone is offline, the operation is caught silently, leaving the entity marked as `pending`. There is no persistent queue recording operations (CREATE, UPDATE, DELETE), retry counts, or idempotency keys.
- **Silent Deletion Corruption (Resurrection Bug)**: When items are deleted locally, they are erased from Room. Since there are no soft-deletes (`deleted_at`), the next sync cycle that pulls remote records re-inserts the deleted items into Room.
- **No Background Sync (WorkManager)**: Sync is triggered inside ViewModels on `init` or user action. If the app is closed before an HTTP call finishes, sync fails until the user re-opens that screen.
- **No Conflict Resolution**: Sync blindly overwrites local items with remote items or vice versa with no version comparison (`version` or `server_version`).

---

## 3. Backend Architecture & State

### 3.1 What Already Works
- **Framework & Asynchrony**: FastAPI 0.115, asyncpg driver, SQLAlchemy 2.0 async engine and sessions.
- **Authentication**: JWT access tokens, password hashing with bcrypt, endpoint protection dependencies.
- **API Routers**:
  - `/api/v1/auth`: login, register, profile.
  - `/api/v1/accounts`: CRUD and balance tracking.
  - `/api/v1/transactions`: CRUD with filtering by date, category, account, and type.
  - `/api/v1/debts`: CRUD, payment recording, and priority-sorted list.
  - `/api/v1/goals` & wishlist: CRUD, progress tracking, and priority-sorted list.
  - `/api/v1/tasks`: Protected cron endpoints for Telegram daily/weekly digests.
  - `/api/v1/telegram`: Webhook and polling handlers.
  - `/api/v1/insights`: AI recommendations endpoint.
- **Deterministic Engines**:
  - `priority_engine.py`: Scored (0–100) priority calculation for debts (interest, due date, status) and goals (urgency, emergency fund, target date).
  - `financial_engine.py`: Period summaries (day, week, month), category breakdowns, net worth calculation, daily target tracking.

### 3.2 What Is Incomplete or Missing
- **Module Structure**: Monolithic layout under `app/api/v1/`, `app/models/`, and `app/services/`. Not yet organized into the modular monolith requested: `domain/`, `application/`, `infrastructure/`, `workers/`, `ai/`, `analytics/`, `forecasting/`, `optimization/`, `simulation/`.
- **Work Engine**: No SQLAlchemy model or router for `WorkSession`.
- **Plan Engine**: No model or calculation service for `Plan`.
- **Background Worker Infrastructure**: Cron tasks run synchronously inside HTTP endpoints (`/api/v1/tasks/daily-summary`). Long-running tasks, embedding generation, and recurring recalculations lack a dedicated worker process (e.g. lightweight asyncio worker / Redis queue).
- **Redis Integration**: Redis is not yet in `requirements.txt` or configured in `app/core/config.py`.

---

## 4. Database & Models Audit

### 4.1 Existing Tables
1. `users` (id, email, hashed_password, full_name, is_active, timestamps)
2. `accounts` (id, user_id, name, account_type, balance, currency, description, color, icon, is_active, include_in_total, timestamps)
3. `transactions` (id, user_id, account_id, amount, transaction_type, category, description, notes, transaction_date, location, tags, is_recurring, recurring_pattern, source, metadata_extra, sync_status, timestamps)
4. `debts` (id, user_id, person_or_entity, debt_type, original_amount, remaining_amount, interest_rate, debt_date, due_date, priority, status, description, notes, tags, timestamps)
5. `debt_payments` (id, debt_id, amount, payment_date, notes, created_at)
6. `goals` (id, user_id, name, target_amount, current_amount, category, description, image_url, priority, target_date, status, notes, tags, timestamps)
7. `wishlist_items` (id, user_id, name, price, url, store, image_url, category, priority, saved_amount, previous_price, status, notes, timestamps)
8. `financial_configs` (id, user_id, hourly_rate, daily_target, weekly_target, monthly_target, work_days_per_week, work_hours_per_day, currency, timezone, custom categories, timestamps)

### 4.2 Missing Schema Capabilities
- **Audit & Sync Metadata**: Missing `version`, `deleted_at` (soft delete), `server_id`, and `device_id` across syncable tables.
- **New Domain Tables**:
  - `work_sessions` (start_time, end_time, duration, income, activity_type, location, notes).
  - `semantic_memories` (id, embedding vector, memory_type, importance, confidence, source, metadata, timestamps).
  - `financial_snapshots` (snapshot_date, snapshot_type, income, expenses, cash, net_worth, debt, savings, goal_progress, hours_worked).
  - `plans` and `plan_items` (date, participants, location, budget_limit, estimated_cost, buffer).
- **Indexes**:
  - Composite index on `transactions (user_id, transaction_date)` for fast range scans.
  - Composite index on `debts (user_id, status)` and `goals (user_id, status)`.
- **Alembic State**: `backend/migrations/versions/` is empty. The project needs an initial baseline migration and strict versioned migrations going forward.

---

## 5. Synchronization Subsystem Audit

| Feature | Current Implementation | Target Master Architecture | Status |
| :--- | :--- | :--- | :--- |
| **Local Write Path** | Room DAO insert -> immediate try/catch API call | Room transaction -> UI update -> Sync Queue | Incomplete |
| **Sync Queue** | None (only in-memory status flag `syncStatus`) | Persistent Room table `sync_queue` | Missing |
| **Retry & Backoff** | None (silent failure on exception) | Exponential backoff with retry limit | Missing |
| **Background Execution** | `viewModelScope.launch` in UI | Android `WorkManager` (periodic + network constraint) | Missing |
| **Conflict Resolution** | Blind overwrite | Version-based deterministic merge / conflict flag | Missing |
| **Deletions** | Hard delete in Room (leads to re-insertion) | Soft deletes (`deleted_at`) + tombstone sync | Missing |
| **Idempotency** | None | Idempotency keys on sync payloads | Missing |

---

## 6. AI & Natural Language Subsystem Audit

### 6.1 Current AI Usage
1. `FinancialInsightsService` (`backend/app/services/ai_service.py`):
   - Direct HTTP call to Gemini Flash or OpenAI with a serialized dictionary of dashboard metrics.
   - Generates 3 advice bullets.
2. `SmartTelegramBot` (`backend/app/services/telegram_bot_service.py`):
   - Direct HTTP call to `gemini-3.6-flash` passing prompt instructions to parse natural language into `{intent, amount, category, description, account}`.
   - Fallback to basic regex/heuristics if the API is unreachable.

### 6.2 AI Architecture Gaps
- **Tight Coupling**: Direct calls to Gemini/OpenAI endpoints with hardcoded prompt strings rather than an **AI Gateway** and **Model Router**.
- **Lack of Typed Tools**: The LLM does not execute typed Python tools; instead, the backend passes ad-hoc summaries to the prompt.
- **No Structured Memory**: No pgvector semantic search or long-term personal context builder.
- **No Multi-Model Interface**: Missing uniform `LLMProvider` and `EmbeddingProvider` abstractions.

---

## 7. Mathematics & Deterministic Engines Audit

| Engine | Current State | Missing Capabilities |
| :--- | :--- | :--- |
| **Financial Truth Engine** | Basic totals (balance, period income/expense, net worth) | Available money vs Protected money vs Free money; obligation coverage. |
| **Priority Engine** | Working debt & goal scoring (0–100) | Multi-attribute constraints, weight tuning. |
| **Work Engine** | Basic hourly rate calculation in config | `WorkSession` tracking, rolling income per hour, day/week/month aggregation. |
| **Forecast Engine** | Non-existent | Baseline, rolling mean, EWMA, day-of-week, confidence bands. |
| **Simulation Engine** | Non-existent | Pure state-transition projection ("What if I spend $X?", "What if I work 10 more hours?"). |
| **Optimization Engine** | Non-existent | Linear programming (OR-Tools / MathOpt) for debt avalanche/snowball and budget allocation. |
| **Financial Health Engine** | Non-existent | Composite index (liquidity, debt pressure, coverage, savings rate) + RED/YELLOW/GREEN status. |

---

## 8. Infrastructure & Cloud Audit

- **Current Deployment**:
  - Contains `backend/render.yaml` geared toward Render.com web service.
  - Contains a basic single-stage `Dockerfile`.
- **Target Deployment (Oracle Cloud Infrastructure Always Free)**:
  - Architecture must run on a small OCI VM (x86_64 or Ampere ARM64).
  - Target stack: Caddy/Nginx reverse proxy, FastAPI backend, background worker, lightweight scheduler, PostgreSQL 16 with pgvector, Redis 7.
  - Containerization: Modular `docker-compose.yml` with multi-arch ARM64-compatible base images.
  - Backup: Automated encrypted PostgreSQL backup script.
