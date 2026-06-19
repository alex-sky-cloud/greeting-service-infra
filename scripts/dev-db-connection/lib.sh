#!/usr/bin/env bash
# ============================================================================
# lib.sh — общие функции для scripts/dev-db-connection/
#
# НАЗНАЧЕНИЕ: IP, пароли, find_psql, terraform output для удалённой PostgreSQL.
# Не запускается напрямую — подключается: source .../lib.sh
# ============================================================================
# Каталог этого набора скриптов и корень репозитория.
DEV_DB_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${DEV_DB_SCRIPT_DIR}/../.." && pwd)"
TERRAFORM_DIR="${REPO_ROOT}/infra/terraform"

# Значения по умолчанию (если terraform output недоступен).
# Актуальные IP после apply — см. 01-show-terraform-ips.sh
DB_HOST="${DB_HOST:-10.10.0.5}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-greeting_db}"
DB_USER="${DB_USER:-greeting_user}"
DEVTOOLS_PUBLIC_IP="${DEVTOOLS_PUBLIC_IP:-72.56.249.137}"
DEVTOOLS_PRIVATE_IP="${DEVTOOLS_PRIVATE_IP:-10.10.0.6}"
LOCAL_TUNNEL_PORT="${LOCAL_TUNNEL_PORT:-15432}"
SSH_KEY="${SSH_KEY:-${HOME}/.ssh/id_ed25519}"
VPC_CIDR="${VPC_CIDR:-10.10.0.0/24}"

load_shell_secrets() {
  # Пароль БД и TF_VAR_* часто лежат в ~/.bashrc (Git Bash / WSL).
  if [[ -f "${HOME}/.bashrc" ]]; then
    # shellcheck disable=SC1090
    source "${HOME}/.bashrc" 2>/dev/null || true
  fi
}

gitbash_path_to_wsl() {
  # /d/Project/foo -> /mnt/d/Project/foo
  local path="$1"
  if [[ "${path}" =~ ^/([a-zA-Z])/(.*)$ ]]; then
    local drive="${BASH_REMATCH[1]}"
    local rest="${BASH_REMATCH[2]}"
    printf '/mnt/%s/%s' "${drive,,}" "${rest}"
    return 0
  fi
  printf '%s' "${path}"
}

terraform_output_raw() {
  local name="$1"
  local wsl_tf_dir
  wsl_tf_dir="$(gitbash_path_to_wsl "${TERRAFORM_DIR}")"

  wsl -d Ubuntu bash -lc "
    source /home/sky/.profile 2>/dev/null || true
    source /mnt/c/Users/sky/.bashrc 2>/dev/null || true
    cd '${wsl_tf_dir}' && terraform output -raw '${name}' 2>/dev/null
  "
}

load_ips_from_terraform() {
  # Перезаписывает IP-переменные из terraform output (если terraform доступен).
  local host port public_ip private_ip jdbc

  host="$(terraform_output_raw db_host || true)"
  port="$(terraform_output_raw db_port || true)"
  public_ip="$(terraform_output_raw devtools_public_ip || true)"
  private_ip="$(terraform_output_raw devtools_private_ip || true)"

  [[ -n "${host}" ]] && DB_HOST="${host}"
  [[ -n "${port}" ]] && DB_PORT="${port}"
  [[ -n "${public_ip}" ]] && DEVTOOLS_PUBLIC_IP="${public_ip}"
  [[ -n "${private_ip}" ]] && DEVTOOLS_PRIVATE_IP="${private_ip}"
}

find_psql() {
  if command -v psql >/dev/null 2>&1; then
    command -v psql
    return 0
  fi
  local candidate
  for candidate in /c/Program\ Files/PostgreSQL/*/bin/psql.exe; do
    if [[ -x "${candidate}" ]]; then
      echo "${candidate}"
      return 0
    fi
  done
  return 1
}

print_connection_summary() {
  cat <<EOF
VPC (приватная сеть)     : ${VPC_CIDR}
PostgreSQL (приватный)   : ${DB_HOST}:${DB_PORT}  — только внутри VPC
Devtools (приватный)     : ${DEVTOOLS_PRIVATE_IP} — VPS в той же VPC
Devtools (публичный SSH) : ${DEVTOOLS_PUBLIC_IP}:22 — jump-хост из интернета
Локальный порт туннеля   : ${LOCAL_TUNNEL_PORT}     — localhost на вашем ПК
База / пользователь      : ${DB_NAME} / ${DB_USER}
EOF
}
