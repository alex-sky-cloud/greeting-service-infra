#!/bin/bash
# =============================================================================
# get-kubeconfig.sh
# Получает kubeconfig из Terraform outputs и сохраняет локально.
# Запускать с локального ПК после terraform apply.
#
# Использование (из корня репозитория):
#   bash scripts/get-kubeconfig.sh
#
# Платформы: macOS, Ubuntu/WSL, Windows (Git Bash)
# =============================================================================

set -euo pipefail

KUBECONFIG_PATH="${HOME}/.kube/timeweb-greeting.yaml"
KUBECONFIG_MARKER="# greeting-service-infra: kubeconfig"
KUBECONFIG_EXPORT='export KUBECONFIG="$HOME/.kube/timeweb-greeting.yaml"'
SHELL_RC="${HOME}/.bashrc"
TERRAFORM_DIR="$(cd "$(dirname "$0")/../infra/terraform" && pwd)"

ensure_kubeconfig_in_shell_rc() {
  if [[ ! -f "${SHELL_RC}" ]]; then
    touch "${SHELL_RC}"
  fi

  if grep -qF "timeweb-greeting.yaml" "${SHELL_RC}"; then
    echo "==> KUBECONFIG уже прописан в ${SHELL_RC}"
    return
  fi

  {
    echo ""
    echo "${KUBECONFIG_MARKER}"
    echo "${KUBECONFIG_EXPORT}"
  } >> "${SHELL_RC}"

  echo "==> Добавлено в ${SHELL_RC}:"
  echo "    ${KUBECONFIG_EXPORT}"
}

activate_kubeconfig() {
  # shellcheck disable=SC1090
  source "${SHELL_RC}"
  export KUBECONFIG="${KUBECONFIG_PATH}"
}

command -v terraform >/dev/null 2>&1 || {
  echo "Ошибка: terraform не найден в PATH." >&2
  exit 1
}

command -v kubectl >/dev/null 2>&1 || {
  echo "Ошибка: kubectl не найден в PATH." >&2
  exit 1
}

mkdir -p "$(dirname "${KUBECONFIG_PATH}")"

cd "${TERRAFORM_DIR}"

echo "==> Каталог Terraform: ${TERRAFORM_DIR}"
echo "==> Получаем kubeconfig из Terraform..."
terraform output -raw kubeconfig > "${KUBECONFIG_PATH}"
chmod 600 "${KUBECONFIG_PATH}"

echo "==> Kubeconfig сохранён: ${KUBECONFIG_PATH}"
echo ""

ensure_kubeconfig_in_shell_rc
activate_kubeconfig

echo ""
echo "==> KUBECONFIG активен в этой сессии: ${KUBECONFIG}"
echo "    (в уже открытых окнах терминала выполните: source ~/.bashrc)"
echo ""
echo "==> Проверка подключения (kubectl get nodes):"
kubectl get nodes
