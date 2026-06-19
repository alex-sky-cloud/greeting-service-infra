#!/usr/bin/env bash
# ============================================================================
# 09-check-db-user-privileges-wsl.sh
#
# НАЗНАЧЕНИЕ: проверить права greeting_user (вариант через SSH на devtools + psql в VPC).
# ЗАЧЕМ:     альтернатива туннелю из WSL, когда localhost:15432 недоступен.
# БЕЗОПАСНО: только SELECT-запросы, данные не меняет.
#             На devtools может установить postgresql-client (apt), БД не изменяет.
#
# Предпочтительно: bash scripts/dev-db-connection/09-check-db-user-privileges.sh
#
# Переменные (опционально):
#   SSH_KEY, DEVTOOLS_IP, DB_PRIVATE_IP, TF_VAR_db_password
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

load_shell_secrets
load_ips_from_terraform 2>/dev/null || true

SSH_KEY="${SSH_KEY:-${HOME}/.ssh/id_ed25519}"
DEV="${DEVTOOLS_IP:-${DEVTOOLS_PUBLIC_IP:-}}"
DB="${DB_PRIVATE_IP:-${DB_HOST:-}}"

[[ -n "${DEV}" ]] || { echo "[ERROR] DEVTOOLS_IP не задан (terraform output или env)"; exit 1; }
[[ -n "${DB}" ]] || { echo "[ERROR] DB_PRIVATE_IP не задан"; exit 1; }
[[ -n "${TF_VAR_db_password:-}" ]] || { echo "[ERROR] TF_VAR_db_password не задан — source ~/.bashrc"; exit 1; }

run_psql() {
  local sql="$1"
  ssh -i "${SSH_KEY}" -o ConnectTimeout=15 -o StrictHostKeyChecking=no "root@${DEV}" \
    "export PGPASSWORD='${TF_VAR_db_password}'; psql -h ${DB} -p 5432 -U greeting_user -d greeting_db -c \"${sql}\"" 2>&1
}

ssh -i "${SSH_KEY}" -o StrictHostKeyChecking=no "root@${DEV}" \
  "command -v psql >/dev/null || (apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -qq -y postgresql-client)"

echo "=== 1. Role ==="
run_psql "SELECT rolname, rolcanlogin, rolcreatedb, rolsuper FROM pg_roles WHERE rolname = 'greeting_user';"

echo "=== 2. CONNECT / CREATE on database ==="
run_psql "SELECT has_database_privilege('greeting_user', 'greeting_db', 'CONNECT') AS connect, has_database_privilege('greeting_user', 'greeting_db', 'CREATE') AS create_schema;"

echo "=== 2b. datacl greeting_db ==="
run_psql "SELECT datname, datacl FROM pg_database WHERE datname = 'greeting_db';"

echo "=== 3. Schemas ==="
run_psql "SELECT schema_name, schema_owner FROM information_schema.schemata WHERE schema_name IN ('iso_demo','shop_demo','public') ORDER BY 1;"

echo "=== 4. Schema privileges ==="
run_psql "SELECT nspname, has_schema_privilege('greeting_user', nspname, 'USAGE') AS usage, has_schema_privilege('greeting_user', nspname, 'CREATE') AS create_priv FROM pg_namespace WHERE nspname IN ('iso_demo','shop_demo','public') ORDER BY 1;"

echo "=== 5. flyway_schema_history ==="
run_psql "SELECT schemaname, tablename FROM pg_tables WHERE tablename = 'flyway_schema_history';"

echo "=== 6. Flyway versions ==="
run_psql "SELECT version, description, success FROM iso_demo.flyway_schema_history ORDER BY installed_rank;" || echo "(no flyway history in iso_demo)"

echo "=== done ==="
