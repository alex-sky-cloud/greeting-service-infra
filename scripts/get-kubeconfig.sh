#!/bin/bash
# =============================================================================
# get-kubeconfig.sh
# Получает kubeconfig из Terraform outputs и сохраняет локально.
# Запускать с локального ПК после terraform apply.
# =============================================================================

set -euo pipefail

KUBECONFIG_PATH="${HOME}/.kube/timeweb-greeting.yaml"

cd "$(dirname "$0")/../infra/terraform"

echo "==> Получаем kubeconfig из Terraform..."
terraform output -raw kubeconfig > "${KUBECONFIG_PATH}"
chmod 600 "${KUBECONFIG_PATH}"

echo "==> Kubeconfig сохранён: ${KUBECONFIG_PATH}"
echo ""
echo "Для использования выполните одно из:"
echo "  export KUBECONFIG=${KUBECONFIG_PATH}"
echo "  # или добавьте в ~/.bashrc / ~/.zshrc"
echo ""
echo "Проверка подключения:"
KUBECONFIG="${KUBECONFIG_PATH}" kubectl get nodes
