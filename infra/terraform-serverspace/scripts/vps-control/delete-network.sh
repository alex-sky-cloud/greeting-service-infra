#!/usr/bin/env bash
# Удалить изолированную сеть по id.
# Использование: ./scripts/delete-network.sh l44n752
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
load_serverspace_env

NET_ID=$(ss_strip "${1:-}")
if [[ -z "$NET_ID" ]]; then
  echo "Использование: $0 NETWORK_ID" >&2
  exit 1
fi

echo "GET network ${NET_ID}"
ss_get "networks/isolated/${NET_ID}"
echo
echo "DELETE network ${NET_ID}"
ss_delete "networks/isolated/${NET_ID}"
