#!/usr/bin/env bash
# ============================================================================
# install-k3s-master.sh
# НАЗНАЧЕНИЕ: фаза 2 — установка k3s control-plane (master) через SSH.
# ГДЕ:       Windows — только Git Bash (не WSL, не PowerShell).
# ЗАПУСК:    bash scripts/manual-deploy/k3s-master/install-k3s-master.sh
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
REMOTE_SCRIPT="$SCRIPT_DIR/install-k3s-master-remote.sh"
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

HOST=""
DRY_RUN=0
NONINTERACTIVE=0

usage() {
  cat <<'EOF'
Использование:
  bash scripts/manual-deploy/k3s-master/install-k3s-master.sh [опции]

IP master берётся из infra-servers.env (K8S_MASTER_IP, затем K3S_SERVER_IP,
затем DEVTOOLS_IP — если у вас один и тот же адрес, это допустимо).

Опции:
  --host IP    явный IP master
  --dry-run    показать шаги без установки k3s
  --yes        без ожидания Enter (только таймер-паузы)
  -h, --help   эта справка

После успеха токен пишется в infra-servers.env как K3S_TOKEN=
(и дублируется в файл k3s-node-token в каталоге запуска).
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
      echo "Лишний аргумент: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "$REMOTE_SCRIPT" ]]; then
  echo "Не найден $REMOTE_SCRIPT" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Не найден $ENV_FILE" >&2
  exit 1
fi

load_infra_env "$ENV_FILE"
print_source_reminder

if [[ -z "$HOST" ]]; then
  HOST="${K8S_MASTER_IP:-${K3S_SERVER_IP:-${DEVTOOLS_IP:-}}}"
fi

if [[ -z "$HOST" || "$HOST" == REPLACE_ME* || "$HOST" == \<* ]]; then
  echo "Не задан IP master." >&2
  echo "Заполните K8S_MASTER_IP / K3S_SERVER_IP / DEVTOOLS_IP или укажите --host." >&2
  exit 1
fi

if [[ ! -f "$SSH_KEY" ]]; then
  echo "SSH-ключ не найден: $SSH_KEY" >&2
  exit 1
fi

echo "=== install-k3s-master.sh (фаза 2) ==="
echo "Хост:        root@${HOST}"
echo "Ключ:        $SSH_KEY"
echo "Каталог запуска: $LAUNCH_DIR"
echo "Файл токена: ${LAUNCH_DIR}/${TOKEN_NAME}"
echo "Режим:       dry-run=${DRY_RUN} yes=${NONINTERACTIVE}"
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
    echo "ОШИБКА: SSH не удался (root@${HOST})." >&2
    echo "Сначала: source infra-servers.env, ключ на VPS, ssh-keyscan." >&2
    exit 1
  fi
  echo
fi

ssh -i "$SSH_KEY" \
  -o ConnectTimeout=15 \
  -o StrictHostKeyChecking=accept-new \
  "root@${HOST}" \
  env DRY_RUN="$DRY_RUN" NONINTERACTIVE="$NONINTERACTIVE" \
  bash -s -- \
  < "$REMOTE_SCRIPT"

if [[ "$DRY_RUN" == "1" ]]; then
  echo
  echo "=== dry-run: файл токена не копировался ==="
  exit 0
fi

echo
echo "--- Копирование токена на локальный ПК ---"
echo "Команда:"
echo "  scp -i ${SSH_KEY} root@${HOST}:/root/${TOKEN_NAME} ${LAUNCH_DIR}/${TOKEN_NAME}"
echo "----- вывод -----"
if scp -i "$SSH_KEY" \
  -o ConnectTimeout=15 \
  -o StrictHostKeyChecking=accept-new \
  "root@${HOST}:/root/${TOKEN_NAME}" \
  "${LAUNCH_DIR}/${TOKEN_NAME}"; then
  echo "----- конец -----"
  echo "--- Результат: OK ---"
else
  echo "----- конец -----"
  echo "ОШИБКА: не удалось скопировать /root/${TOKEN_NAME} с сервера." >&2
  exit 1
fi

if [[ "$LAUNCH_DIR" != "$SCRIPT_DIR" ]]; then
  cp -f "${LAUNCH_DIR}/${TOKEN_NAME}" "${SCRIPT_DIR}/${TOKEN_NAME}"
fi
chmod 600 "${LAUNCH_DIR}/${TOKEN_NAME}" 2>/dev/null || true

TOKEN_VALUE="$(tr -d '\r\n' < "${LAUNCH_DIR}/${TOKEN_NAME}")"
if [[ -z "$TOKEN_VALUE" ]]; then
  echo "ОШИБКА: файл ${TOKEN_NAME} пустой — в env ничего не записано." >&2
  exit 1
fi

echo
echo "--- Запись K3S_TOKEN в infra-servers.env ---"
upsert_env_var "$ENV_FILE" "K3S_TOKEN" "$TOKEN_VALUE"
echo "--- Результат: OK — переменная K3S_TOKEN обновлена в env ---"
echo "    (значение на экран не печатаем)"

echo
echo "================================================================"
echo "  Токен сохранён. Вручную его копировать не нужно."
echo "================================================================"
echo
echo "  env:   ${ENV_FILE}  (строка K3S_TOKEN=...)"
echo "  файл:  ${LAUNCH_DIR}/${TOKEN_NAME}"
echo "  Права: только для вас. Файл и *.env в .gitignore."
echo
echo "Что делать дальше:"
echo "  1. В ЭТОЙ сессии Git Bash снова подхватите env:"
echo
echo "     source <(tr -d '\\r' < ./infra-servers.env)"
echo
echo "  2. Заполните K3S_WORKER_1_IP и K3S_WORKER_2_IP, если ещё REPLACE_ME."
echo "  3. Запустите скрипт воркеров — он сам возьмёт K3S_TOKEN из env:"
echo
echo "     bash scripts/manual-deploy/k3s-workers/install-k3s-workers.sh"
echo
echo "=== Локально: install-k3s-master.sh завершён для root@${HOST} ==="
