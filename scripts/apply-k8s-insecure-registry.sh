#!/usr/bin/env bash
# Apply insecure-registry on a K8s worker from local Git Bash.
# Usage: bash scripts/apply-k8s-insecure-registry.sh worker-10.10.0.7
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
