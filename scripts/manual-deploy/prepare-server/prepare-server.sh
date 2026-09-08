#!/usr/bin/env bash
# ============================================================================
# prepare-server.sh
# НАЗНАЧЕНИЕ: подготовка VPS (§7) с локального ПК через SSH.
# ГДЕ:       Windows — только Git Bash (не WSL, не PowerShell).
# ЗАПУСК:    bash scripts/manual-deploy/prepare-server/prepare-server.sh
#            bash scripts/manual-deploy/prepare-server/prepare-server.sh devtools
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
REMOTE_SCRIPT="$SCRIPT_DIR/prepare-server-remote.sh"
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
      echo "Обнаружен WSL. Этот скрипт рассчитан на Git Bash на Windows, не на WSL." >&2
      echo "Откройте Git Bash и запустите команду оттуда." >&2
      exit 1
    fi
    ;;
esac

ROLE=""
HOST=""
DOCKER_FLAG="--auto"
DRY_RUN=0
SKIP_UPGRADE=0
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
  bash scripts/manual-deploy/prepare-server/prepare-server.sh [ROLE] [опции]

Без ROLE скрипт обходит все роли из infra-servers.env:
  devtools, k8s-master, k8s-worker-1, k8s-worker-2,
  traefik-1, traefik-2, storage-1, storage-2

Готовность сервера проверяет SSH (не ping): у облака ICMP часто закрыт,
а SSH при этом работает. Если IP ещё REPLACE_ME или SSH не отвечает —
скрипт не падает: пишет подсказку, ждёт Enter, заново читает env.

Один и тот же IP на двух ролях (например DEVTOOLS_IP = K3S_SERVER_IP)
обрабатывается один раз. Вторую роль пропускает: на одной машине
нельзя задать два hostname.

Опции:
  --host IP           явный IP (только вместе с ROLE)
  --with-docker       установить Docker даже на k8s-нодах
  --no-docker         не ставить Docker даже на devtools
  --skip-upgrade      пропустить apt-get upgrade (быстрый повтор)
  --dry-run           показать шаги без изменений на сервере
  --yes               без ожидания Enter на remote (пауза «нет VPS» остаётся)
  -h, --help          эта справка

Примеры:
  bash scripts/manual-deploy/prepare-server/prepare-server.sh
  bash scripts/manual-deploy/prepare-server/prepare-server.sh --skip-upgrade
  bash scripts/manual-deploy/prepare-server/prepare-server.sh devtools
  bash scripts/manual-deploy/prepare-server/prepare-server.sh traefik-1 --host 203.0.113.10
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
    --with-docker)
      DOCKER_FLAG="--with-docker"
      shift
      ;;
    --no-docker)
      DOCKER_FLAG="--no-docker"
      shift
      ;;
    --skip-upgrade)
      SKIP_UPGRADE=1
      shift
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
  echo "Опция --host нужна вместе с ролью, например: prepare-server.sh traefik-1 --host 1.2.3.4" >&2
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
        echo "Или укажите IP: --host 1.2.3.4" >&2
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
  echo "Не найден $ENV_FILE — создайте файл с IP серверов." >&2
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
    *)
      echo ""
      ;;
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

# Возврат 0 = повторить, 1 = пропустить роль.
wait_retry_or_skip() {
  echo
  echo "Когда VPS готов и infra-servers.env сохранён — нажмите Enter."
  echo "Скрипт сам прочитает env и повторит проверку."
  echo "Чтобы пропустить эту роль и идти дальше — введите s и нажмите Enter."
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

  echo "=== prepare-server.sh ==="
  echo "Роль:   $role"
  echo "Хост:   root@${ip}"
  echo "Ключ:   $SSH_KEY"
  echo "Docker: $DOCKER_FLAG"
  echo "Режим:  dry-run=$DRY_RUN skip-upgrade=$SKIP_UPGRADE yes=$NONINTERACTIVE"
  echo

  if [[ "$DRY_RUN" == "1" ]]; then
    echo "(dry-run) на ${ip} была бы подготовка роли ${role}"
    echo
    echo "=== Локально: пропуск изменений для ${role} @ ${ip} ==="
    return 0
  fi

  ssh -i "$SSH_KEY" \
    -o ConnectTimeout=15 \
    -o StrictHostKeyChecking=accept-new \
    "root@${ip}" \
    env DRY_RUN="$DRY_RUN" SKIP_UPGRADE="$SKIP_UPGRADE" NONINTERACTIVE="$NONINTERACTIVE" \
    bash -s -- "$role" "$DOCKER_FLAG" \
    < "$REMOTE_SCRIPT"

  echo
  echo "=== Локально: prepare-server.sh завершён для ${role} @ ${ip} ==="
}

# Возврат 0 = готовы к remote (HOST_READY), 2 = роль пропущена.
HOST_READY=""
declare -A SEEN_IPS=()

wait_until_ready() {
  local role="$1"
  local forced_ip="${2:-}"
  local ip env_name

  HOST_READY=""
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
      echo
      echo "Посмотрите внимательно:"
      echo "  • возможно, VPS «${role}» ещё не заказан или не запущен;"
      echo "  • в ${ENV_FILE} переменная ${env_name} сейчас REPLACE_ME или пустая;"
      echo "  • SSH-ключ на этот VPS ещё не прописан."
      echo
      echo "Что сделать:"
      echo "  1. Создайте / запустите VPS и привяжите ключ ~/.ssh/id_ed25519."
      echo "  2. Запишите публичный IP в infra-servers.env (${env_name})."
      echo "  3. Сохраните файл."
      if wait_retry_or_skip; then
        continue
      fi
      echo "Роль «${role}» пропущена."
      return 2
    fi

    if [[ -n "${SEEN_IPS[$ip]:-}" ]]; then
      echo
      echo "Пропуск «${role}»: адрес ${ip} уже обработан как «${SEEN_IPS[$ip]}»."
      echo "На одном VPS нельзя поставить два hostname. Если нужен отдельный сервер —"
      echo "запишите другой IP в ${env_name}."
      return 2
    fi

    echo
    echo "--- Проверка SSH: root@${ip} (${role}) ---"
    echo "Команда:"
    echo "  ssh -i ${SSH_KEY} root@${ip} \"echo connected; hostname\""
    echo "----- вывод -----"
    if ssh_probe "$ip"; then
      echo "----- конец -----"
      echo "--- Результат: OK — SSH работает ---"
      HOST_READY="$ip"
      return 0
    fi
    echo "----- конец -----"
    echo
    echo "================================================================"
    echo "  Роль «${role}» (${env_name}=${ip}) не отвечает по SSH."
    echo "================================================================"
    echo
    echo "Посмотрите внимательно:"
    echo "  • VPS выключен или ещё устанавливается;"
    echo "  • IP в env устарел — поправьте ${env_name};"
    echo "  • ключ не добавлен в панель / на сервер."
    echo
    echo "Ping здесь специально не используем: у многих VPS ICMP закрыт,"
    echo "а SSH при этом уже работает. Готовность = вход по ключу."
    if wait_retry_or_skip; then
      continue
    fi
    echo "Роль «${role}» пропущена."
    return 2
  done
}

print_plan() {
  local role ip note
  echo "План по infra-servers.env:"
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

prepare_role() {
  local role="$1"
  local forced_ip="${2:-}"
  local status=0

  echo
  echo "################################################################"
  echo "#  ${role}"
  echo "################################################################"

  wait_until_ready "$role" "$forced_ip" || status=$?
  if [[ "$status" -eq 2 ]]; then
    return 0
  fi
  if [[ "$status" -ne 0 ]]; then
    return "$status"
  fi

  run_remote "$role" "$HOST_READY"
  SEEN_IPS["$HOST_READY"]="$role"
}

echo "=== prepare-server.sh (§7) ==="
echo "Env:    $ENV_FILE"
echo "Ключ:   $SSH_KEY"
echo "Docker: $DOCKER_FLAG"
echo "Режим:  dry-run=$DRY_RUN skip-upgrade=$SKIP_UPGRADE yes=$NONINTERACTIVE"
echo

if [[ -z "$ROLE" ]]; then
  print_plan
  for ROLE_ITEM in "${ALL_ROLES[@]}"; do
    prepare_role "$ROLE_ITEM"
  done
  echo
  echo "=== Все доступные роли из env обработаны ==="
else
  prepare_role "$ROLE" "$HOST"
  echo
  echo "=== Локально: роль ${ROLE} обработана ==="
fi

print_source_reminder
