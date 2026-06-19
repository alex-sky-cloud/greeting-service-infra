#!/usr/bin/env bash
# ============================================================================
# 09-check-db-user-privileges.sh
# НАЗНАЧЕНИЕ: проверить права greeting_user на удалённой БД (Flyway).
# БЕЗОПАСНО: только SELECT-запросы через туннель, данные не меняет.
# ЗАПУСК:    bash scripts/dev-db-connection/09-check-db-user-privileges.sh
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/lib.sh"
load_shell_secrets
load_ips_from_terraform

PSQL_BIN=""
if ! PSQL_BIN="$(find_psql)"; then
  echo "psql не найден — docs/dev-remote-db-connection.md §2.1"
  exit 1
fi

if [[ -z "${TF_VAR_db_password:-}" ]]; then
  echo "TF_VAR_db_password не задан — source ~/.bashrc"
  exit 1
fi

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
s.connect(("127.0.0.1", ${LOCAL_TUNNEL_PORT}))
s.close()
PY
then
  bash "${SCRIPT_DIR}/03-start-tunnel.sh"
  STARTED_TUNNEL=1
fi

export PGPASSWORD="${TF_VAR_db_password}"
PSQL=( "${PSQL_BIN}" -h 127.0.0.1 -p "${LOCAL_TUNNEL_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 )

echo "=== Подключение ==="
print_connection_summary
echo

echo "=== 1. Роль greeting_user существует? ==="
"${PSQL[@]}" -c "SELECT rolname, rolcanlogin, rolcreaterole, rolcreatedb, rolsuper FROM pg_roles WHERE rolname = '${DB_USER}';"

echo
echo "=== 2. CONNECT на базу greeting_db ==="
"${PSQL[@]}" -c "SELECT has_database_privilege('${DB_USER}', '${DB_NAME}', 'CONNECT') AS can_connect;"

echo
echo "=== 3. CREATE на базу greeting_db (нужно для Flyway create-schemas) ==="
"${PSQL[@]}" -c "SELECT has_database_privilege('${DB_USER}', '${DB_NAME}', 'CREATE') AS can_create_schema;"

echo
echo "=== 4. Существующие схемы приложения ==="
"${PSQL[@]}" -c "SELECT schema_name, schema_owner FROM information_schema.schemata WHERE schema_name IN ('iso_demo', 'shop_demo', 'public') ORDER BY schema_name;"

echo
echo "=== 5. Привилегии на схемы iso_demo / shop_demo ==="
"${PSQL[@]}" -c "
SELECT nspname AS schema,
       has_schema_privilege('${DB_USER}', nspname, 'USAGE') AS usage,
       has_schema_privilege('${DB_USER}', nspname, 'CREATE') AS create_on_schema
FROM pg_namespace
WHERE nspname IN ('iso_demo', 'shop_demo', 'public')
ORDER BY nspname;"

echo
echo "=== 6. flyway_schema_history (если есть) ==="
"${PSQL[@]}" -c "
SELECT schemaname, tablename
FROM pg_tables
WHERE tablename = 'flyway_schema_history';"

echo
echo "=== 7. Версии миграций Flyway (если таблица есть) ==="
"${PSQL[@]}" -c "
SELECT version, description, success
FROM iso_demo.flyway_schema_history
ORDER BY installed_rank
LIMIT 20;" 2>/dev/null || echo "(таблица iso_demo.flyway_schema_history отсутствует — чистая БД или миграции не проходили)"

echo
echo "=== done ==="
