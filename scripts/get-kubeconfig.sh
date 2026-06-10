#!/bin/bash
# =============================================================================
# get-kubeconfig.sh
# Получает kubeconfig из Terraform outputs и сохраняет локально.
# Запускать с локального ПК после terraform apply.
#
# Использование (из корня репозитория, WSL Ubuntu):
#   bash scripts/get-kubeconfig.sh
#
# Файл kubeconfig — в профиле Windows (не в ~/.kube WSL):
#   C:\Users\sky\.kube\timeweb-greeting.yaml
#   /mnt/c/Users/sky/.kube/timeweb-greeting.yaml
# =============================================================================

set -euo pipefail

# Единый каталог конфигов на диске C: (как ~/.ssh и ключ в registry_server.tf)
KUBECONFIG_PATH="/mnt/c/Users/sky/.kube/timeweb-greeting.yaml"
TERRAFORM_DIR="$(cd "$(dirname "$0")/../infra/terraform" && pwd)"

command -v terraform >/dev/null 2>&1 || {
  echo "Ошибка: terraform не найден в PATH." >&2
  exit 1
}

mkdir -p "$(dirname "${KUBECONFIG_PATH}")"

cd "${TERRAFORM_DIR}"

echo "==> Каталог Terraform: ${TERRAFORM_DIR}"
echo "==> Получаем kubeconfig из Terraform..."
terraform output -raw kubeconfig > "${KUBECONFIG_PATH}"
chmod 600 "${KUBECONFIG_PATH}" 2>/dev/null || true

echo "==> Kubeconfig сохранён:"
echo "    Windows: C:\\Users\\sky\\.kube\\timeweb-greeting.yaml"
echo "    WSL:     ${KUBECONFIG_PATH}"
echo ""

if command -v kubectl >/dev/null 2>&1; then
  echo "==> Проверка (kubectl get nodes):"
  kubectl --kubeconfig "${KUBECONFIG_PATH}" get nodes
else
  echo "==> kubectl не в PATH этой сессии WSL."
  echo "    Файл уже на диске C: — используйте из Windows или явно:"
  echo "    kubectl --kubeconfig ${KUBECONFIG_PATH} get nodes"
fi
