#!/usr/bin/env bash
# Перебор id серверов в диапазоне — найти «призрак», не видимый в GET /servers.
# Использование: ./scripts/probe-server-ids.sh l44s1304940 l44s1304959
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
load_serverspace_env
resolve_python || true
resolve_jq || true

FROM="${1:-l44s1304740}"
TO="${2:-l44s1304760}"

from_num=${FROM#l44s}
to_num=${TO#l44s}

PROBE="$(mktemp)"
trap 'rm -f "$PROBE"' EXIT

echo "Диапазон: l44s${from_num} .. l44s${to_num}"
found=0

for ((n=from_num; n<=to_num; n++)); do
  id="l44s${n}"
  code=$(curl -sS -o "$PROBE" -w "%{http_code}" "${SS_HDR[@]}" "${SS_API_BASE}/servers/${id}")
  if [[ "$code" == "200" ]]; then
    found=1
    if [[ -n "$JQ" ]]; then
      name=$("$JQ" -r '.server.name // .name' "$PROBE")
      state=$("$JQ" -r '.server.state // .state' "$PROBE")
      nics=$("$JQ" '(.server.nics // .nics // []) | length' "$PROBE")
    elif [[ -n "$PYTHON" ]]; then
      # shellcheck disable=SC2086
      read -r name state nics < <($PYTHON - "$PROBE" <<'PY'
import json, sys
s = json.load(open(sys.argv[1], encoding="utf-8"))
s = s.get("server", s)
print(s.get("name", "?"), s.get("state", "?"), len(s.get("nics") or []))
PY
)
    else
      name=$(grep -o '"name"[[:space:]]*:[[:space:]]*"[^"]*"' "$PROBE" | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
      state=$(grep -o '"state"[[:space:]]*:[[:space:]]*"[^"]*"' "$PROBE" | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
      nics="?"
    fi
    name=${name//$'\r'/}
    state=${state//$'\r'/}
    nics=${nics//$'\r'/}
    echo "FOUND ${id} ${name} ${state} nics:${nics} HTTP:${code}"
  fi
done

if [[ "$found" -eq 0 ]]; then
  echo "Ни одного сервера в диапазоне (API)."
  echo "Если в панели карточка есть — см. delete-stuck-server.md"
fi
