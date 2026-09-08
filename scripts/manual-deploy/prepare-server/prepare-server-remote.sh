#!/usr/bin/env bash
# ============================================================================
# prepare-server-remote.sh
# НАЗНАЧЕНИЕ: подготовка VPS (§7 гайда): пакеты, hostname, ufw, опционально Docker.
# ГДЕ:       выполняется НА СЕРВЕРЕ (root). Вызывается через prepare-server.sh.
# АРГУМЕНТЫ: <ROLE_NAME> [--with-docker | --no-docker]
# ============================================================================
set -euo pipefail

ROLE_NAME="${1:-}"
DOCKER_MODE="${2:---auto}"

if [[ -z "$ROLE_NAME" || "$ROLE_NAME" == -* ]]; then
  echo "Использование: $0 <ROLE_NAME> [--with-docker|--no-docker|--auto]" >&2
  exit 1
fi

if [[ "$DOCKER_MODE" != "--with-docker" && "$DOCKER_MODE" != "--no-docker" && "$DOCKER_MODE" != "--auto" ]]; then
  echo "Неизвестный режим Docker: $DOCKER_MODE" >&2
  exit 1
fi

DRY_RUN="${DRY_RUN:-0}"
SKIP_UPGRADE="${SKIP_UPGRADE:-0}"
NONINTERACTIVE="${NONINTERACTIVE:-0}"

# --- паузы для чтения -------------------------------------------------------
pause_for_reading() {
  local text="$1"
  local chars=${#text}
  local seconds=$((2 + chars / 35))
  if (( seconds < 3 )); then seconds=3; fi
  if (( seconds > 25 )); then seconds=25; fi

  echo
  if [[ "$NONINTERACTIVE" == "1" ]]; then
    echo ">>> Пауза ${seconds} с (режим --yes)..."
    sleep "$seconds"
  else
    echo ">>> Пауза до ${seconds} с — прочитайте текст выше."
    read -r -t "$seconds" -p "    Enter — продолжить сразу, или подождите таймер... " _ || true
  fi
  echo
}

section_title() {
  echo
  echo "================================================================"
  echo "  $1"
  echo "================================================================"
}

subsection_doing() {
  echo
  echo "--- Выполняется: $1"
}

subsection_check() {
  echo
  echo "--- Проверка: $1"
}

subsection_result() {
  echo
  echo "--- Результат: $1"
}

# Проверка: команда → вывод на экран → OK/FAIL
VERIFY_FAIL=0

verify_command() {
  local title="$1"
  local cmd="$2"
  local optional="${3:-0}"

  subsection_check "$title"
  echo "Команда:"
  echo "  ${cmd}"
  echo "----- вывод -----"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "(dry-run — команда не выполнялась)"
    echo "----- конец -----"
    subsection_result "пропущено (dry-run)"
    return 0
  fi
  set +e
  local out status
  out=$(bash -c "$cmd" 2>&1)
  status=$?
  set -e
  echo "$out"
  echo "----- конец -----"
  if [[ "$status" -eq 0 ]]; then
    subsection_result "OK"
    return 0
  fi
  if [[ "$optional" == "1" ]]; then
    subsection_result "не критично (код ${status})"
    return 0
  fi
  subsection_result "FAIL (код выхода ${status})"
  VERIFY_FAIL=1
  return "$status"
}

verify_commands_exist() {
  local title="$1"
  shift
  local bin check missing=0 pkg

  subsection_check "$title"
  echo "Команда: command -v (проверка каждой утилиты)"
  echo "----- вывод -----"
  for bin in "$@"; do
    check="$bin"
    if [[ "$bin" == "gnupg" ]]; then
      check="gpg"
    fi
    if command -v "$check" >/dev/null 2>&1; then
      echo "  OK  ${bin} -> $(command -v "$check")"
    elif dpkg -l "$bin" 2>/dev/null | grep -q '^ii'; then
      echo "  OK  ${bin} (пакет установлен, бинарник: ${check})"
    else
      echo "  FAIL ${bin} — не найден"
      missing=1
    fi
  done
  echo "----- конец -----"
  if [[ "$missing" -eq 0 ]]; then
    subsection_result "OK — все утилиты на месте"
  else
    subsection_result "FAIL — не все утилиты установлены"
    VERIFY_FAIL=1
    return 1
  fi
}

run_cmd() {
  echo "+ $*"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  (dry-run: команда не выполнялась)"
    return 0
  fi
  "$@"
}

run_shell() {
  echo "+ $1"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  (dry-run: команда не выполнялась)"
    return 0
  fi
  bash -c "$1"
}

dns_resolves() {
  getent ahostsv4 archive.ubuntu.com >/dev/null 2>&1 \
    && getent ahostsv4 security.ubuntu.com >/dev/null 2>&1
}

default_iface() {
  ip -4 route show default 2>/dev/null | awk '{print $5; exit}'
}

has_internet() {
  ping -4 -c1 -W3 1.1.1.1 >/dev/null 2>&1 \
    || ping -4 -c1 -W3 8.8.8.8 >/dev/null 2>&1 \
    || ping -4 -c1 -W3 9.9.9.9 >/dev/null 2>&1
}

wait_for_network() {
  local elapsed=0 timeout=90
  while (( elapsed < timeout )); do
    if ip -4 route show default 2>/dev/null | grep -q . && has_internet; then
      echo "  OK  маршрут и интернет (ping публичного IP)"
      return 0
    fi
    echo "  ждём сеть... ${elapsed}/${timeout} с"
    sleep 3
    elapsed=$((elapsed + 3))
  done
  echo "ОШИБКА: нет интернета или default route за ${timeout} с." >&2
  ip -4 route show default 2>&1 || true
  return 1
}

fix_systemd_resolved() {
  mkdir -p /etc/systemd/resolved.conf.d
  cat > /etc/systemd/resolved.conf.d/99-prepare-server-dns.conf <<'EOF'
[Resolve]
DNS=1.1.1.1 8.8.8.8 9.9.9.9
FallbackDNS=149.112.112.112
Domains=~.
DNSStubListener=yes
EOF
  systemctl restart systemd-resolved 2>/dev/null || true
  sleep 2
}

fix_resolvectl_iface() {
  local iface
  iface="$(default_iface)"
  [[ -z "$iface" ]] && return 0
  if command -v resolvectl >/dev/null 2>&1; then
    resolvectl dns "$iface" 1.1.1.1 8.8.8.8 9.9.9.9 2>/dev/null || true
    resolvectl domain "$iface" "~." 2>/dev/null || true
    resolvectl flush-caches 2>/dev/null || true
  fi
  sleep 1
}

fix_static_resolv_conf() {
  chattr -i /etc/resolv.conf 2>/dev/null || true
  if [[ -L /etc/resolv.conf ]]; then
    rm -f /etc/resolv.conf
  fi
  cp -a /etc/resolv.conf "/etc/resolv.conf.bak-prepare-server.$(date +%s)" 2>/dev/null || true
  cat > /etc/resolv.conf <<'EOF'
nameserver 1.1.1.1
nameserver 8.8.8.8
nameserver 9.9.9.9
options timeout:2 attempts:3 rotate
EOF
  chmod 644 /etc/resolv.conf
  sleep 1
}

apply_dns_fix() {
  local attempt="$1"
  echo "  применяем DNS-исправление (вариант ${attempt})..."
  case "$(( attempt % 3 ))" in
    0) fix_systemd_resolved ;;
    1) fix_resolvectl_iface ;;
    2) fix_static_resolv_conf ;;
  esac
  fix_systemd_resolved
  fix_resolvectl_iface
  fix_static_resolv_conf
}

disable_unreachable_apt_mirrors() {
  local f host line hosts=()
  while IFS= read -r f; do
    [[ -f "$f" ]] || continue
    while IFS= read -r line; do
      [[ "$line" =~ ^[[:space:]]*# ]] && continue
      if [[ "$line" =~ ^deb[[:space:]]+https?://([^/[:space:]]+) ]]; then
        hosts+=("${BASH_REMATCH[1]}")
      fi
    done < "$f"
  done < <(find /etc/apt/sources.list /etc/apt/sources.list.d -type f 2>/dev/null)

  for host in $(printf '%s\n' "${hosts[@]}" | sort -u); do
    [[ "$host" == "archive.ubuntu.com" || "$host" == "security.ubuntu.com" ]] && continue
    if ! getent ahostsv4 "$host" >/dev/null 2>&1; then
      echo "  отключаем недоступное зеркало apt: ${host}"
      while IFS= read -r f; do
        [[ -f "$f" ]] || continue
        sed -i "/${host}/s/^[[:space:]]*deb/# prepare-server disabled mirror: &/" "$f" 2>/dev/null || true
      done < <(find /etc/apt/sources.list /etc/apt/sources.list.d -type f 2>/dev/null)
    fi
  done
}

ensure_network_and_dns() {
  local reason="${1:-перед apt}"
  local attempt

  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  (dry-run: проверка сети/DNS пропущена)"
    return 0
  fi

  echo "  проверка: ${reason}"
  wait_for_network || return 1

  for attempt in $(seq 1 10); do
    if dns_resolves; then
      echo "  OK  DNS работает"
      getent ahostsv4 archive.ubuntu.com | awk 'NR==1 {print "      archive.ubuntu.com ->", $1}'
      disable_unreachable_apt_mirrors
      return 0
    fi
    echo "  DNS пока не работает — попытка ${attempt}/10"
    apply_dns_fix "$attempt"
    sleep 2
  done

  echo ""
  echo "ОШИБКА: не удалось настроить DNS за 10 попыток." >&2
  echo "cat /etc/resolv.conf:" >&2
  cat /etc/resolv.conf >&2 || true
  return 1
}

run_apt_get_update_logged() {
  local log="$1"
  env DEBIAN_FRONTEND=noninteractive apt-get -o Acquire::Retries=3 update >"$log" 2>&1
}

run_apt_get() {
  local tries attempt=1 max=4
  echo "+ apt-get $*"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  (dry-run: команда не выполнялась)"
    return 0
  fi
  ensure_network_and_dns "перед apt-get $*" || exit 1
  while (( attempt <= max )); do
    if env DEBIAN_FRONTEND=noninteractive apt-get -o Acquire::Retries=3 "$@"; then
      return 0
    fi
    echo ""
    echo "apt-get не удался (попытка ${attempt}/${max})"
    ensure_network_and_dns "повтор apt-get" || exit 1
    disable_unreachable_apt_mirrors
    attempt=$((attempt + 1))
    sleep 2
  done
  echo "ОШИБКА: apt-get $* не выполнен после ${max} попыток." >&2
  exit 1
}

install_docker_engine() {
  subsection_doing "установка Docker (пакет docker.io из Ubuntu)"
  echo "На части VPS get.docker.com не ставит docker-ce — используем docker.io из Ubuntu."

  if [[ "$DRY_RUN" == "1" ]]; then
    echo "+ apt-get install -y docker.io"
    echo "+ systemctl enable --now docker"
    echo "  (dry-run: команда не выполнялась)"
    return 0
  fi

  if ! run_apt_get install -y docker.io; then
    echo ""
    echo "ОШИБКА: не удалось установить docker.io через apt." >&2
    exit 1
  fi

  if ! systemctl enable --now docker; then
    echo ""
    echo "ОШИБКА: docker установлен, но systemctl enable --now docker не сработал." >&2
    systemctl status docker --no-pager 2>&1 || true
    exit 1
  fi

  if ! command -v docker >/dev/null 2>&1; then
    echo "ОШИБКА: команда docker не найдена после установки." >&2
    exit 1
  fi

  if ! systemctl is-active docker >/dev/null 2>&1; then
    echo "ОШИБКА: сервис docker не активен." >&2
    systemctl status docker --no-pager 2>&1 || true
    exit 1
  fi
}

role_needs_docker() {
  case "$ROLE_NAME" in
    devtools|traefik-1|traefik-2|storage-1|storage-2|traefik-*|storage-*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

resolve_docker_install() {
  case "$DOCKER_MODE" in
    --with-docker) echo "yes" ;;
    --no-docker) echo "no" ;;
    --auto)
      if role_needs_docker; then echo "yes"; else echo "no"; fi
      ;;
  esac
}

INSTALL_DOCKER="$(resolve_docker_install)"

# --- старт -------------------------------------------------------------------
section_title "Подготовка сервера: роль «${ROLE_NAME}»"
echo "Docker: $([[ "$INSTALL_DOCKER" == "yes" ]] && echo 'будет установлен' || echo 'не требуется для этой роли')"
pause_for_reading "Начинаем подготовку сервера ${ROLE_NAME}. Сначала — исходная проверка, затем установка, в конце — полная проверка всех компонентов."

# --- 0. исходная проверка (до изменений) -------------------------------------
section_title "0. Исходная проверка (до изменений)"
echo "Смотрим, что уже есть на сервере до apt/hostname/ufw/Docker."
verify_command "hostname и ОС" "hostnamectl status"
verify_command "firewall (ufw)" "ufw status verbose" "1"
verify_commands_exist "утилиты до установки (что уже есть)" curl wget git ufw gnupg || true
VERIFY_FAIL=0
subsection_result "исходное состояние зафиксировано — начинаем настройку"
pause_for_reading "Исходная проверка завершена. Дальше — сеть, DNS и установка пакетов."

# --- 1. сеть и DNS -----------------------------------------------------------
section_title "1. Сеть, DNS и зеркала apt"
subsection_doing "ожидание интернета, настройка DNS, отключение мёртвых зеркал"
if ! ensure_network_and_dns "перед установкой пакетов"; then
  exit 1
fi
subsection_check "resolv.conf и резолв Ubuntu"
run_shell "cat /etc/resolv.conf"
run_shell "getent ahostsv4 archive.ubuntu.com | head -2"
subsection_result "сеть и DNS готовы"
pause_for_reading "Скрипт сам дождался интернета, прописал публичные DNS при необходимости и отключил недоступные зеркала apt других провайдеров."

# --- 2. apt update -----------------------------------------------------------
section_title "2. Обновление индексов пакетов (apt-get update)"
subsection_doing "скачивание списков пакетов из репозиториев Ubuntu"
UPDATE_LOG="$(mktemp)"
if [[ "$DRY_RUN" == "1" ]]; then
  echo "+ apt-get update (dry-run)"
else
  ensure_network_and_dns "перед apt-get update" || exit 1
  set +e
  run_apt_get_update_logged "$UPDATE_LOG"
  UPDATE_STATUS=$?
  set -e
  cat "$UPDATE_LOG"
  if [[ "$UPDATE_STATUS" -ne 0 ]]; then
    echo "apt-get update вернул $UPDATE_STATUS — правим DNS/зеркала и повторяем..."
    disable_unreachable_apt_mirrors
    ensure_network_and_dns "повтор apt-get update" || exit 1
    set +e
    run_apt_get_update_logged "$UPDATE_LOG"
    UPDATE_STATUS=$?
    set -e
    cat "$UPDATE_LOG"
  fi
  if [[ "$UPDATE_STATUS" -ne 0 ]]; then
    echo "Ошибка: apt-get update завершился с кодом $UPDATE_STATUS" >&2
    rm -f "$UPDATE_LOG"
    exit "$UPDATE_STATUS"
  fi
fi

subsection_check "вывод apt-get update (последние строки)"
tail -n 8 "$UPDATE_LOG" || true

if [[ "$DRY_RUN" != "1" ]] && command -v apt >/dev/null 2>&1; then
  if grep -qi 'modernize-sources\|Run .*modernize-sources' "$UPDATE_LOG"; then
    subsection_doing "миграция формата sources.list (apt modernize-sources — подсказка из консоли apt)"
    pause_for_reading "Ubuntu просит выполнить apt modernize-sources — это безопасная миграция формата репозиториев."
    run_cmd apt modernize-sources || true
  fi
fi
rm -f "$UPDATE_LOG"

subsection_result "индексы пакетов обновлены"
pause_for_reading "Шаг 2 (apt update) завершён. Далее — upgrade."

# --- 3. apt upgrade ----------------------------------------------------------
section_title "3. Обновление установленных пакетов (apt-get upgrade)"
if [[ "$SKIP_UPGRADE" == "1" ]]; then
  echo "Пропуск upgrade (--skip-upgrade)."
else
  subsection_doing "apt-get upgrade -y (может занять несколько минут)"
  pause_for_reading "Сейчас обновятся уже установленные пакеты системы. На чистом VPS это нормальный и рекомендуемый шаг."
  run_apt_get -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold upgrade -y
  subsection_check "последние записи apt (upgrade)"
  tail -n 5 /var/log/apt/history.log 2>/dev/null || echo "(лог history пока пуст)"
  subsection_result "upgrade завершён"
fi
pause_for_reading "Шаг 3 завершён. Устанавливаем базовые утилиты: curl, wget, git, ufw и др."

# --- 4. базовые пакеты -------------------------------------------------------
section_title "4. Установка базовых пакетов"
subsection_doing "apt-get install curl wget gnupg git ufw ca-certificates"
run_apt_get install -y curl wget gnupg git ufw ca-certificates

verify_commands_exist "проверка после apt install" curl wget git ufw gnupg ca-certificates || exit 1
subsection_result "базовые пакеты установлены"
pause_for_reading "Шаг 4 завершён. Задаём уникальное имя хоста (hostname) для роли ${ROLE_NAME}."

# --- 5. hostname -------------------------------------------------------------
section_title "5. Имя сервера (hostname)"
subsection_doing "hostnamectl set-hostname ${ROLE_NAME}"
run_cmd hostnamectl set-hostname "$ROLE_NAME"

verify_command "hostname после set-hostname" "hostnamectl status"
subsection_result "hostname установлен: ${ROLE_NAME}"
pause_for_reading "Шаг 5 завершён. Настраиваем firewall (ufw): SSH, HTTP и HTTPS."

# --- 6. ufw ------------------------------------------------------------------
section_title "6. Firewall (ufw)"
subsection_doing "разрешить OpenSSH, 80/tcp, 443/tcp и включить ufw"
run_cmd ufw allow OpenSSH
run_cmd ufw allow 80/tcp
run_cmd ufw allow 443/tcp
run_cmd ufw --force enable

verify_command "hostname после ufw" "hostnamectl status"
verify_command "ufw status verbose" "ufw status verbose"
verify_command "порты 22, 80, 443" "ss -tulpn | grep -E ':22|:80|:443' || echo '(80/443 пока не слушаются — нормально)'" "1"
subsection_result "ufw активен, правила применены"
pause_for_reading "Шаг 6 завершён. Далее — Docker (только если роль это предполагает)."

# --- 7. docker (опционально) -------------------------------------------------
if [[ "$INSTALL_DOCKER" == "yes" ]]; then
  section_title "7. Docker (devtools / traefik / storage)"
  pause_for_reading "Docker нужен на этой роли: registry и Runner (devtools), Traefik в контейнере или MinIO на storage. docker login — позже, при push в registry."
  install_docker_engine

  verify_command "docker --version" "docker --version"
  verify_command "systemctl is-active docker" "systemctl is-active docker"
  verify_command "systemctl is-enabled docker" "systemctl is-enabled docker"
  verify_command "docker version" "docker version"
  pause_for_reading "Запускаем hello-world — проверка pull и run контейнера."
  verify_command "docker run hello-world" "docker run --rm hello-world"

  subsection_result "Docker установлен и отвечает"
else
  section_title "7. Docker — пропуск"
  echo "Для роли «${ROLE_NAME}» Docker в §7 не требуется (k3s использует containerd)."
  pause_for_reading "На master/worker k3s Docker не ставим. Переходим к итоговой проверке."
fi

# --- 8. итоговая проверка установленных компонентов ---------------------------
section_title "8. Итоговая проверка установленных компонентов"
echo "Полный список проверок (как в §7 гайда) — каждая команда отдельно."
pause_for_reading "Финальный блок: команда → вывод → OK/FAIL."

verify_command "8.1 hostnamectl status" "hostnamectl status"
verify_command "8.2 ufw status verbose" "ufw status verbose"
verify_command "8.3 ss -tulpn | grep :22|:80|:443" "ss -tulpn | grep -E ':22|:80|:443' || echo '(80/443 пока не слушаются)'" "1"

echo
echo "--- 8.4 command -v (каждая утилита отдельно) ---"
verify_command "8.4.1 command -v curl" "command -v curl"
verify_command "8.4.2 command -v wget" "command -v wget"
verify_command "8.4.3 command -v git" "command -v git"
verify_command "8.4.4 command -v ufw" "command -v ufw"
verify_command "8.4.5 command -v gpg (пакет gnupg)" "command -v gpg"
verify_command "8.4.6 ca-certificates (dpkg)" "dpkg -l ca-certificates | tail -1"

if [[ "$INSTALL_DOCKER" == "yes" ]]; then
  echo
  echo "--- 8.5 Docker (devtools / traefik / storage) ---"
  verify_command "8.5.1 docker --version" "docker --version"
  verify_command "8.5.2 systemctl is-active docker" "systemctl is-active docker"
  verify_command "8.5.3 systemctl is-enabled docker" "systemctl is-enabled docker"
  verify_command "8.5.4 docker version" "docker version"
else
  echo
  echo "--- Docker: не требуется для роли «${ROLE_NAME}» ---"
fi

echo
echo "================================================================"
if [[ "$VERIFY_FAIL" -eq 0 ]]; then
  subsection_result "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ — сервер «${ROLE_NAME}» готов"
else
  subsection_result "ЕСТЬ ОШИБКИ ПРОВЕРКИ — см. FAIL выше"
  echo "Готово с замечаниями. Исправьте FAIL и перезапустите скрипт с --skip-upgrade." >&2
  exit 1
fi

echo
echo "Готово. Можно закрыть SSH-сессию или перейти к следующему разделу гайда."
pause_for_reading "§7 завершён для «${ROLE_NAME}». Сохраните вывод финальной проверки (раздел 8) и переходите к §8 k3s или §10 registry."
