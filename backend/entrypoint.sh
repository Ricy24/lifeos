#!/bin/sh
set -e

echo "=== Running Database Migrations ==="
alembic upgrade head

echo "=== Starting FastAPI Application ==="
exec uvicorn app.main:app --host 0.0.0.0 --port 8000
