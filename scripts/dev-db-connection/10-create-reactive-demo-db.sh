#!/usr/bin/env bash
# ============================================================================
# 10-create-reactive-demo-db.sh
#
# НАЗНАЧЕНИЕ: создать базу reactive_demo на УДАЛЁННОМ managed PostgreSQL.
# ЗАЧЕМ:     reactive-demo на ноутбуке (через туннель) или после terraform apply.
# ГДЕ:       managed PG в VPC — через SSH-туннель localhost:15432.
# БЕЗОПАСНО: только CREATE DATABASE если её ещё нет; существующие данные НЕ трогает.
#             (После terraform apply база может уже существовать — скрипт пропустит.)
#
# Перед запуском:
#   bash scripts/dev-db-connection/03-start-tunnel.sh
#   source ~/.bashrc   # TF_VAR_db_password
#
# Запуск из корня репозитория:
#   bash scripts/dev-db-connection/10-create-reactive-demo-db.sh
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

load_shell_secrets
load_ips_from_terraform 2>/dev/null || true

REACTIVE_DB="${REACTIVE_DB:-reactive_demo}"
PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-${LOCAL_TUNNEL_PORT}}"
PGUSER="${PGUSER:-${DB_USER}}"

PSQL_BIN=""
if ! PSQL_BIN="$(find_psql)"; then
  echo "[ERROR] psql не найден. docs/dev-remote-db-connection.md"
  exit 1
fi

if [[ -z "${TF_VAR_db_password:-}" ]]; then
  echo "[ERROR] TF_VAR_db_password не задан. Выполните: source ~/.bashrc"
  exit 1
fi

export PGPASSWORD="${TF_VAR_db_password}"

STARTED_TUNNEL=0
cleanup() {
  if [[ "${STARTED_TUNNEL}" -eq 1 ]]; then
    bash "${SCRIPT_DIR}/04-stop-tunnel.sh" || true
  fi
}
trap cleanup EXIT

if ! python - <<PY 2>/dev/null
import socket
s = socket.socket()
s.settimeout(2)
s.connect(("127.0.0.1", ${PGPORT}))
s.close()
PY
then
  echo "[INFO] Туннель не найден — поднимаю..."
  bash "${SCRIPT_DIR}/03-start-tunnel.sh"
  STARTED_TUNNEL=1
  sleep 1
fi

exists="$("${PSQL_BIN}" -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d postgres -tc \
  "SELECT 1 FROM pg_database WHERE datname='${REACTIVE_DB}'" | tr -d '[:space:]')"

if [[ "${exists}" == "1" ]]; then
  echo "База ${REACTIVE_DB} уже существует на ${PGHOST}:${PGPORT}."
else
  echo "Создаю базу ${REACTIVE_DB} на ${PGHOST}:${PGPORT}..."
  "${PSQL_BIN}" -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d postgres -v ON_ERROR_STOP=1 \
    -c "CREATE DATABASE ${REACTIVE_DB} OWNER ${PGUSER};"
  echo "Готово."
fi
