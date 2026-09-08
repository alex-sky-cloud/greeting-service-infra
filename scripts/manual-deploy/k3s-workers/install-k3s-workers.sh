#!/usr/bin/env bash
# ============================================================================
# install-k3s-workers.sh
# НАЗНАЧЕНИЕ: фаза 2 — join k3s agent на worker-1 и worker-2 через SSH.
# ГДЕ:       Windows — только Git Bash (не WSL, не PowerShell).
# ЗАПУСК:    bash scripts/manual-deploy/k3s-workers/install-k3s-workers.sh
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
REMOTE_SCRIPT="$SCRIPT_DIR/install-k3s-workers-remote.sh"
ENV_FILE="$REPO_ROOT/infra-servers.env"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_ed25519}"
LAUNCH_DIR="$(pwd)"
TOKEN_NAME="k3s-node-token"

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
  if [[ ! -f "$env_file" ]]; then
    echo "Не найден $env_file" >&2
    return 1
  fi
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
        print "# " k " — пишет скрипт k3s, не коммитить"
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
      echo "Откройте Git Bash и запустите оттуда." >&2
      exit 1
    fi
    ;;
esac

DRY_RUN=0
NONINTERACTIVE=0
ONLY_ROLE=""

usage() {
  cat <<'EOF'
Использование:
  bash scripts/manual-deploy/k3s-workers/install-k3s-workers.sh [опции]

Берёт из infra-servers.env:
  K8S_MASTER_IP / K3S_SERVER_IP / DEVTOOLS_IP — куда join (K3S_URL)
  K3S_TOKEN                                 — токен с master
  K3S_WORKER_1_IP, K3S_WORKER_2_IP          — воркеры

Если IP воркера REPLACE_ME или SSH не отвечает — скрипт НЕ падает:
пишет, что VPS, возможно, не запущен, ждёт любую клавишу,
заново читает env и пробует снова.

Опции:
  --only worker-1|worker-2   только один воркер
  --dry-run                  показать шаги без установки
  --yes                      без Enter на remote (пауза «нет VPS» всё равно есть)
  -h, --help                 эта справка
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --only)
      ONLY_ROLE="${2:?--only требует worker-1 или worker-2}"
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
      echo "Лишний аргумент: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -n "$ONLY_ROLE" && "$ONLY_ROLE" != "worker-1" && "$ONLY_ROLE" != "worker-2" ]]; then
  echo " --only принимает только worker-1 или worker-2" >&2
  exit 1
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

wait_any_key() {
  echo
  echo "Когда VPS готов и infra-servers.env сохранён — нажмите любую клавишу."
  echo "Скрипт сам сделает source env и продолжит."
  echo
  if [[ -r /dev/tty ]]; then
    read -n 1 -s -r -p "Нажмите любую клавишу..." _ </dev/tty
  else
    read -n 1 -s -r -p "Нажмите любую клавишу..." _
  fi
  echo
  echo
}

# Если токена нет в env — взять из файла или с master, затем записать в env.
ensure_k3s_token() {
  load_infra_env "$ENV_FILE"
  if [[ -n "${K3S_TOKEN:-}" ]]; then
    return 0
  fi

  local candidate token
  for candidate in \
    "${LAUNCH_DIR}/${TOKEN_NAME}" \
    "${SCRIPT_DIR}/../k3s-master/${TOKEN_NAME}" \
    "${REPO_ROOT}/${TOKEN_NAME}"
  do
    if [[ -f "$candidate" ]]; then
      token="$(tr -d '\r\n' < "$candidate")"
      if [[ -n "$token" ]]; then
        upsert_env_var "$ENV_FILE" "K3S_TOKEN" "$token"
        load_infra_env "$ENV_FILE"
        echo "K3S_TOKEN записан в env из файла (значение скрыто)."
        return 0
      fi
    fi
  done

  local master_ip="${K8S_MASTER_IP:-${K3S_SERVER_IP:-${DEVTOOLS_IP:-}}}"
  if ip_is_missing "${master_ip:-}"; then
    echo "Нет K3S_TOKEN в env и нет IP master, чтобы скачать node-token." >&2
    return 1
  fi

  echo "K3S_TOKEN в env пуст — копирую /root/${TOKEN_NAME} с master ${master_ip}..."
  local tmp
  tmp="$(mktemp)"
  if scp -i "$SSH_KEY" \
    -o ConnectTimeout=15 \
    -o StrictHostKeyChecking=accept-new \
    "root@${master_ip}:/root/${TOKEN_NAME}" \
    "$tmp"
  then
    token="$(tr -d '\r\n' < "$tmp")"
    rm -f "$tmp"
    if [[ -z "$token" ]]; then
      echo "ОШИБКА: файл токена на master пустой." >&2
      return 1
    fi
    upsert_env_var "$ENV_FILE" "K3S_TOKEN" "$token"
    load_infra_env "$ENV_FILE"
    echo "K3S_TOKEN записан в env с master (значение скрыто)."
    return 0
  fi
  rm -f "$tmp"
  echo "ОШИБКА: нет K3S_TOKEN. Сначала прогоните install-k3s-master.sh." >&2
  return 1
}

WORKER_IP=""

wait_for_worker() {
  local role="$1"
  local var="$2"
  local ip
  WORKER_IP=""

  while true; do
    load_infra_env "$ENV_FILE"
    ip="${!var}"

    if ip_is_missing "$ip"; then
      echo
      echo "================================================================"
      echo "  Worker «${role}» ещё не готов (нет IP в env)."
      echo "================================================================"
      echo
      echo "Посмотрите внимательно:"
      echo "  • возможно, VPS «${role}» ещё не заказан или не запущен в панели;"
      echo "  • в ${ENV_FILE} сейчас ${var}=REPLACE_ME (или пусто);"
      echo "  • SSH-ключ на этот VPS ещё не прописан."
      echo
      echo "Что сделать:"
      echo "  1. Создайте / запустите VPS и привяжите ключ ~/.ssh/id_ed25519."
      echo "  2. Запишите публичный IP в строку ${var}=... в infra-servers.env"
      echo "  3. Сохраните файл."
      wait_any_key
      continue
    fi

    echo
    echo "--- Проверка SSH: root@${ip} (${role}) ---"
    echo "Команда:"
    echo "  ssh -i ${SSH_KEY} root@${ip} \"echo connected; hostname\""
    echo "----- вывод -----"
    if ssh -i "$SSH_KEY" \
      -o ConnectTimeout=12 \
      -o BatchMode=yes \
      -o StrictHostKeyChecking=accept-new \
      "root@${ip}" \
      "echo connected; hostname"
    then
      echo "----- конец -----"
      echo "--- Результат: OK — SSH работает ---"
      WORKER_IP="$ip"
      return 0
    fi
    echo "----- конец -----"
    echo
    echo "================================================================"
    echo "  Worker «${role}» (${var}=${ip}) не отвечает по SSH."
    echo "================================================================"
    echo
    echo "Посмотрите внимательно:"
    echo "  • VPS выключен или ещё устанавливается;"
    echo "  • IP в env устарел — поправьте ${var} в infra-servers.env;"
    echo "  • ключ не добавлен в панель / на сервер."
    echo
    echo "Когда поправите VPS и сохраните env — скрипт продолжит с этого воркера."
    wait_any_key
  done
}

join_worker() {
  local role="$1"
  local var="$2"
  local node_name="$3"
  local ip master_ip k3s_url

  echo
  echo "################################################################"
  echo "#  ${role}"
  echo "################################################################"

  wait_for_worker "$role" "$var"
  ip="$WORKER_IP"

  load_infra_env "$ENV_FILE"
  master_ip="${K8S_MASTER_IP:-${K3S_SERVER_IP:-${DEVTOOLS_IP:-}}}"
  if ip_is_missing "${master_ip:-}"; then
    echo "Не задан IP master (K8S_MASTER_IP / K3S_SERVER_IP / DEVTOOLS_IP)." >&2
    exit 1
  fi
  k3s_url="https://${master_ip}:6443"

  ensure_k3s_token

  echo
  echo "Join ${role} (${ip}) → ${k3s_url} как ${node_name}"
  echo "K3S_TOKEN берётся из infra-servers.env (на экран не печатаем)."
  echo

  if [[ "$DRY_RUN" == "1" ]]; then
    echo "(dry-run) на ${ip} была бы установка agent с K3S_URL=${k3s_url}"
    return 0
  fi

  ssh -i "$SSH_KEY" \
    -o ConnectTimeout=15 \
    -o StrictHostKeyChecking=accept-new \
    "root@${ip}" \
    env DRY_RUN="$DRY_RUN" \
        NONINTERACTIVE="$NONINTERACTIVE" \
        K3S_URL="$k3s_url" \
        K3S_TOKEN="$K3S_TOKEN" \
        K3S_NODE_NAME="$node_name" \
    bash -s -- \
    < "$REMOTE_SCRIPT"
}

echo "=== install-k3s-workers.sh (фаза 2, agents) ==="
echo "Env:         $ENV_FILE"
echo "Ключ:        $SSH_KEY"
echo "Режим:       dry-run=${DRY_RUN} yes=${NONINTERACTIVE} only=${ONLY_ROLE:-оба}"
echo

ensure_k3s_token

if [[ -z "$ONLY_ROLE" || "$ONLY_ROLE" == "worker-1" ]]; then
  join_worker "k8s-worker-1" "K3S_WORKER_1_IP" "k8s-worker-1"
fi

if [[ -z "$ONLY_ROLE" || "$ONLY_ROLE" == "worker-2" ]]; then
  join_worker "k8s-worker-2" "K3S_WORKER_2_IP" "k8s-worker-2"
fi

load_infra_env "$ENV_FILE"
MASTER_IP="${K8S_MASTER_IP:-${K3S_SERVER_IP:-${DEVTOOLS_IP:-}}}"

echo
echo "--- На master: kubectl get nodes -o wide ---"
if [[ "$DRY_RUN" == "1" ]]; then
  echo "(dry-run — проверка на master пропущена)"
elif ip_is_missing "${MASTER_IP:-}"; then
  echo "IP master неизвестен — проверьте ноды вручную."
else
  echo "Команда:"
  echo "  ssh -i ${SSH_KEY} root@${MASTER_IP} \"kubectl get nodes -o wide\""
  echo "----- вывод -----"
  if ssh -i "$SSH_KEY" \
    -o ConnectTimeout=15 \
    -o StrictHostKeyChecking=accept-new \
    "root@${MASTER_IP}" \
    "if command -v kubectl >/dev/null; then kubectl get nodes -o wide; else k3s kubectl get nodes -o wide; fi"
  then
    echo "----- конец -----"
    echo "--- Результат: OK ---"
  else
    echo "----- конец -----"
    echo "Проверка на master не удалась — зайдите сами и выполните kubectl get nodes."
  fi
fi

print_source_reminder

echo "=== Локально: install-k3s-workers.sh завершён ==="
