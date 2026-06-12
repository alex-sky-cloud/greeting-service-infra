#!/bin/bash
# =============================================================================
# get-kubeconfig.sh
# Получает kubeconfig из Terraform outputs и сохраняет локально.
#
# Использование (из корня репозитория):
#   WSL:      bash scripts/get-kubeconfig.sh
#   Git Bash: bash scripts/get-kubeconfig.sh
#
# Путь к файлу определяется автоматически:
#   WSL      → /mnt/c/Users/<WindowsUser>/.kube/timeweb-greeting.yaml
#   Git Bash → C:/Users/<User>/.kube/timeweb-greeting.yaml
#   Linux    → ~/.kube/timeweb-greeting.yaml
#
# Переопределить путь: export KUBECONFIG=/path/to/file.yaml
# =============================================================================

set -euo pipefail

KUBECONFIG_FILENAME="timeweb-greeting.yaml"
TERRAFORM_DIR="$(cd "$(dirname "$0")/../infra/terraform" && pwd)"

# Определяет путь сохранения kubeconfig в зависимости от среды запуска.
resolve_kubeconfig_path() {
  if [[ -n "${KUBECONFIG:-}" ]]; then
    echo "${KUBECONFIG}"
    return 0
  fi

  local win_user

  # WSL: профиль Windows под /mnt/c
  if [[ -r /proc/version ]] && grep -qiE 'microsoft|wsl' /proc/version; then
    if command -v cmd.exe >/dev/null 2>&1; then
      win_user="$(cmd.exe /c 'echo %USERNAME%' 2>/dev/null | tr -d '\r\n')"
    fi
    win_user="${WIN_USER:-${win_user:-${USER:-sky}}}"
    echo "/mnt/c/Users/${win_user}/.kube/${KUBECONFIG_FILENAME}"
    return 0
  fi

  # Git Bash / MSYS / Cygwin (локальный ПК Windows)
  if [[ -n "${USERPROFILE:-}" ]]; then
    echo "${USERPROFILE//\\//}/.kube/${KUBECONFIG_FILENAME}"
    return 0
  fi
  if [[ "${OSTYPE:-}" == "msys" || "${OSTYPE:-}" == "cygwin" ]]; then
    echo "/c/Users/${USERNAME:-${USER:-sky}}/.kube/${KUBECONFIG_FILENAME}"
    return 0
  fi

  # Linux / macOS без WSL
  echo "${HOME}/.kube/${KUBECONFIG_FILENAME}"
}

KUBECONFIG_PATH="$(resolve_kubeconfig_path)"

command -v terraform >/dev/null 2>&1 || {
  echo "Ошибка: terraform не найден в PATH." >&2
  exit 1
}

mkdir -p "$(dirname "${KUBECONFIG_PATH}")"

cd "${TERRAFORM_DIR}"

echo "==> Среда: $(uname -s 2>/dev/null || echo unknown)"
echo "==> Каталог Terraform: ${TERRAFORM_DIR}"
echo "==> Получаем kubeconfig из Terraform..."
terraform output -raw kubeconfig > "${KUBECONFIG_PATH}"
chmod 600 "${KUBECONFIG_PATH}" 2>/dev/null || true

echo "==> Kubeconfig сохранён: ${KUBECONFIG_PATH}"
echo ""
echo "    export KUBECONFIG=${KUBECONFIG_PATH}"
echo ""

if command -v kubectl >/dev/null 2>&1; then
  echo "==> Проверка (kubectl get nodes):"
  kubectl --kubeconfig "${KUBECONFIG_PATH}" get nodes
else
  echo "==> kubectl не в PATH этой сессии."
  echo "    kubectl --kubeconfig \"${KUBECONFIG_PATH}\" get nodes"
fi
