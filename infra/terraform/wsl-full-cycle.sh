#!/bin/bash
set -euo pipefail

source "${HOME}/.bashrc" 2>/dev/null || true

TERRAFORM_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${TERRAFORM_DIR}/../.." && pwd)"

cd "${TERRAFORM_DIR}"

echo "==> terraform destroy"
terraform destroy -auto-approve

echo "==> terraform plan"
terraform plan -out=tfplan

echo "==> terraform apply"
terraform apply tfplan

echo "==> kubeconfig"
bash "${REPO_ROOT}/scripts/get-kubeconfig.sh"

IP="$(terraform output -raw devtools_public_ip)"
SSH_CONFIG="/mnt/c/Users/sky/.ssh/config"

echo "==> ssh config (${IP})"
mkdir -p /mnt/c/Users/sky/.ssh
cp "${SSH_CONFIG}" "${SSH_CONFIG}.bak" 2>/dev/null || true
cat > "${SSH_CONFIG}" <<EOF
Host devtools
    User root
    IdentityFile /mnt/c/Users/sky/.ssh/id_ed25519

Host ${IP}
    User root
    IdentityFile /mnt/c/Users/sky/.ssh/id_ed25519
EOF
chmod 600 "${SSH_CONFIG}" 2>/dev/null || true

echo "==> devtools check"
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  if ssh -i /mnt/c/Users/sky/.ssh/id_ed25519 -o ConnectTimeout=15 -o StrictHostKeyChecking=accept-new \
    "root@${IP}" 'cloud-init status' 2>/dev/null; then
    ssh -i /mnt/c/Users/sky/.ssh/id_ed25519 "root@${IP}" \
      'tail -3 /var/log/devtools-init.log 2>/dev/null; docker --version 2>/dev/null || true'
    exit 0
  fi
  echo "    SSH not ready, wait 30s (${i}/12)..."
  sleep 30
done

echo "SSH to devtools failed after 6 min" >&2
exit 1
