#!/usr/bin/env bash
# Показать проект, серверы, изолированные сети и SSH-ключи в текущем API-проекте.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
load_serverspace_env
resolve_python || true
resolve_jq || true

if [[ -z "$PYTHON" && -z "$JQ" ]]; then
  echo "Предупреждение: нет python/jq — вывод будет сырой JSON." >&2
  echo "На Git Bash Windows используйте WSL или установите Python." >&2
fi

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

ss_fetch() {
  local path="$1"
  local http
  http=$(curl -sS -o "$TMP" -w "%{http_code}" "${SS_HDR[@]}" "${SS_API_BASE}/${path}")
  if [[ "$http" != "200" ]]; then
    echo "Ошибка API GET /${path}: HTTP ${http}" >&2
    cat "$TMP" >&2
    return 1
  fi
}

print_json_file() {
  if [[ -n "$PYTHON" ]]; then
    # shellcheck disable=SC2086
    $PYTHON -m json.tool "$TMP"
  elif [[ -n "$JQ" ]]; then
    "$JQ" . "$TMP"
  else
    cat "$TMP"
  fi
}

print_servers_table() {
  if [[ -n "$JQ" ]]; then
    local n
    n=$("$JQ" '.servers | length' "$TMP")
    echo "count: $n"
    "$JQ" -r '.servers[] | "\(.id)\t\(.name)\t\(.state)\t\(.location_id)"' "$TMP"
  elif [[ -n "$PYTHON" ]]; then
    # shellcheck disable=SC2086
    $PYTHON - "$TMP" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    d = json.load(f)
items = d.get("servers", [])
print("count:", len(items))
for s in items:
    print(f"{s['id']}\t{s['name']}\t{s['state']}\t{s['location_id']}")
PY
  else
    cat "$TMP"
  fi
}

print_networks_table() {
  if [[ -n "$JQ" ]]; then
    local n
    n=$("$JQ" '.isolated_networks | length' "$TMP")
    echo "count: $n"
    "$JQ" -r '.isolated_networks[] | "\(.id)\t\(.name)\t\(.state)\tservers=\((.server_ids // []) | join(","))"' "$TMP"
  elif [[ -n "$PYTHON" ]]; then
    # shellcheck disable=SC2086
    $PYTHON - "$TMP" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    d = json.load(f)
items = d.get("isolated_networks", [])
print("count:", len(items))
for n in items:
    servers = ",".join(n.get("server_ids") or []) or "-"
    print(f"{n['id']}\t{n['name']}\t{n['state']}\tservers={servers}")
PY
  else
    cat "$TMP"
  fi
}

print_ssh_table() {
  if [[ -n "$JQ" ]]; then
    local n
    n=$("$JQ" '(.ssh_keys // .["ssh-keys"] // .keys // []) | length' "$TMP")
    echo "count: $n"
    "$JQ" -r '(.ssh_keys // .["ssh-keys"] // .keys // [])[] | "\(.id)\t\(.name)"' "$TMP"
  elif [[ -n "$PYTHON" ]]; then
    # shellcheck disable=SC2086
    $PYTHON - "$TMP" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    d = json.load(f)
items = d.get("ssh_keys") or d.get("ssh-keys") or d.get("keys") or []
print("count:", len(items))
for k in items:
    print(f"{k.get('id', '?')}\t{k.get('name', '?')}")
PY
  else
    cat "$TMP"
  fi
}

echo "=== PROJECT ==="
ss_fetch project
print_json_file

echo
echo "=== SERVERS ==="
ss_fetch servers
print_servers_table

echo
echo "=== ISOLATED NETWORKS ==="
ss_fetch networks/isolated
print_networks_table

echo
echo "=== SSH KEYS ==="
if ss_fetch ssh-keys; then
  print_ssh_table
else
  echo "(ssh-keys недоступен)"
fi
