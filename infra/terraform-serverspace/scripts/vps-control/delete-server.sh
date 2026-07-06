#!/usr/bin/env bash
# Удалить один сервер по id (например l44s1304750).
# Использование: ./scripts/delete-server.sh l44s1304750
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
load_serverspace_env

SERVER_ID=$(ss_strip "${1:-}")
if [[ -z "$SERVER_ID" ]]; then
  echo "Использование: $0 SERVER_ID" >&2
  echo "Список id: ./scripts/list-resources.sh" >&2
  exit 1
fi

echo "GET server ${SERVER_ID}"
ss_get "servers/${SERVER_ID}"
echo
echo "DELETE server ${SERVER_ID}"
ss_delete "servers/${SERVER_ID}"
