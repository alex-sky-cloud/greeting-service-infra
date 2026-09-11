#!/usr/bin/env bash
# ============================================================================
# reset-servers-remote.sh
# НАЗНАЧЕНИЕ: откат prepare-server + k3s на VPS. SSH-ключи не трогаем.
# ГДЕ:       выполняется НА СЕРВЕРЕ (root). Вызывается через reset-servers.sh.
# АРГУМЕНТЫ: <ROLE_NAME>
# ============================================================================
set -euo pipefail

ROLE_NAME="${1:-unknown}"
DRY_RUN="${DRY_RUN:-0}"

section_title() {
  echo
  echo "================================================================"
  echo "  $1"
  echo "================================================================"
}

run_cmd() {
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "+ $*"
  else
    "$@"
  fi
}

role_had_docker_from_prepare() {
  case "$ROLE_NAME" in
    devtools|traefik-1|traefik-2|storage-1|storage-2|traefik-*|storage-*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

restore_original_hostname() {
  local current rev pub_ip

  current="$(hostname -s 2>/dev/null || hostname)"
  case "$current" in
    k8s-*|devtools|traefik-*|storage-*)
      :
      ;;
    *)
      echo "hostname «${current}» не из prepare-server — не меняем."
      return 0
      ;;
  esac

  pub_ip="$(curl -sf --max-time 8 ifconfig.me 2>/dev/null || curl -sf --max-time 8 icanhazip.com 2>/dev/null || true)"
  if [[ -z "$pub_ip" ]]; then
    echo "WARN: не удалось узнать публичный IP — hostname оставлен «${current}»."
    return 0
  fi

  if command -v dig >/dev/null 2>&1; then
    rev="$(dig +short -x "$pub_ip" 2>/dev/null | sed 's/\.$//' | head -1)"
  else
    rev=""
  fi

  if [[ -n "$rev" ]]; then
    echo "hostname: ${current} → ${rev}"
    if [[ "$DRY_RUN" != "1" ]]; then
      hostnamectl set-hostname "$rev"
    fi
  else
    echo "WARN: reverse DNS для ${pub_ip} пуст — hostname оставлен «${current}»."
  fi
}

section_title "reset-servers: роль «${ROLE_NAME}»"
echo "SSH-ключи в /root/.ssh/authorized_keys не удаляем."

# --- k3s ---------------------------------------------------------------------
section_title "1. k3s"
if [[ "$DRY_RUN" == "1" ]]; then
  echo "+ /usr/local/bin/k3s-uninstall.sh (если есть)"
  echo "+ /usr/local/bin/k3s-agent-uninstall.sh (если есть)"
else
  if [[ -x /usr/local/bin/k3s-uninstall.sh ]]; then
    /usr/local/bin/k3s-uninstall.sh || true
  fi
  if [[ -x /usr/local/bin/k3s-agent-uninstall.sh ]]; then
    /usr/local/bin/k3s-agent-uninstall.sh || true
  fi
  rm -f /root/k3s-node-token /root/.k3s-join-token
fi

# --- ufw (ставили в prepare-server) ------------------------------------------
section_title "2. ufw"
if command -v ufw >/dev/null 2>&1; then
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "+ ufw --force disable"
    echo "+ apt-get purge -y ufw"
  else
    ufw --force disable || true
    run_cmd apt-get purge -y ufw || true
  fi
else
  echo "ufw не установлен — пропуск."
fi

# --- git (ставили в prepare-server) ------------------------------------------
section_title "3. git"
if dpkg -l git 2>/dev/null | grep -q '^ii'; then
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "+ apt-get purge -y git git-man"
  else
    run_cmd apt-get purge -y git git-man || true
  fi
else
  echo "git не установлен — пропуск."
fi

# --- docker (только роли devtools / traefik / storage) -----------------------
section_title "4. Docker"
if role_had_docker_from_prepare; then
  if command -v docker >/dev/null 2>&1 || dpkg -l docker.io 2>/dev/null | grep -q '^ii'; then
    if [[ "$DRY_RUN" == "1" ]]; then
      echo "+ systemctl stop docker"
      echo "+ apt-get purge -y docker.io containerd"
    else
      systemctl stop docker 2>/dev/null || true
      systemctl disable docker 2>/dev/null || true
      run_cmd apt-get purge -y docker.io containerd || true
    fi
  else
    echo "docker не установлен — пропуск."
  fi
else
  echo "Для роли «${ROLE_NAME}» Docker в prepare-server не ставился — пропуск."
fi

# --- hostname ----------------------------------------------------------------
section_title "5. hostname"
restore_original_hostname

# --- autoremove --------------------------------------------------------------
section_title "6. apt autoremove"
if [[ "$DRY_RUN" == "1" ]]; then
  echo "+ apt-get autoremove -y"
else
  run_cmd apt-get autoremove -y || true
fi

# --- проверка SSH-ключа ------------------------------------------------------
section_title "7. Проверка"
if [[ "$DRY_RUN" == "1" ]]; then
  echo "+ test -f /root/.ssh/authorized_keys"
else
  if test -f /root/.ssh/authorized_keys; then
    echo "SSH: /root/.ssh/authorized_keys на месте."
  else
    echo "WARN: /root/.ssh/authorized_keys не найден."
  fi
fi

echo
echo "--- Итог на $(hostname) ---"
command -v k3s >/dev/null 2>&1 && echo "WARN: k3s всё ещё в PATH" || echo "k3s: removed"
command -v ufw >/dev/null 2>&1 && echo "WARN: ufw всё ещё в PATH" || echo "ufw: removed"
command -v git >/dev/null 2>&1 && echo "WARN: git всё ещё в PATH" || echo "git: removed"
command -v docker >/dev/null 2>&1 && echo "WARN: docker всё ещё в PATH" || echo "docker: removed (или не ставился)"
echo "=== reset-servers-remote.sh завершён для «${ROLE_NAME}» ==="
