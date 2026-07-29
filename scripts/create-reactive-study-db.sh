#!/usr/bin/env bash
# ============================================================================
# create-reactive-study-db.sh
#
# НАЗНАЧЕНИЕ: поднять локальный PostgreSQL для модуля reactive-study.
# ГДЕ:       reactive-study/src/main/resources/docker-reactive-study/
# ЗАЧЕМ:     база reactive_study на localhost:5434 для profile local + Flyway.
#
# Запуск из КОРНЯ репозитория:
#   bash scripts/create-reactive-study-db.sh
#
# Windows:
#   scripts\create-reactive-study-db.cmd
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_DIR="${REPO_ROOT}/reactive-study/src/main/resources/docker-reactive-study"

if [[ ! -f "${COMPOSE_DIR}/docker-compose.yml" ]]; then
  echo "[ERROR] Не найден ${COMPOSE_DIR}/docker-compose.yml"
  exit 1
fi

if [[ ! -f "${COMPOSE_DIR}/.env" ]]; then
  echo "[INFO] Копирую .env.example → .env"
  cp "${COMPOSE_DIR}/.env.example" "${COMPOSE_DIR}/.env"
fi

echo "[INFO] docker compose up -d (каталог: docker-reactive-study)"
(cd "${COMPOSE_DIR}" && docker compose up -d)

echo "[INFO] Ожидание healthcheck..."
for _ in $(seq 1 30); do
  if docker exec reactive-study-postgres pg_isready -U app -d reactive_study >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! docker exec reactive-study-postgres pg_isready -U app -d reactive_study >/dev/null 2>&1; then
  echo "[ERROR] reactive-study-postgres не готов. Проверьте: docker logs reactive-study-postgres"
  exit 1
fi

echo "Готово: reactive_study на localhost:5434 (контейнер reactive-study-postgres)."
