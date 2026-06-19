#!/usr/bin/env bash
# ============================================================================
# 08-clean-remote-database.sh
# НАЗНАЧЕНИЕ: DROP SCHEMA iso_demo, shop_demo в удалённой greeting_db (модуль app).
# ОПАСНО:     УДАЛЯЕТ учебные данные и flyway_schema_history. Требует ввод yes.
# НЕ ТРОГАЕТ: reactive_demo, другие базы, настройки сервера/K8s.
# ЗАПУСК:    bash scripts/dev-db-connection/08-clean-remote-database.sh
# ============================================================================set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/lib.sh"

CLEAN_SQL="${REPO_ROOT}/app/src/main/resources/db/clean-database-remote.sql"
STARTED_TUNNEL=0

cleanup() {
  if [[ "${STARTED_TUNNEL}" -eq 1 ]]; then
    bash "${SCRIPT_DIR}/04-stop-tunnel.sh" || true
  fi
}
trap cleanup EXIT

load_shell_secrets
load_ips_from_terraform

if [[ ! -f "${CLEAN_SQL}" ]]; then
  echo "Не найден файл: ${CLEAN_SQL}"
  exit 1
fi

PSQL_BIN=""
if ! PSQL_BIN="$(find_psql)"; then
  echo "psql не найден. Установите клиент — docs/dev-remote-db-connection.md §2.1"
  exit 1
fi

if [[ -z "${TF_VAR_db_password:-}" ]]; then
  echo "TF_VAR_db_password не задан. Выполните: source ~/.bashrc"
  exit 1
fi

echo "=== Очистка удалённой БД ==="
print_connection_summary
echo
echo "SQL : ${CLEAN_SQL}"
echo "Цель: DROP SCHEMA iso_demo, shop_demo (включая flyway_schema_history)"
echo
echo "ВНИМАНИЕ: все учебные данные и история миграций будут удалены."
read -r -p "Введите yes для продолжения: " CONFIRM
if [[ "${CONFIRM}" != "yes" ]]; then
  echo "Отменено."
  exit 0
fi

# Проверяем, открыт ли уже туннель на LOCAL_TUNNEL_PORT.
if ! python - <<PY 2>/dev/null
import socket
s = socket.socket()
s.settimeout(2)
s.connect(("127.0.0.1", ${LOCAL_TUNNEL_PORT}))
s.close()
PY
then
  echo
  echo "Туннель не найден — поднимаем..."
  bash "${SCRIPT_DIR}/03-start-tunnel.sh"
  STARTED_TUNNEL=1
else
  echo
  echo "Используем существующий туннель localhost:${LOCAL_TUNNEL_PORT}"
fi

echo
echo "=== Выполнение clean-database-remote.sql ==="
export PGPASSWORD="${TF_VAR_db_password}"
"${PSQL_BIN}" -h 127.0.0.1 -p "${LOCAL_TUNNEL_PORT}" -U "${DB_USER}" -d "${DB_NAME}" \
  -v ON_ERROR_STOP=1 \
  -f "${CLEAN_SQL}"

echo
echo "=== Проверка: оставшиеся схемы ==="
"${PSQL_BIN}" -h 127.0.0.1 -p "${LOCAL_TUNNEL_PORT}" -U "${DB_USER}" -d "${DB_NAME}" \
  -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('iso_demo', 'shop_demo');"

echo
echo "remote_clean_ok — при следующем старте приложения Flyway накатит миграции заново."
