#!/usr/bin/env bash
# ============================================================================
# apply-k8s-insecure-registry.sh
#
# НАЗНАЧЕНИЕ: с локального ПК применить insecure-registry на указанной K8s worker-ноде.
# ЗАЧЕМ:     worker сможет pull образов с devtools:5000 без HTTPS.
# ГДЕ:       локальный kubectl debug → chroot на worker; PostgreSQL не затрагивается.
# ВНИМАНИЕ:  перезапускает k0sworker на выбранной ноде (кратковременный downtime pod'ов на ней).
#
# Запуск: bash scripts/apply-k8s-insecure-registry.sh worker-10.10.0.7
# ============================================================================
set -euo pipefail
NODE="${1:?Usage: bash scripts/apply-k8s-insecure-registry.sh <node-name>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export KUBECONFIG="${KUBECONFIG:-/c/Users/sky/.kube/timeweb-greeting.yaml}"
DEVTOOLS_IP="${DEVTOOLS_IP:-72.56.249.137}"

encode() {
  base64 "$1" | tr -d '\n\r'
}

run_on_worker() {
  local payload="$1"
  kubectl debug "node/${NODE}" --profile=sysadmin --image=ubuntu:22.04 --attach -- \
    bash -c "chroot //host bash -c 'echo ${payload} | base64 -d | tr -d \"\\r\" | bash'"
}

echo "=== Step 1/2: configure registry on ${NODE} ==="
CFG_PAYLOAD="$(encode "${ROOT}/scripts/k8s-worker-insecure-registry.sh")"
run_on_worker "${CFG_PAYLOAD}"

echo "=== Step 2/2: verify config on ${NODE} ==="
kubectl debug "node/${NODE}" --profile=sysadmin --image=ubuntu:22.04 --attach -- \
  bash -c "chroot //host bash -c 'systemctl is-active k0sworker; test -s /etc/k0s/containerd.d/certs.d/${DEVTOOLS_IP}:5000/hosts.toml; echo OK: registry config on \$(hostname)'"

echo "Done: ${NODE}"
