#!/usr/bin/env bash
# Проверяет SSH-доступ к devtools (jump-хост) по публичному IP.
set -euo pipefail

source "$(dirname "$0")/lib.sh"
load_ips_from_terraform

echo "=== SSH к devtools ==="
echo "host: root@${DEVTOOLS_PUBLIC_IP}"
echo "key : ${SSH_KEY}"
echo

ssh -i "${SSH_KEY}" \
  -o ConnectTimeout=15 \
  -o StrictHostKeyChecking=accept-new \
  "root@${DEVTOOLS_PUBLIC_IP}" \
  "echo devtools_ok; hostname; ip -4 addr show scope global 2>/dev/null | head -5"

echo
echo "SSH OK — можно поднимать туннель (03-start-tunnel.sh)."
