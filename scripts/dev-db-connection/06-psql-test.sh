#!/usr/bin/env bash
# ============================================================================
# 06-psql-test.sh
# НАЗНАЧЕНИЕ: тестовый SELECT через psql и SSH-туннель.
# БЕЗОПАСНО: только чтение (SELECT, \dn), данные не меняет.
# ЗАПУСК:    bash scripts/dev-db-connection/06-psql-test.sh
# ============================================================================set -euo pipefail

source "$(dirname "$0")/lib.sh"
load_shell_secrets

PSQL_BIN=""
if ! PSQL_BIN="$(find_psql)"; then
  echo "psql не найден. Установите клиент — docs/dev-remote-db-connection.md §2.1"
  exit 1
fi

if [[ -z "${TF_VAR_db_password:-}" ]]; then
  echo "TF_VAR_db_password не задан. Выполните: source ~/.bashrc"
  exit 1
fi

echo "=== psql через туннель localhost:${LOCAL_TUNNEL_PORT} ==="
echo "psql: ${PSQL_BIN}"
echo "db  : ${DB_NAME}, user: ${DB_USER}"
echo

export PGPASSWORD="${TF_VAR_db_password}"
"${PSQL_BIN}" -h 127.0.0.1 -p "${LOCAL_TUNNEL_PORT}" -U "${DB_USER}" -d "${DB_NAME}" \
  -c "SELECT current_database() AS db, current_user AS usr;"

echo
echo "=== Список схем (\\dn) ==="
"${PSQL_BIN}" -h 127.0.0.1 -p "${LOCAL_TUNNEL_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -c "\dn"

echo
echo "psql_ok"
