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
SSH_BOOTSTRAP="" # yes | no | ask

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
  --yes               без ожидания Enter на remote (не отключает вопрос фазы 0)
  --ssh-bootstrap     фаза 0 без вопроса: да, копировать ключ по паролю
  --no-ssh-bootstrap  фаза 0 без вопроса: нет, ключ уже на серверах
  -h, --help          эта справка

Фаза 0 (в начале этого же скрипта, интерактивно yes/no, если не заданы флаги):
  yes — по IP из env: вход *_SSH_USER / *_SSH_PASSWORD, запись ~/.ssh/id_ed25519.pub
  no  — сразу §7, только вход по ключу

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
    --ssh-bootstrap)
      SSH_BOOTSTRAP="yes"
      shift
      ;;
    --no-ssh-bootstrap)
      SSH_BOOTSTRAP="no"
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

resolve_ssh_user_from_role() {
  local role="$1"
  local user="${SSH_BOOTSTRAP_DEFAULT_USER:-root}"
  case "$role" in
    devtools) user="${DEVTOOLS_SSH_USER:-$user}" ;;
    k8s-master|k3s-server|k3s-master) user="${K3S_SERVER_SSH_USER:-$user}" ;;
    k8s-worker-1|k3s-worker-1) user="${K3S_WORKER_1_SSH_USER:-$user}" ;;
    k8s-worker-2|k3s-worker-2) user="${K3S_WORKER_2_SSH_USER:-$user}" ;;
    traefik-1) user="${TRAEFIK_1_SSH_USER:-$user}" ;;
    traefik-2) user="${TRAEFIK_2_SSH_USER:-$user}" ;;
    storage-1) user="${STORAGE_1_SSH_USER:-$user}" ;;
    storage-2) user="${STORAGE_2_SSH_USER:-$user}" ;;
  esac
  echo "$user"
}

resolve_password_from_role() {
  case "$1" in
    devtools) echo "${DEVTOOLS_SSH_PASSWORD:-}" ;;
    k8s-master|k3s-server|k3s-master) echo "${K3S_SERVER_SSH_PASSWORD:-}" ;;
    k8s-worker-1|k3s-worker-1) echo "${K3S_WORKER_1_SSH_PASSWORD:-}" ;;
    k8s-worker-2|k3s-worker-2) echo "${K3S_WORKER_2_SSH_PASSWORD:-}" ;;
    traefik-1) echo "${TRAEFIK_1_SSH_PASSWORD:-}" ;;
    traefik-2) echo "${TRAEFIK_2_SSH_PASSWORD:-}" ;;
    storage-1) echo "${STORAGE_1_SSH_PASSWORD:-}" ;;
    storage-2) echo "${STORAGE_2_SSH_PASSWORD:-}" ;;
    *) echo "" ;;
  esac
}

bootstrap_role_in_scope() {
  local role="$1"
  if [[ -z "$ROLE" ]]; then
    return 0
  fi
  [[ "$role" == "$ROLE" ]]
}

ASKPASS_HELPER=""

cleanup_askpass() {
  if [[ -n "$ASKPASS_HELPER" && -f "$ASKPASS_HELPER" ]]; then
    rm -f "$ASKPASS_HELPER"
  fi
  ASKPASS_HELPER=""
  unset SSH_BOOTSTRAP_ASKPASS_PASSWORD SSH_ASKPASS SSH_ASKPASS_REQUIRE 2>/dev/null || true
}

setup_askpass() {
  local password="$1"
  cleanup_askpass
  ASKPASS_HELPER="$(mktemp)"
  chmod 700 "$ASKPASS_HELPER"
  cat > "$ASKPASS_HELPER" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$SSH_BOOTSTRAP_ASKPASS_PASSWORD"
EOF
  chmod 700 "$ASKPASS_HELPER"
  export SSH_BOOTSTRAP_ASKPASS_PASSWORD="$password"
  export SSH_ASKPASS="$ASKPASS_HELPER"
  export SSH_ASKPASS_REQUIRE=force
  export DISPLAY="${DISPLAY:-:0}"
}

# Если отпечаток именно этого хоста сменился — правим только его строку
# в known_hosts и подключаемся ещё раз. Остальные записи файла не трогаем.
replace_changed_host_key() {
  local host="$1"
  echo "known_hosts: отпечаток ${host} сменился — обновляю только эту запись"
  ssh-keygen -R "$host" >/dev/null 2>&1 || true
  ssh-keygen -R "[${host}]:22" >/dev/null 2>&1 || true
}

run_with_host_key_refresh() {
  local host="$1"
  shift
  local err rc
  err="$(mktemp)"
  if "$@" 2>"$err"; then
    cat "$err" >&2
    rm -f "$err"
    return 0
  fi
  rc=$?
  cat "$err" >&2
  if grep -q 'REMOTE HOST IDENTIFICATION HAS CHANGED' "$err"; then
    rm -f "$err"
    replace_changed_host_key "$host"
    "$@"
    return $?
  fi
  rm -f "$err"
  return "$rc"
}

SSH_HOSTKEY_OPTS=(
  -o StrictHostKeyChecking=accept-new
)

ssh_password_common_opts=(
  -o ConnectTimeout=60
  "${SSH_HOSTKEY_OPTS[@]}"
  -o PreferredAuthentications=password,keyboard-interactive
  -o PubkeyAuthentication=no
  -o NumberOfPasswordPrompts=1
)

run_ssh_with_password() {
  local user="$1"
  local host="$2"
  local password="$3"
  shift 3
  setup_askpass "$password"
  # shellcheck disable=SC2068
  run_with_host_key_refresh "$host" ssh "${ssh_password_common_opts[@]}" "${user}@${host}" "$@"
  local rc=$?
  cleanup_askpass
  return "$rc"
}

run_scp_with_password() {
  local user="$1"
  local host="$2"
  local password="$3"
  local src="$4"
  local dst="$5"
  setup_askpass "$password"
  run_with_host_key_refresh "$host" scp "${ssh_password_common_opts[@]}" "$src" "${user}@${host}:${dst}"
  local rc=$?
  cleanup_askpass
  return "$rc"
}

install_ssh_key_on_host() {
  local role="$1"
  local user="$2"
  local host="$3"
  local password="$4"
  local pub_key="${SSH_KEY}.pub"

  echo "--- ${role} @ ${user}@${host} ---"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "[dry-run] scp ${pub_key} → authorized_keys"
    return 0
  fi

  run_scp_with_password "$user" "$host" "$password" "$pub_key" "/tmp/bootstrap-ssh-key.pub"
  run_ssh_with_password "$user" "$host" "$password" bash -s <<'REMOTE'
set -euo pipefail
mkdir -p ~/.ssh
chmod 700 ~/.ssh
auth=~/.ssh/authorized_keys
touch "$auth"
chmod 600 "$auth"
line="$(tr -d '\r\n' < /tmp/bootstrap-ssh-key.pub)"
grep -qxF "$line" "$auth" || echo "$line" >> "$auth"
rm -f /tmp/bootstrap-ssh-key.pub
REMOTE

  if run_with_host_key_refresh "$host" ssh -i "$SSH_KEY" \
    -o ConnectTimeout=30 \
    -o BatchMode=yes \
    "${SSH_HOSTKEY_OPTS[@]}" \
    "${user}@${host}" \
    "echo connected; hostname"; then
    echo "OK: ключ работает"
    return 0
  fi
  echo "FAIL: ключ не принят после фазы 0" >&2
  return 1
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
  if [[ "$NONINTERACTIVE" == "1" ]]; then
    echo
    echo "Режим --yes: роль пропущена (нет IP или SSH)."
    return 1
  fi
  echo
  echo "Когда VPS готов и infra-servers.env сохранён — нажмите Enter."
  echo "Скрипт сам прочитает env и повторит проверку."
  echo "Чтобы пропустить эту роль и идти дальше — введите s и нажмите Enter."
  echo
  local choice=""
  if [[ -r /dev/tty ]]; then
    read -r -p "Enter — повторить, s — пропустить: " choice </dev/tty
  else
    echo "Нет интерактивного терминала — роль пропущена." >&2
    return 1
  fi
  echo
  if [[ "$choice" == "s" || "$choice" == "S" ]]; then
    return 1
  fi
  return 0
}

ssh_probe() {
  local ip="$1"
  local ssh_user="${2:-root}"
  run_with_host_key_refresh "$ip" ssh -i "$SSH_KEY" \
    -o ConnectTimeout=12 \
    -o BatchMode=yes \
    "${SSH_HOSTKEY_OPTS[@]}" \
    "${ssh_user}@${ip}" \
    "echo connected; hostname"
}

ask_ssh_bootstrap() {
  if [[ -n "$SSH_BOOTSTRAP" ]]; then
    return 0
  fi
  if [[ "$DRY_RUN" == "1" ]]; then
    SSH_BOOTSTRAP="no"
    return 0
  fi

  echo
  echo "================================================================"
  echo "  Фаза 0: копирование SSH-ключа по паролю"
  echo "================================================================"
  echo
  echo "Некоторые облака не добавляют SSH-ключ при создании VPS."
  echo "Можно подключиться по логину/паролю из infra-servers.env"
  echo "и записать ${SSH_KEY}.pub в authorized_keys на каждом сервере."
  echo
  echo "  yes — выполнить фазу 0, затем основную подготовку §7"
  echo "  no  — пропустить (ключ уже на серверах, дальше только SSH-ключ)"
  echo
  local choice=""
  if [[ ! -r /dev/tty ]]; then
    echo "ОШИБКА: нужен интерактивный терминал для yes/no." >&2
    echo "Запустите из Git Bash или укажите --ssh-bootstrap / --no-ssh-bootstrap." >&2
    exit 1
  fi
  read -r -p "Копировать SSH-ключ по паролю? (yes/no): " choice </dev/tty
  case "$choice" in
    yes|y|Y|Yes|YES)
      SSH_BOOTSTRAP="yes"
      ;;
    *)
      SSH_BOOTSTRAP="no"
      ;;
  esac
  echo
}

run_ssh_bootstrap() {
  local role ip user password
  local -a failed=()
  declare -A bootstrap_seen_ips=()

  ask_ssh_bootstrap

  echo "Фаза 0 (копирование SSH-ключа): ${SSH_BOOTSTRAP}"
  if [[ "$SSH_BOOTSTRAP" != "yes" ]]; then
    echo "Пропуск — дальше подключение только по ключу."
    return 0
  fi

  if [[ ! -f "${SSH_KEY}.pub" ]]; then
    echo "Не найден публичный ключ: ${SSH_KEY}.pub" >&2
    exit 1
  fi

  trap cleanup_askpass EXIT
  load_infra_env "$ENV_FILE"

  echo
  echo "=== Фаза 0: копирование ${SSH_KEY}.pub по паролю из env ==="
  echo

  for role in "${ALL_ROLES[@]}"; do
    if ! bootstrap_role_in_scope "$role"; then
      continue
    fi

    ip="$(resolve_host_from_role "$role")"
    if ip_is_missing "$ip"; then
      echo "${role}: пропуск — нет IP"
      continue
    fi
    if [[ -n "${bootstrap_seen_ips[$ip]:-}" ]]; then
      echo "${role}: пропуск — IP ${ip} уже как «${bootstrap_seen_ips[$ip]}»"
      continue
    fi

    user="$(resolve_ssh_user_from_role "$role")"
    password="$(resolve_password_from_role "$role")"
    if [[ -z "$password" ]]; then
      echo "${role} @ ${ip}: пропуск — пароль не задан в env"
      continue
    fi

    if install_ssh_key_on_host "$role" "$user" "$ip" "$password"; then
      bootstrap_seen_ips["$ip"]="$role"
    else
      failed+=("$role")
    fi
    echo
  done

  cleanup_askpass
  trap - EXIT

  if [[ ${#failed[@]} -gt 0 ]]; then
    echo "ОШИБКА: фаза 0 не завершилась для: ${failed[*]}" >&2
    exit 1
  fi

  echo "--- Фаза 0 завершена ---"
  echo
}

run_remote() {
  local role="$1"
  local ip="$2"
  local ssh_user
  ssh_user="$(resolve_ssh_user_from_role "$role")"

  echo "=== prepare-server.sh ==="
  echo "Роль:   $role"
  echo "Хост:   ${ssh_user}@${ip}"
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

  run_with_host_key_refresh "$ip" ssh -i "$SSH_KEY" \
    -o ConnectTimeout=15 \
    "${SSH_HOSTKEY_OPTS[@]}" \
    "${ssh_user}@${ip}" \
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
  local ip env_name ssh_user

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

    load_infra_env "$ENV_FILE"
    ssh_user="$(resolve_ssh_user_from_role "$role")"

    echo
    echo "--- Проверка SSH: ${ssh_user}@${ip} (${role}) ---"
    echo "Команда:"
    echo "  ssh -i ${SSH_KEY} ${ssh_user}@${ip} \"echo connected; hostname\""
    echo "----- вывод -----"
    if ssh_probe "$ip" "$ssh_user"; then
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

run_ssh_bootstrap

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
