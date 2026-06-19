#!/usr/bin/env bash
# ============================================================================
# create-reactive-demo-db.sh
#
# НАЗНАЧЕНИЕ: создать базу reactive_demo в ЛОКАЛЬНОМ Docker Postgres (local-postgres).
# ЗАЧЕМ:     модуль reactive-demo (profile local) подключается к localhost:5432/reactive_demo.
# ГДЕ:       только ваш ПК — удалённый managed PostgreSQL не затрагивается.
# БЕЗОПАСНО: только CREATE DATABASE IF NOT EXISTS (идемпотентно), данные не удаляются.
#
# Запуск из КОРНЯ репозитория:
#   bash scripts/create-reactive-demo-db.sh
#
# Windows:
#   scripts\create-reactive-demo-db.cmd
#
# Документация: scripts/local-reactive-demo-db.md
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_DIR="${REPO_ROOT}/app/src/main/resources/docker-greeting"

ensure_local_postgres() {
  if docker ps --format '{{.Names}}' | grep -qx 'local-postgres'; then
    return 0
  fi
  echo "[INFO] local-postgres не запущен — поднимаю docker compose..."
  if [[ ! -f "${COMPOSE_DIR}/docker-compose.yml" ]]; then
    echo "[ERROR] Не найден ${COMPOSE_DIR}/docker-compose.yml"
    exit 1
  fi
  (cd "${COMPOSE_DIR}" && docker compose up -d postgres reactive-demo-db-init)
  sleep 5
  if ! docker ps --format '{{.Names}}' | grep -qx 'local-postgres'; then
    echo "[ERROR] local-postgres не стартовал. Проверьте Docker Desktop."
    exit 1
  fi
}

ensure_local_postgres

if docker exec local-postgres psql -U app -d app -tc "SELECT 1 FROM pg_database WHERE datname='reactive_demo'" | grep -q 1; then
  echo "База reactive_demo уже существует."
else
  echo "Создаю базу reactive_demo..."
  docker exec local-postgres psql -U app -d app -v ON_ERROR_STOP=1 \
    -c "CREATE DATABASE reactive_demo OWNER app;"
  echo "Готово: reactive_demo создана."
fi
