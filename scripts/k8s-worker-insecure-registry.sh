#!/usr/bin/env bash
# Worker-side: write containerd registry config and restart k0sworker.
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
