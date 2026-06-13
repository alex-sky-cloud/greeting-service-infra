#!/usr/bin/env bash
# Останавливает фоновый SSH-туннель на LOCAL_TUNNEL_PORT.
set -euo pipefail

source "$(dirname "$0")/lib.sh"
load_ips_from_terraform

echo "=== Остановка SSH-туннеля ==="
echo "pattern: ${LOCAL_TUNNEL_PORT}:${DB_HOST}:${DB_PORT}"
echo

if pkill -f "${LOCAL_TUNNEL_PORT}:${DB_HOST}:${DB_PORT}" 2>/dev/null; then
  echo "Туннель остановлен."
else
  echo "Процесс туннеля не найден (возможно, уже остановлен)."
fi
