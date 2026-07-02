# Общие функции для скриптов Serverspace API.

SS_SCRIPT_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SS_ROOT="$(cd "${SS_SCRIPT_LIB_DIR}/.." && pwd)"

PYTHON=""
JQ=""

load_serverspace_env() {
  cd "$SS_ROOT"

  if [[ -z "${SERVERSPACE_TOKEN:-}" && -f docker/.env ]]; then
    set -a
    # shellcheck disable=SC1091
    source docker/.env
    set +a
  fi

  if [[ -z "${SERVERSPACE_TOKEN:-}" ]]; then
    echo "Ошибка: задайте SERVERSPACE_TOKEN (export) или заполните docker/.env" >&2
    exit 1
  fi
  # Git Bash / .env с CRLF
  SERVERSPACE_TOKEN=${SERVERSPACE_TOKEN//$'\r'/}

  SS_API_BASE="${SS_API_BASE:-https://api.serverspace.ru/api/v1}"
  SS_HDR=(-H "X-API-KEY: ${SERVERSPACE_TOKEN}")
}

# Убрать CR/LF из id и путей (Windows).
ss_strip() {
  local v="$1"
  v=${v//$'\r'/}
  v=${v//$'\n'/}
  v="${v#"${v%%[![:space:]]*}"}"
  v="${v%"${v##*[![:space:]]}"}"
  printf '%s' "$v"
}

ss_get() {
  curl -sS "${SS_HDR[@]}" "${SS_API_BASE}/$1"
}

ss_delete() {
  local api_path
  api_path=$(ss_strip "$1")
  curl -sS -w "\nHTTP:%{http_code}\n" -X DELETE \
    -H "X-API-KEY: ${SERVERSPACE_TOKEN}" \
    "${SS_API_BASE}/${api_path}"
}

# GET → файл; возвращает HTTP-код (200 = ок).
ss_fetch_to() {
  local api_path="$1" out_file="$2"
  curl -sS -o "$out_file" -w "%{http_code}" "${SS_HDR[@]}" "${SS_API_BASE}/${api_path}"
}

json_print() {
  local file="$1"
  if [[ -n "$PYTHON" ]]; then
    # shellcheck disable=SC2086
    $PYTHON -m json.tool "$file"
  elif [[ -n "$JQ" ]]; then
    "$JQ" . "$file"
  else
    cat "$file"
  fi
}

# Рабочий Python (не заглушка Windows Store).
resolve_python() {
  PYTHON=""
  local cmd
  for cmd in python3 python; do
    if command -v "$cmd" >/dev/null 2>&1 && "$cmd" -c "import json" >/dev/null 2>&1; then
      PYTHON="$cmd"
      return 0
    fi
  done
  if command -v py >/dev/null 2>&1 && py -3 -c "import json" >/dev/null 2>&1; then
    PYTHON="py -3"
    return 0
  fi
  return 1
}

resolve_jq() {
  JQ=""
  if command -v jq >/dev/null 2>&1; then
    JQ=jq
    return 0
  fi
  return 1
}

require_json_tool() {
  resolve_python || resolve_jq || {
    echo "Нужен python (с модулем json) или jq. Установите Python для Git Bash или jq." >&2
    exit 1
  }
}

require_python3() {
  require_json_tool
}
