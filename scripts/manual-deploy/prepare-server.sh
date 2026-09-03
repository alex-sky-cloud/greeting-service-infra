#!/usr/bin/env bash
# ============================================================================
# prepare-server.sh
# НАЗНАЧЕНИЕ: запуск подготовки VPS (§7) с локального ПК через SSH.
# ГДЕ:       Windows — только Git Bash (не WSL, не PowerShell).
#            macOS / Linux — обычный bash.
# ЗАПУСК:    bash scripts/manual-deploy/prepare-server.sh devtools
#            bash scripts/manual-deploy/prepare-server.sh k8s-master --skip-upgrade
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REMOTE_SCRIPT="$SCRIPT_DIR/prepare-server-remote.sh"
ENV_FILE="$REPO_ROOT/infra-servers.env"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_ed25519}"

# Windows: ожидаем Git Bash (MSYS), не WSL
case "${OSTYPE:-}" in
  msys*|mingw*)
    : # Git Bash — OK
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

usage() {
  cat <<'EOF'
Использование:
  bash scripts/manual-deploy/prepare-server.sh <ROLE> [опции]

Роли (имя hostname на сервере):
  devtools, k8s-master, k8s-worker-1, k8s-worker-2,
  traefik-1, traefik-2, storage-1, storage-2

IP берётся из infra-servers.env, если не задан --host.

Опции:
  --host IP           явный IP (игнорировать mapping из env)
  --with-docker       установить Docker даже на k8s-нодах
  --no-docker         не ставить Docker даже на devtools
  --skip-upgrade      пропустить apt-get upgrade (быстрый повтор)
  --dry-run           показать шаги без изменений на сервере
  --yes               без ожидания Enter (только таймер-паузы)
  -h, --help          эта справка

Примеры:
  bash scripts/manual-deploy/prepare-server.sh devtools
  bash scripts/manual-deploy/prepare-server.sh traefik-1 --host 203.0.113.10
  bash scripts/manual-deploy/prepare-server.sh k8s-master --skip-upgrade --yes
EOF
}

if [[ $# -eq 0 ]]; then
  echo ""
  echo "ОШИБКА: не указана роль сервера (первый аргумент)." >&2
  echo "" >&2
  echo "  bash scripts/manual-deploy/prepare-server.sh devtools" >&2
  echo "" >&2
  usage >&2
  exit 1
fi

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

if [[ -z "$ROLE" ]]; then
  echo ""
  echo "ОШИБКА: не указана роль сервера." >&2
  echo "Укажите, например: devtools, k8s-master, traefik-1" >&2
  echo "" >&2
  usage >&2
  exit 1
fi

case "$ROLE" in
  devtools|k8s-master|k3s-server|k3s-master|k8s-worker-1|k8s-worker-2|k3s-worker-1|k3s-worker-2|traefik-1|traefik-2|storage-1|storage-2)
    ;;
  *)
    if [[ -z "$HOST" ]]; then
      echo ""
      echo "ОШИБКА: неизвестная роль «${ROLE}»." >&2
      echo "Допустимо: devtools, k8s-master, k8s-worker-1, k8s-worker-2, traefik-1, traefik-2, storage-1, storage-2" >&2
      echo "Или укажите IP: --host 1.2.3.4" >&2
      exit 1
    fi
    ;;
esac

if [[ ! -f "$REMOTE_SCRIPT" ]]; then
  echo "Не найден $REMOTE_SCRIPT" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Не найден $ENV_FILE — создайте файл с IP серверов." >&2
  exit 1
fi

# shellcheck source=/dev/null
source <(tr -d '\r' < "$ENV_FILE")

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

if [[ -z "$HOST" ]]; then
  HOST="$(resolve_host_from_role "$ROLE")"
fi

if [[ -z "$HOST" || "$HOST" == REPLACE_ME* || "$HOST" == \<* ]]; then
  echo "Не задан IP для роли «${ROLE}»." >&2
  echo "Заполните infra-servers.env или укажите: --host 1.2.3.4" >&2
  exit 1
fi

if [[ ! -f "$SSH_KEY" ]]; then
  echo "SSH-ключ не найден: $SSH_KEY" >&2
  exit 1
fi

echo "=== prepare-server.sh ==="
echo "Роль:   $ROLE"
echo "Хост:   root@${HOST}"
echo "Ключ:   $SSH_KEY"
echo "Docker: $DOCKER_FLAG"
echo "Режим:  dry-run=$DRY_RUN skip-upgrade=$SKIP_UPGRADE yes=$NONINTERACTIVE"
echo

if [[ "$DRY_RUN" != "1" ]]; then
  echo "--- Исходная проверка (локально): SSH ---"
  echo "Команда:"
  echo "  ssh -i ${SSH_KEY} root@${HOST} \"echo connected; hostname\""
  echo "----- вывод -----"
  if ssh -i "$SSH_KEY" \
    -o ConnectTimeout=15 \
    -o StrictHostKeyChecking=accept-new \
    "root@${HOST}" \
    "echo connected; hostname"; then
    echo "----- конец -----"
    echo "--- Результат: OK — SSH работает ---"
  else
    echo "----- конец -----"
    echo ""
    echo "ОШИБКА: SSH не удался (root@${HOST})." >&2
    echo "Проверьте ключ на VPS, ssh-keygen -R ${HOST} и ssh-keyscan." >&2
    exit 1
  fi
  echo
fi

export DRY_RUN SKIP_UPGRADE NONINTERACTIVE

ssh -i "$SSH_KEY" \
  -o ConnectTimeout=15 \
  -o StrictHostKeyChecking=accept-new \
  "root@${HOST}" \
  env DRY_RUN="$DRY_RUN" SKIP_UPGRADE="$SKIP_UPGRADE" NONINTERACTIVE="$NONINTERACTIVE" \
  bash -s -- "$ROLE" "$DOCKER_FLAG" \
  < "$REMOTE_SCRIPT"

echo
echo "=== Локально: prepare-server.sh завершён для ${ROLE} @ ${HOST} ==="
