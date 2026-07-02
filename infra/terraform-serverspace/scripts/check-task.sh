#!/usr/bin/env bash
# Статус фоновой задачи Serverspace (создание/удаление VM).
# Использование: ./scripts/check-task.sh lt6334455
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
load_serverspace_env
resolve_python || true
resolve_jq || true

TASK_ID="${1:-}"
if [[ -z "$TASK_ID" ]]; then
  echo "Использование: $0 TASK_ID" >&2
  echo "Пример из ошибки Terraform: task 'lt6334455' failed" >&2
  exit 1
fi

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

http=$(curl -sS -o "$TMP" -w "%{http_code}" "${SS_HDR[@]}" "${SS_API_BASE}/tasks/${TASK_ID}")
if [[ "$http" != "200" ]]; then
  echo "Ошибка API GET /tasks/${TASK_ID}: HTTP ${http}" >&2
  cat "$TMP" >&2
  exit 1
fi

if [[ -n "$PYTHON" ]]; then
  # shellcheck disable=SC2086
  $PYTHON -m json.tool "$TMP"
elif [[ -n "$JQ" ]]; then
  "$JQ" . "$TMP"
else
  cat "$TMP"
fi
