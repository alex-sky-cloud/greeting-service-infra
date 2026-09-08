#!/usr/bin/env bash
# ============================================================================
# install-k3s-workers-remote.sh
# НАЗНАЧЕНИЕ: установка k3s agent (worker) на ЭТОМ VPS.
# ГДЕ:       выполняется НА СЕРВЕРЕ (root). Вызывается через install-k3s-workers.sh.
# ENV:       K3S_URL  K3S_TOKEN  K3S_NODE_NAME  DRY_RUN  NONINTERACTIVE
# ============================================================================
set -euo pipefail

DRY_RUN="${DRY_RUN:-0}"
NONINTERACTIVE="${NONINTERACTIVE:-0}"
K3S_URL="${K3S_URL:-}"
K3S_TOKEN="${K3S_TOKEN:-}"
K3S_NODE_NAME="${K3S_NODE_NAME:-$(hostname)}"
TOKEN_FILE="/root/.k3s-join-token"

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

VERIFY_FAIL=0

verify_command() {
  local title="$1"
  local cmd="$2"

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
  subsection_result "FAIL (код выхода ${status})"
  VERIFY_FAIL=1
  return "$status"
}

if [[ -z "$K3S_URL" ]]; then
  echo "ОШИБКА: не задан K3S_URL (должен быть https://<IP_MASTER>:6443)." >&2
  exit 1
fi

if [[ -z "$K3S_TOKEN" && -f "$TOKEN_FILE" ]]; then
  K3S_TOKEN="$(tr -d '\r\n' < "$TOKEN_FILE")"
fi

if [[ -z "$K3S_TOKEN" ]]; then
  echo "ОШИБКА: нет K3S_TOKEN — локальный скрипт должен передать его из env." >&2
  exit 1
fi

echo "=== install-k3s-workers-remote.sh — этот сервер: $(hostname) ==="
echo "Пользователь: $(id -un)"
echo "Имя ноды:     ${K3S_NODE_NAME}"
echo "K3S_URL:      ${K3S_URL}"
echo "K3S_TOKEN:    (скрыт, длина ${#K3S_TOKEN})"
echo

# ── 0 ────────────────────────────────────────────────────────────────────
section_title "0. Что будет сделано на ЭТОМ сервере"
echo "Это join worker (k3s agent). Не control-plane."
echo
echo "Почему обязателен K3S_URL:"
echo "  Установщик k3s без K3S_URL ставит server — второй «мозг»."
echo "  С K3S_URL=https://<master>:6443 он ставит agent и регистрируется"
echo "  на уже существующем master. Нам нужен именно agent."
echo
echo "Почему обязателен K3S_TOKEN:"
echo "  Это пароль кластера с master (node-token)."
echo "  Без него API не примет новую ноду."
echo
echo "Шаги:"
echo "  1) curl https://get.k3s.io | K3S_URL=... K3S_TOKEN=... sh -"
echo "  2) проверить k3s-agent"
echo "  3) открыть 8472/udp и 10250/tcp, если есть ufw"
pause_for_reading "Пояснение join на этом worker."

# ── 1 ────────────────────────────────────────────────────────────────────
section_title "1. Установка k3s agent"
echo "Токен на экран не печатаем."
pause_for_reading "Сейчас будет curl | sh установки agent."

subsection_doing "установка k3s agent (если ещё не стоит)"
if [[ "$DRY_RUN" == "1" ]]; then
  echo "+ curl -sfL https://get.k3s.io | K3S_URL=${K3S_URL} K3S_TOKEN=*** K3S_NODE_NAME=${K3S_NODE_NAME} sh -"
  echo "  (dry-run: команда не выполнялась)"
elif systemctl is-active --quiet k3s-agent 2>/dev/null; then
  echo "k3s-agent уже активен — повторную установку пропускаем."
  verify_command "версия k3s" "k3s --version"
elif command -v k3s >/dev/null 2>&1 && systemctl is-active --quiet k3s 2>/dev/null; then
  echo "На этом хосте запущен k3s server, а не agent."
  echo "K3S_URL не был задан при прошлой установке — получился второй master."
  echo "Разберите вручную (не продолжаем автоматически)."
  exit 1
else
  echo "Команда:"
  echo "  curl -sfL https://get.k3s.io | K3S_URL=${K3S_URL} K3S_TOKEN=*** K3S_NODE_NAME=${K3S_NODE_NAME} sh -"
  echo "----- вывод -----"
  curl -sfL https://get.k3s.io | K3S_URL="$K3S_URL" K3S_TOKEN="$K3S_TOKEN" K3S_NODE_NAME="$K3S_NODE_NAME" sh -
  echo "----- конец -----"
  subsection_result "установщик завершился"
fi

# ── 2 ────────────────────────────────────────────────────────────────────
section_title "2. Проверка agent"
pause_for_reading "Сейчас проверка сервиса k3s-agent."

if [[ "$DRY_RUN" != "1" ]]; then
  echo "Ожидание active (до 60 с)..."
  ready=0
  for _ in $(seq 1 12); do
    if systemctl is-active --quiet k3s-agent 2>/dev/null; then
      ready=1
      break
    fi
    sleep 5
  done
  if [[ "$ready" -ne 1 ]]; then
    echo "Предупреждение: за 60 с k3s-agent не стал active — проверка ниже."
  fi
fi

verify_command "systemctl is-active k3s-agent" "systemctl is-active k3s-agent"

# ── 3 ────────────────────────────────────────────────────────────────────
section_title "3. Порты между нодами"
echo "8472/udp — Flannel VXLAN. 10250/tcp — kubelet."
pause_for_reading "Сейчас ufw allow на worker."

if command -v ufw >/dev/null 2>&1; then
  echo "+ ufw allow 8472/udp"
  if [[ "$DRY_RUN" != "1" ]]; then
    ufw allow 8472/udp || true
  fi
  echo "+ ufw allow 10250/tcp"
  if [[ "$DRY_RUN" != "1" ]]; then
    ufw allow 10250/tcp || true
  fi
  verify_command "ufw status" "ufw status" || true
else
  echo "ufw не установлен — откройте порты в панели, если firewall есть."
fi

if [[ -f "$TOKEN_FILE" ]]; then
  rm -f "$TOKEN_FILE"
fi

# ── 4 ────────────────────────────────────────────────────────────────────
section_title "4. Итог на этом сервере"
if [[ "$VERIFY_FAIL" -ne 0 ]]; then
  echo "Есть FAIL в проверках. Разберите вывод выше."
  exit 1
fi

echo "k3s agent на $(hostname) готов (worker ${K3S_NODE_NAME})."
echo "Готовность ноды в кластере смотрите на master: kubectl get nodes"
echo
echo "=== remote: install-k3s-workers-remote.sh завершён ==="
