#!/usr/bin/env bash
set -euo pipefail
source /mnt/c/Users/sky/.bashrc 2>/dev/null || true
SSH_KEY="/mnt/c/Users/sky/.ssh/id_ed25519"
DEV="72.56.249.137"
DB="10.10.0.5"
[[ -n "${TF_VAR_db_password:-}" ]] || { echo "TF_VAR_db_password not set"; exit 1; }

run_psql() {
  local sql="$1"
  ssh -i "${SSH_KEY}" -o ConnectTimeout=15 -o StrictHostKeyChecking=no "root@${DEV}" \
    "export PGPASSWORD='${TF_VAR_db_password}'; psql -h ${DB} -p 5432 -U greeting_user -d greeting_db -c \"${sql}\"" 2>&1
}

ssh -i "${SSH_KEY}" -o StrictHostKeyChecking=no "root@${DEV}" "command -v psql >/dev/null || (apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -qq -y postgresql-client)"

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
