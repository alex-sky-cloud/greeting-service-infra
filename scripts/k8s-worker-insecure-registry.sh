#!/usr/bin/env bash
# ============================================================================
# k8s-worker-insecure-registry.sh
#
# НАЗНАЧЕНИЕ: настроить containerd на K8s worker для pull с HTTP registry (:5000).
# ЗАЧЕМ:     приватный registry на devtools без TLS — worker должен доверять insecure.
# ГДЕ:       выполняется НА worker-ноде (через kubectl debug или SSH).
# ВНИМАНИЕ:  перезапускает k0sworker; образы в registry не удаляет, БД не трогает.
#
# Обычно не запускают вручную — вызывается из apply-k8s-insecure-registry.sh
# ============================================================================
set -eu
DEVTOOLS_IP="${DEVTOOLS_IP:-72.56.249.137}"
REG_HOST="${DEVTOOLS_IP}:5000"
CERTS_BASE="/etc/k0s/containerd.d/certs.d"
REG_DIR="${CERTS_BASE}/${REG_HOST}"

echo "hostname: $(hostname)"
echo "k0sworker (before): $(systemctl is-active k0sworker)"

mkdir -p "${CERTS_BASE}" "${REG_DIR}"

tee /etc/k0s/containerd.d/cri-registry.toml <<'EOF'
version = 2

[plugins."io.containerd.grpc.v1.cri".registry]
  config_path = "/etc/k0s/containerd.d/certs.d"
EOF

tee "${REG_DIR}/hosts.toml" <<EOF
server = "http://${DEVTOOLS_IP}:5000"

[host."http://${DEVTOOLS_IP}:5000"]
  capabilities = ["pull", "resolve"]
  skip_verify = true
EOF

systemctl restart k0sworker
sleep 3
echo "k0sworker (after): $(systemctl is-active k0sworker)"
ls -la "${REG_DIR}/hosts.toml"
echo "CONFIGURED: $(hostname)"
