#!/usr/bin/env bash
# ============================================================================
# install-k3s-master-remote.sh
# НАЗНАЧЕНИЕ: установка k3s server (control-plane) на ЭТОМ VPS.
# ГДЕ:       выполняется НА СЕРВЕРЕ (root). Вызывается через install-k3s-master.sh.
# ============================================================================
set -euo pipefail

DRY_RUN="${DRY_RUN:-0}"
NONINTERACTIVE="${NONINTERACTIVE:-0}"
TOKEN_FILE="/root/k3s-node-token"

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

run_shell() {
  echo "+ $1"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  (dry-run: команда не выполнялась)"
    return 0
  fi
  bash -c "$1"
}

echo "=== install-k3s-master-remote.sh — этот сервер: $(hostname) ==="
echo "Пользователь: $(id -un)"
echo

# ── 0 ────────────────────────────────────────────────────────────────────
section_title "0. Что будет сделано на ЭТОМ сервере"
echo "Это фаза 2. Здесь поднимается k3s control-plane (master)."
echo "Java-приложение на master не ставится. Traefik внутри k3s отключается:"
echo "вход из интернета — отдельный Traefik (§9 гайда)."
echo
echo "Шаги:"
echo "  1) curl https://get.k3s.io | INSTALL_K3S_EXEC=\"--disable traefik\" sh -"
echo "  2) kubectl get nodes  (пока одна нода — этот master)"
echo "  3) cat /var/lib/rancher/k3s/server/node-token"
echo "  4) записать токен в ${TOKEN_FILE} (скрипт на ПК запишет K3S_TOKEN в infra-servers.env)"
echo "  5) открыть порт 6443 для будущих worker"
pause_for_reading "Пояснение фазы 2 на этом сервере."

# ── 1 ────────────────────────────────────────────────────────────────────
section_title "1. Установка k3s server (control-plane)"
echo "K3s даёт установочный скрипт для systemd/openrc."
echo "Флаг --disable traefik: не ставить встроенный Ingress — у нас Traefik на своих VPS."
pause_for_reading "Сейчас будет curl | sh установки k3s."

subsection_doing "установка k3s (если ещё не стоит)"
if [[ "$DRY_RUN" == "1" ]]; then
  echo "+ curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC=\"--disable traefik\" sh -"
  echo "  (dry-run: команда не выполнялась)"
elif command -v k3s >/dev/null 2>&1 && systemctl is-active --quiet k3s 2>/dev/null; then
  echo "k3s уже установлен и активен — повторную установку пропускаем."
  verify_command "версия k3s" "k3s --version"
else
  echo "Команда:"
  echo "  curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC=\"--disable traefik\" sh -"
  echo "----- вывод -----"
  curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik" sh -
  echo "----- конец -----"
  subsection_result "установщик завершился"
fi

# ── 2 ────────────────────────────────────────────────────────────────────
section_title "2. Проверка: control plane жив (пока одна нода)"
echo "Выполняем на этом удалённом сервере, где ставим k3s."
echo "Если kubectl в PATH — увидите этот узел. Статус Ready."
pause_for_reading "Сейчас kubectl get nodes."

if [[ "$DRY_RUN" != "1" ]]; then
  echo "Ожидание Ready (до 90 с)..."
  ready=0
  for _ in $(seq 1 18); do
    if kubectl get nodes 2>/dev/null | grep -q ' Ready '; then
      ready=1
      break
    fi
    if k3s kubectl get nodes 2>/dev/null | grep -q ' Ready '; then
      ready=1
      break
    fi
    sleep 5
  done
  if [[ "$ready" -ne 1 ]]; then
    echo "Предупреждение: за 90 с не увидели Ready — проверка ниже покажет вывод."
  fi
fi

if command -v kubectl >/dev/null 2>&1; then
  verify_command "kubectl get nodes" "kubectl get nodes"
else
  echo "kubectl не в PATH — используем обёртку k3s kubectl."
  verify_command "k3s kubectl get nodes" "k3s kubectl get nodes"
fi

# ── 3 ────────────────────────────────────────────────────────────────────
section_title "3. Токен для worker"
echo "Скопируйте весь вывод — это K3S_TOKEN для каждого worker."
echo "Скрипт дополнительно запишет токен в файл ${TOKEN_FILE}."
pause_for_reading "Сейчас cat node-token и запись файла."

verify_command \
  "cat /var/lib/rancher/k3s/server/node-token" \
  "cat /var/lib/rancher/k3s/server/node-token"

subsection_doing "запись токена в ${TOKEN_FILE}"
if [[ "$DRY_RUN" == "1" ]]; then
  echo "  (dry-run: файл не создавался)"
else
  umask 077
  cat /var/lib/rancher/k3s/server/node-token > "$TOKEN_FILE"
  chmod 600 "$TOKEN_FILE"
  echo "Записано: ${TOKEN_FILE} ($(wc -c < "$TOKEN_FILE") байт)"
fi
subsection_result "файл токена на сервере готов"

# ── 4 ────────────────────────────────────────────────────────────────────
section_title "4. Порт API для worker (6443)"
echo "Worker подключается к этому server по https://ЭТОТ_IP:6443."
echo "Если ufw включён — без 6443/tcp join с worker не пройдёт."
pause_for_reading "Сейчас ufw allow 6443."

if command -v ufw >/dev/null 2>&1; then
  run_shell "ufw allow 6443/tcp"
  run_shell "ufw allow 8472/udp || true"
  run_shell "ufw allow 10250/tcp || true"
  verify_command "ufw status" "ufw status" || true
else
  echo "ufw не установлен — откройте 6443/tcp в панели провайдера, если есть firewall."
fi

# ── 5 ────────────────────────────────────────────────────────────────────
section_title "5. Итог на этом сервере"
if [[ "$VERIFY_FAIL" -ne 0 ]]; then
  echo "Есть FAIL в проверках. Разберите вывод выше."
  exit 1
fi

echo "k3s server на $(hostname) готов (control-plane)."
echo
echo "ВНИМАНИЕ. На этом сервере создан файл:"
echo "  ${TOKEN_FILE}"
echo
echo "Локальный скрипт скопирует файл на ПК и запишет K3S_TOKEN в infra-servers.env."
echo "Дальше install-k3s-workers.sh сам возьмёт токен из env."
echo
echo "Worker: K3S_TOKEN + K3S_URL=https://<IP_ЭТОГО_MASTER>:6443"
echo "Не публикуйте токен."
echo
echo "=== remote: install-k3s-master-remote.sh завершён ==="
