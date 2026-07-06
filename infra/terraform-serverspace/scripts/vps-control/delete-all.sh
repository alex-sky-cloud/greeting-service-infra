#!/usr/bin/env bash
# Удалить все серверы и изолированные сети в текущем проекте.
# 1) список id → 2) DELETE по типу (servers, затем networks) → 3) проверка «пусто».
# Внимание: необратимо. Сначала: ./scripts/list-resources.sh
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
load_serverspace_env
resolve_python || true
resolve_jq || true

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

ids_from_json() {
  local key="$1" file="$2"
  if [[ -n "$JQ" ]]; then
    "$JQ" -r --arg k "$key" '.[$k][]?.id // empty' "$file"
  elif [[ -n "$PYTHON" ]]; then
    # shellcheck disable=SC2086
    $PYTHON - "$key" "$file" <<'PY'
import json, sys
key, path = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as f:
    d = json.load(f)
for item in d.get(key, []):
    print(item["id"], flush=True)
PY
  else
    echo "Нужен python или jq для разбора JSON" >&2
    exit 1
  fi
}

count_from_json() {
  local key="$1" file="$2"
  if [[ -n "$JQ" ]]; then
    "$JQ" --arg k "$key" '.[$k] | length' "$file"
  elif [[ -n "$PYTHON" ]]; then
    # shellcheck disable=SC2086
    $PYTHON - "$key" "$file" <<'PY'
import json, sys
key, path = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as f:
    d = json.load(f)
print(len(d.get(key, [])))
PY
  else
    echo "?"
  fi
}

print_servers() {
  local n
  n=$(count_from_json servers "$TMP")
  echo "count: $n"
  if [[ -n "$JQ" ]]; then
    "$JQ" -r '.servers[]? | "\(.id)\t\(.name)\t\(.state)"' "$TMP"
  elif [[ -n "$PYTHON" ]]; then
    # shellcheck disable=SC2086
    $PYTHON - "$TMP" <<'PY'
import json, sys
for s in json.load(open(sys.argv[1], encoding="utf-8")).get("servers", []):
    print(f"{s['id']}\t{s['name']}\t{s['state']}")
PY
  fi
}

print_networks() {
  local n
  n=$(count_from_json isolated_networks "$TMP")
  echo "count: $n"
  if [[ -n "$JQ" ]]; then
    "$JQ" -r '.isolated_networks[]? | "\(.id)\t\(.name)\t\(.state)"' "$TMP"
  elif [[ -n "$PYTHON" ]]; then
    # shellcheck disable=SC2086
    $PYTHON - "$TMP" <<'PY'
import json, sys
for n in json.load(open(sys.argv[1], encoding="utf-8")).get("isolated_networks", []):
    print(f"{n['id']}\t{n['name']}\t{n['state']}")
PY
  fi
}

echo "=== servers (before) ==="
http=$(ss_fetch_to servers "$TMP")
[[ "$http" == "200" ]] || { echo "HTTP $http" >&2; cat "$TMP" >&2; exit 1; }
print_servers

echo
echo "=== DELETE servers ==="
while IFS= read -r id; do
  id=$(ss_strip "$id")
  [[ -z "$id" ]] && continue
  echo "DELETE server $id"
  ss_delete "servers/${id}"
done < <(ids_from_json servers "$TMP")

echo
echo "=== networks (before) ==="
http=$(ss_fetch_to networks/isolated "$TMP")
[[ "$http" == "200" ]] || { echo "HTTP $http" >&2; cat "$TMP" >&2; exit 1; }
print_networks

echo
echo "=== DELETE networks ==="
while IFS= read -r id; do
  id=$(ss_strip "$id")
  [[ -z "$id" ]] && continue
  echo "DELETE network $id"
  ss_delete "networks/isolated/${id}" || true
done < <(ids_from_json isolated_networks "$TMP")

echo
echo "=== after (проверка) ==="
left_servers=0
left_nets=0
http=$(ss_fetch_to servers "$TMP")
if [[ "$http" == "200" ]]; then
  left_servers=$(count_from_json servers "$TMP")
fi
echo "servers: ${left_servers}"
http=$(ss_fetch_to networks/isolated "$TMP")
if [[ "$http" == "200" ]]; then
  left_nets=$(count_from_json isolated_networks "$TMP")
fi
echo "networks: ${left_nets}"
if [[ "$left_servers" == "0" && "$left_nets" == "0" ]]; then
  echo "OK: проект пуст (серверов и сетей нет)."
else
  echo "ВНИМАНИЕ: остались ресурсы — servers=$left_servers networks=$left_nets"
  echo "См. delete-stuck-server.md"
  exit 1
fi
