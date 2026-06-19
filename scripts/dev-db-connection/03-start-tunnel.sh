#!/usr/bin/env bash
# ============================================================================
# 03-start-tunnel.sh
# НАЗНАЧЕНИЕ: SSH-туннель localhost:15432 → managed PostgreSQL в VPC.
# ЗАЧЕМ:     DBeaver, psql, bootRun с ноутбука на удалённую БД.
# ГДЕ:       локальный ПК; на сервере только SSH, БД не изменяется.
# ЗАПУСК:    bash scripts/dev-db-connection/03-start-tunnel.sh
# ============================================================================set -euo pipefail

source "$(dirname "$0")/lib.sh"
load_ips_from_terraform

echo "=== Старт SSH-туннеля ==="
echo "localhost:${LOCAL_TUNNEL_PORT} -> ${DB_HOST}:${DB_PORT} via root@${DEVTOOLS_PUBLIC_IP}"
echo

# Закрываем предыдущий туннель с тем же локальным портом, если остался.
pkill -f "${LOCAL_TUNNEL_PORT}:${DB_HOST}:${DB_PORT}" 2>/dev/null || true

ssh -i "${SSH_KEY}" \
  -o ConnectTimeout=15 \
  -o StrictHostKeyChecking=no \
  -f -N \
  -L "${LOCAL_TUNNEL_PORT}:${DB_HOST}:${DB_PORT}" \
  "root@${DEVTOOLS_PUBLIC_IP}"

sleep 1
echo "Туннель запущен в фоне."
echo "Проверка: bash scripts/dev-db-connection/05-check-tunnel-port.sh"
echo "Остановка: bash scripts/dev-db-connection/04-stop-tunnel.sh"
