#!/usr/bin/env bash
# Проверяет, что локальный порт туннеля (localhost:LOCAL_TUNNEL_PORT) принимает TCP-соединения.
set -euo pipefail

source "$(dirname "$0")/lib.sh"

echo "=== Проверка TCP localhost:${LOCAL_TUNNEL_PORT} ==="
echo "(Туннель должен быть поднят: 03-start-tunnel.sh)"
echo

python - <<PY
import socket
port = ${LOCAL_TUNNEL_PORT}
s = socket.socket()
s.settimeout(5)
try:
    s.connect(("127.0.0.1", port))
    print(f"tunnel_ok: localhost:{port}")
except OSError as e:
    print(f"tunnel_fail: {e}")
    raise SystemExit(1)
finally:
    s.close()
PY

echo
echo "Порт открыт. Для запроса к БД: bash scripts/dev-db-connection/06-psql-test.sh"
