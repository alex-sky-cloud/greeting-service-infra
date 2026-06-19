#!/usr/bin/env bash
# ============================================================================
# 07-verify-all.sh
# НАЗНАЧЕНИЕ: полная проверка цепочки SSH → VPC → туннель → psql.
# БЕЗОПАСНО: диагностика; в конце останавливает поднятый туннель.
# ЗАПУСК:    bash scripts/dev-db-connection/07-verify-all.sh
# ============================================================================set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== dev-db-connection: полная проверка ==="
echo

bash "${SCRIPT_DIR}/01-show-terraform-ips.sh"
echo

bash "${SCRIPT_DIR}/02-check-ssh.sh"
echo

source "${SCRIPT_DIR}/lib.sh"
load_ips_from_terraform

echo "=== Postgres из devtools (внутри VPC) ==="
ssh -i "${SSH_KEY}" -o ConnectTimeout=15 -o StrictHostKeyChecking=no \
  "root@${DEVTOOLS_PUBLIC_IP}" \
  "timeout 5 bash -c 'echo >/dev/tcp/${DB_HOST}/${DB_PORT}' && echo postgres_port_open || echo postgres_port_closed"
echo

bash "${SCRIPT_DIR}/03-start-tunnel.sh"
echo

bash "${SCRIPT_DIR}/05-check-tunnel-port.sh"
echo

if PSQL_BIN="$(find_psql)"; then
  load_shell_secrets
  if [[ -n "${TF_VAR_db_password:-}" ]]; then
    bash "${SCRIPT_DIR}/06-psql-test.sh"
  else
    echo "skip psql: TF_VAR_db_password не задан (source ~/.bashrc)"
  fi
else
  echo "skip psql: клиент не установлен (docs/dev-remote-db-connection.md §2.1)"
fi

bash "${SCRIPT_DIR}/04-stop-tunnel.sh"
echo
echo "=== done ==="
