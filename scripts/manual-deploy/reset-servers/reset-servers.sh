#!/usr/bin/env bash
# ============================================================================
# reset-servers.sh
# НАЗНАЧЕНИЕ: откат prepare-server + k3s на VPS. SSH-ключи не трогаем.
# ГДЕ:       Windows — только Git Bash (не WSL, не PowerShell).
# ЗАПУСК:    bash scripts/manual-deploy/reset-servers/reset-servers.sh
#            bash scripts/manual-deploy/reset-servers/reset-servers.sh k8s-master
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
REMOTE_SCRIPT="$SCRIPT_DIR/reset-servers-remote.sh"
ENV_FILE="$REPO_ROOT/infra-servers.env"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_ed25519}"

load_infra_env() {
  local env_file="$1"
  if [[ ! -f "$env_file" ]]; then
    echo "Не найден $env_file" >&2
    return 1
  fi
  # shellcheck source=/dev/null
  source <(tr -d '\r' < "$env_file")
}

upsert_env_var() {
  local env_file="$1"
  local key="$2"
  local value="$3"
  local tmp
  tmp="$(mktemp)"
  tr -d '\r' < "$env_file" | awk -v k="$key" -v v="$value" '
    BEGIN { done=0 }
    $0 ~ ("^" k "=") {
      print k "=" v
      done=1
      next
    }
    { print }
    END {
      if (!done) {
        print ""
        print "# " k
        print k "=" v
      }
    }
  ' > "$tmp"
  cp "$tmp" "$env_file"
  rm -f "$tmp"
}

ip_is_missing() {
  local ip="${1:-}"
  [[ -z "$ip" || "$ip" == REPLACE_ME* || "$ip" == \<* ]]
}

print_source_reminder() {
  cat <<'EOF'

---------------------------------------------------------------------
  Не забывайте source в ЭТОЙ сессии Git Bash перед ручными ssh:

    source <(tr -d '\r' < ./infra-servers.env)

  Этот скрипт подхватывает env сам.
---------------------------------------------------------------------

EOF
}

case "${OSTYPE:-}" in
  msys*|mingw*)
    :
    ;;
  linux-gnu*)
    if grep -qi microsoft /proc/version 2>/dev/null; then
      echo "Обнаружен WSL. Этот скрипт — только Git Bash на Windows, не WSL." >&2
      exit 1
    fi
    ;;
esac

ROLE=""
HOST=""
DRY_RUN=0
NONINTERACTIVE=0
ALL_ROLES=(
  devtools
  k8s-master
  k8s-worker-1
  k8s-worker-2
  traefik-1
  traefik-2
  storage-1
  storage-2
)

usage() {
  cat <<'EOF'
Использование:
  bash scripts/manual-deploy/reset-servers/reset-servers.sh [ROLE] [опции]

Без ROLE обходит все роли из infra-servers.env (как prepare-server.sh).

Снимает с VPS всё, что ставили prepare-server и k3s:
  k3s uninstall, ufw, git, docker (на devtools/traefik/storage), hostname.

SSH-ключи в /root/.ssh/authorized_keys НЕ удаляются.

После сброса k8s-master локально очищает K3S_TOKEN в infra-servers.env.

Опции:
  --host IP    явный IP (только вместе с ROLE)
  --dry-run    показать шаги без изменений на сервере
  --yes        без пауз Enter на remote (пауза «нет VPS» остаётся)
  -h, --help   эта справка

Примеры:
  bash scripts/manual-deploy/reset-servers/reset-servers.sh
  bash scripts/manual-deploy/reset-servers/reset-servers.sh k8s-master
  bash scripts/manual-deploy/reset-servers/reset-servers.sh k8s-worker-1 --yes
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --host)
      HOST="${2:?--host требует IP}"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --yes)
      NONINTERACTIVE=1
      shift
      ;;
    -*)
      echo "Неизвестная опция: $1" >&2
      usage >&2
      exit 1
      ;;
    *)
      if [[ -z "$ROLE" ]]; then
        ROLE="$1"
      else
        echo "Лишний аргумент: $1" >&2
        exit 1
      fi
      shift
      ;;
  esac
done

if [[ -n "$HOST" && -z "$ROLE" ]]; then
  echo "Опция --host нужна вместе с ролью." >&2
  exit 1
fi

if [[ -n "$ROLE" ]]; then
  case "$ROLE" in
    devtools|k8s-master|k3s-server|k3s-master|k8s-worker-1|k8s-worker-2|k3s-worker-1|k3s-worker-2|traefik-1|traefik-2|storage-1|storage-2)
      ;;
    *)
      if [[ -z "$HOST" ]]; then
        echo "Неизвестная роль «${ROLE}»." >&2
        echo "Допустимо: ${ALL_ROLES[*]}" >&2
        exit 1
      fi
      ;;
  esac
fi

if [[ ! -f "$REMOTE_SCRIPT" ]]; then
  echo "Не найден $REMOTE_SCRIPT" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Не найден $ENV_FILE" >&2
  exit 1
fi

if [[ ! -f "$SSH_KEY" ]]; then
  echo "SSH-ключ не найден: $SSH_KEY" >&2
  exit 1
fi

load_infra_env "$ENV_FILE"
print_source_reminder

resolve_host_from_role() {
  case "$1" in
    devtools) echo "${DEVTOOLS_IP:-}" ;;
    k8s-master|k3s-server|k3s-master)
      echo "${K8S_MASTER_IP:-${K3S_SERVER_IP:-}}"
      ;;
    k8s-worker-1|k3s-worker-1) echo "${K3S_WORKER_1_IP:-}" ;;
    k8s-worker-2|k3s-worker-2) echo "${K3S_WORKER_2_IP:-}" ;;
    traefik-1) echo "${TRAEFIK_1_IP:-${TRAEFIK_ENTRY_IP:-}}" ;;
    traefik-2) echo "${TRAEFIK_2_IP:-}" ;;
    storage-1) echo "${STORAGE_1_IP:-}" ;;
    storage-2) echo "${STORAGE_2_IP:-}" ;;
    *) echo "" ;;
  esac
}

env_var_for_role() {
  case "$1" in
    devtools) echo "DEVTOOLS_IP" ;;
    k8s-master|k3s-server|k3s-master) echo "K3S_SERVER_IP / K8S_MASTER_IP" ;;
    k8s-worker-1|k3s-worker-1) echo "K3S_WORKER_1_IP" ;;
    k8s-worker-2|k3s-worker-2) echo "K3S_WORKER_2_IP" ;;
    traefik-1) echo "TRAEFIK_1_IP / TRAEFIK_ENTRY_IP" ;;
    traefik-2) echo "TRAEFIK_2_IP" ;;
    storage-1) echo "STORAGE_1_IP" ;;
    storage-2) echo "STORAGE_2_IP" ;;
    *) echo "" ;;
  esac
}

is_k8s_master_role() {
  case "$1" in
    k8s-master|k3s-server|k3s-master) return 0 ;;
    *) return 1 ;;
  esac
}

wait_retry_or_skip() {
  echo
  echo "Когда VPS готов и infra-servers.env сохранён — нажмите Enter."
  echo "Скрипт сам прочитает env и повторит проверку."
  echo "Чтобы пропустить эту роль — введите s и нажмите Enter."
  echo
  local choice=""
  if [[ -r /dev/tty ]]; then
    read -r -p "Enter — повторить, s — пропустить: " choice </dev/tty
  else
    read -r -p "Enter — повторить, s — пропустить: " choice
  fi
  echo
  if [[ "$choice" == "s" || "$choice" == "S" ]]; then
    return 1
  fi
  return 0
}

ssh_probe() {
  local ip="$1"
  ssh -i "$SSH_KEY" \
    -o ConnectTimeout=12 \
    -o BatchMode=yes \
    -o StrictHostKeyChecking=accept-new \
    "root@${ip}" \
    "echo connected; hostname"
}

run_remote() {
  local role="$1"
  local ip="$2"

  echo "=== reset-servers.sh ==="
  echo "Роль:   $role"
  echo "Хост:   root@${ip}"
  echo "Ключ:   $SSH_KEY"
  echo "Режим:  dry-run=$DRY_RUN yes=$NONINTERACTIVE"
  echo

  if [[ "$DRY_RUN" == "1" ]]; then
    echo "[dry-run] SSH root@${ip} reset-servers-remote.sh ${role}"
    return 0
  fi

  echo "--- SSH reset на root@${ip} ---"
  ssh -i "$SSH_KEY" \
    -o ConnectTimeout=15 \
    -o StrictHostKeyChecking=accept-new \
    "root@${ip}" \
    env DRY_RUN="$DRY_RUN" NONINTERACTIVE="$NONINTERACTIVE" \
    bash -s -- "$role" \
    < "$REMOTE_SCRIPT"
}

clear_local_k3s_artifacts() {
  local token_paths=(
    "$REPO_ROOT/k3s-node-token"
    "$REPO_ROOT/scripts/manual-deploy/k3s-master/k3s-node-token"
  )
  local p
  for p in "${token_paths[@]}"; do
    if [[ -f "$p" ]]; then
      rm -f "$p"
      echo "Удалён локальный файл: $p"
    fi
  done
  upsert_env_var "$ENV_FILE" "K3S_TOKEN" ""
  echo "K3S_TOKEN очищен в ${ENV_FILE}"
}

wait_until_ready() {
  local role="$1"
  local forced_ip="${2:-}"
  local env_name ip

  env_name="$(env_var_for_role "$role")"

  while true; do
    load_infra_env "$ENV_FILE"
    if [[ -n "$forced_ip" ]]; then
      ip="$forced_ip"
    else
      ip="$(resolve_host_from_role "$role")"
    fi

    if ip_is_missing "$ip"; then
      echo
      echo "================================================================"
      echo "  Роль «${role}» ещё не готова (нет IP в env)."
      echo "================================================================"
      echo "  ${env_name}=REPLACE_ME или пусто."
      if wait_retry_or_skip; then
        continue
      fi
      echo "Роль «${role}» пропущена."
      return 2
    fi

    echo
    echo "--- Проверка SSH: root@${ip} (${role}) ---"
    if ssh_probe "$ip"; then
      echo "--- Результат: OK — SSH работает ---"
      HOST_READY="$ip"
      return 0
    fi

    echo
    echo "================================================================"
    echo "  Роль «${role}» (${env_name}=${ip}) не отвечает по SSH."
    echo "================================================================"
    if wait_retry_or_skip; then
      continue
    fi
    echo "Роль «${role}» пропущена."
    return 2
  done
}

print_plan() {
  local role ip note
  echo "План сброса по infra-servers.env:"
  for role in "${ALL_ROLES[@]}"; do
    ip="$(resolve_host_from_role "$role")"
    if ip_is_missing "$ip"; then
      note="ещё нет IP"
    else
      note="$ip"
    fi
    printf "  %-14s %s\n" "$role" "$note"
  done
  echo
}

reset_role() {
  local role="$1"
  local forced_ip="${2:-}"
  local status=0

  echo
  echo "################################################################"
  echo "#  reset: ${role}"
  echo "################################################################"

  wait_until_ready "$role" "$forced_ip" || status=$?
  if [[ "$status" -eq 2 ]]; then
    return 0
  fi
  if [[ "$status" -ne 0 ]]; then
    return "$status"
  fi

  run_remote "$role" "$HOST_READY"

  if [[ "$DRY_RUN" != "1" ]] && is_k8s_master_role "$role"; then
    echo
    echo "--- Локально: очистка K3S_TOKEN и k3s-node-token ---"
    clear_local_k3s_artifacts
  fi
}

echo "=== reset-servers.sh ==="
echo "Env:    $ENV_FILE"
echo "Ключ:   $SSH_KEY"
echo "Режим:  dry-run=$DRY_RUN yes=$NONINTERACTIVE"
echo
echo "Внимание: снимает k3s, ufw, git, docker (где ставили). SSH-ключи сохраняются."
echo

if [[ -z "$ROLE" ]]; then
  print_plan
  for ROLE_ITEM in "${ALL_ROLES[@]}"; do
    reset_role "$ROLE_ITEM"
  done
  echo
  echo "=== Все доступные роли из env обработаны ==="
else
  reset_role "$ROLE" "$HOST"
  echo
  echo "=== Локально: роль ${ROLE} сброшена ==="
fi

print_source_reminder
