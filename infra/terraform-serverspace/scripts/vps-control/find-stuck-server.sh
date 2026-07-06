#!/usr/bin/env bash
# Диагностика задачи Terraform после ошибки apply.
#
#   ./scripts/find-stuck-server.sh lt6336740
#   ./scripts/find-stuck-server.sh --delete lt6336740
#
# Id сети вторым аргументом НЕ нужен — все сети проекта выводятся автоматически.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"

usage() {
  cat <<'EOF'
Использование:
  ./scripts/find-stuck-server.sh TASK_ID
  ./scripts/find-stuck-server.sh --delete TASK_ID

TASK_ID — из ошибки Terraform: task 'lt6336740' failed → lt6336740

Скрипт сам показывает:
  - статус задачи (Completed / Failed, server_id)
  - ВСЕ изолированные сети проекта
  - список VM и probe id рядом с ними

Id сети указывать не нужно:
  ./scripts/find-stuck-server.sh lt6336740

Опционально (только одна сеть, редко):
  ./scripts/find-stuck-server.sh lt6336740 l44n754

Документация: scripts/find-stuck-server.md
EOF
}

load_serverspace_env
resolve_python || true
resolve_jq || true

DO_DELETE=0
ARGS=()
for a in "$@"; do
  case "$a" in
    --delete) DO_DELETE=1 ;;
    -h|--help) usage; exit 0 ;;
    *) ARGS+=("$a") ;;
  esac
done

if [[ ${#ARGS[@]} -eq 0 ]]; then
  usage >&2
  exit 1
fi

TASK_ID=$(ss_strip "${ARGS[0]}")
FOCUS_NET=""
if [[ ${#ARGS[@]} -ge 2 && "${ARGS[1]}" =~ ^l44n ]]; then
  FOCUS_NET=$(ss_strip "${ARGS[1]}")
fi

TMP="$(mktemp)"
PROBE="$(mktemp)"
IDS_FILE="$(mktemp)"
trap 'rm -f "$TMP" "$PROBE" "$IDS_FILE"' EXIT

echo "=== task ${TASK_ID} ==="
http=$(ss_fetch_to "tasks/${TASK_ID}" "$TMP")
if [[ "$http" != "200" ]]; then
  echo "HTTP ${http}" >&2
  cat "$TMP" >&2
  exit 1
fi
json_print "$TMP"

TASK_STATUS=""
SERVER_FROM_TASK=""
if [[ -n "$JQ" ]]; then
  TASK_STATUS=$("$JQ" -r '.task.is_completed // ""' "$TMP")
  SERVER_FROM_TASK=$("$JQ" -r '.task.server_id // ""' "$TMP")
elif [[ -n "$PYTHON" ]]; then
  # shellcheck disable=SC2086
  read -r TASK_STATUS SERVER_FROM_TASK < <($PYTHON - "$TMP" <<'PY'
import json, sys
t = json.load(open(sys.argv[1], encoding="utf-8")).get("task", {})
print(t.get("is_completed", ""), t.get("server_id", ""))
PY
)
else
  TASK_STATUS=$(grep -o '"is_completed"[[:space:]]*:[[:space:]]*"[^"]*"' "$TMP" | sed 's/.*"\([^"]*\)"$/\1/')
  SERVER_FROM_TASK=$(grep -o '"server_id"[[:space:]]*:[[:space:]]*"[^"]*"' "$TMP" | sed 's/.*"\([^"]*\)"$/\1/')
fi
SERVER_FROM_TASK=$(ss_strip "$SERVER_FROM_TASK")

if [[ -n "$SERVER_FROM_TASK" ]]; then
  echo "server_id from task: ${SERVER_FROM_TASK}"
else
  echo "server_id from task: (пусто — VM не создана или API не вернул id)"
fi
echo "task status: ${TASK_STATUS:-?}"

echo
if [[ -n "$FOCUS_NET" ]]; then
  echo "=== network ${FOCUS_NET} (запрошена вручную) ==="
  http=$(ss_fetch_to "networks/isolated/${FOCUS_NET}" "$TMP")
  if [[ "$http" == "200" ]]; then
    json_print "$TMP"
  else
    echo "HTTP ${http} — сеть не найдена."
    cat "$TMP"
  fi
  echo
fi

echo "=== isolated networks (all in project) ==="
http=$(ss_fetch_to networks/isolated "$TMP")
if [[ "$http" == "200" ]]; then
  if [[ -n "$JQ" ]]; then
    n=$("$JQ" '.isolated_networks | length' "$TMP")
    echo "count: $n"
    "$JQ" -r '.isolated_networks[] | "\(.id)\t\(.name)\t\(.state)\tservers=\((.server_ids // []) | join(","))"' "$TMP"
  elif [[ -n "$PYTHON" ]]; then
    # shellcheck disable=SC2086
    $PYTHON - "$TMP" <<'PY'
import json, sys
items = json.load(open(sys.argv[1], encoding="utf-8")).get("isolated_networks", [])
print("count:", len(items))
for n in items:
    s = ",".join(n.get("server_ids") or []) or "-"
    print(f"{n['id']}\t{n['name']}\t{n['state']}\tservers={s}")
PY
  else
    json_print "$TMP"
  fi
  echo
  echo "Подсказка: id сети — в terraform.tfstate (reactive_net) или в строках выше."
else
  echo "HTTP ${http}"
fi

echo
echo "=== servers list ==="
http=$(ss_fetch_to servers "$TMP")
: >"$IDS_FILE"
if [[ "$http" == "200" ]]; then
  if [[ -n "$JQ" ]]; then
    n=$("$JQ" '.servers | length' "$TMP")
    echo "count: $n"
    "$JQ" -r '.servers[] | "\(.id)\t\(.name)\t\(.state)"' "$TMP"
    "$JQ" -r '.servers[].id' "$TMP" >"$IDS_FILE"
  elif [[ -n "$PYTHON" ]]; then
    # shellcheck disable=SC2086
    $PYTHON - "$TMP" "$IDS_FILE" <<'PY'
import json, sys
ss = json.load(open(sys.argv[1], encoding="utf-8")).get("servers", [])
print("count:", len(ss))
with open(sys.argv[2], "w", encoding="utf-8") as out:
    for s in ss:
        print(f"{s['id']}\t{s['name']}\t{s['state']}")
        out.write(s["id"] + "\n")
PY
  else
    json_print "$TMP"
  fi
else
  echo "HTTP ${http}"
fi

echo
echo "=== probe server ids (рядом с текущими VM) ==="
if [[ ! -s "$IDS_FILE" ]]; then
  echo "(нет VM в списке — probe пропущен)"
else
  min=999999999
  max=0
  while IFS= read -r sid; do
    sid=$(ss_strip "$sid")
    [[ "$sid" =~ ^l44s([0-9]+)$ ]] || continue
    n="${BASH_REMATCH[1]}"
    (( n < min )) && min=$n
    (( n > max )) && max=$n
  done <"$IDS_FILE"
  from_num=$((min > 3 ? min - 3 : 1))
  to_num=$((max + 3))
  echo "диапазон: l44s${from_num} .. l44s${to_num}"
  found=0
  for ((n=from_num; n<=to_num; n++)); do
    id="l44s${n}"
    code=$(curl -sS -o "$PROBE" -w "%{http_code}" -H "X-API-KEY: ${SERVERSPACE_TOKEN}" "${SS_API_BASE}/servers/${id}")
    if [[ "$code" == "200" ]]; then
      found=1
      if [[ -n "$JQ" ]]; then
        name=$("$JQ" -r '.server.name // .name' "$PROBE")
        state=$("$JQ" -r '.server.state // .state' "$PROBE")
      elif [[ -n "$PYTHON" ]]; then
        # shellcheck disable=SC2086
        read -r name state < <($PYTHON - "$PROBE" <<'PY'
import json, sys
raw = json.load(open(sys.argv[1], encoding="utf-8"))
s = raw.get("server", raw)
print(s.get("name", "?"), s.get("state", "?"))
PY
)
      else
        name="?"
        state="?"
      fi
      name=${name//$'\r'/}
      state=${state//$'\r'/}
      echo "FOUND ${id} ${name} ${state} HTTP:${code}"
    fi
  done
  [[ "$found" -eq 0 ]] && echo "(в диапазоне id не найдено — «призрака» нет)"
fi

if [[ "$DO_DELETE" -eq 1 && -n "$SERVER_FROM_TASK" && "$TASK_STATUS" == "Failed" ]]; then
  echo
  echo "=== DELETE (task Failed): ${SERVER_FROM_TASK} ==="
  ss_delete "servers/${SERVER_FROM_TASK}"
elif [[ "$DO_DELETE" -eq 1 ]]; then
  echo
  echo "Удаление пропущено: task не Failed или нет server_id (status=${TASK_STATUS:-?})."
fi
