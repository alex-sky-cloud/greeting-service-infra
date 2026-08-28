#!/bin/sh
cd "$(dirname "$0")" || exit 1
URL="http://127.0.0.1:8765/"
echo "Documentation: $URL"
echo "Do not close this window while you read."
if command -v python3 >/dev/null 2>&1; then
  PY=python3
elif command -v python >/dev/null 2>&1; then
  PY=python
else
  echo "Python 3 is not installed. Open jmc-5-4/jfr-runtime-guide/toc.htm in the browser instead."
  exit 1
fi
( sleep 1
  if command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$URL"
  elif command -v open >/dev/null 2>&1; then
    open "$URL"
  fi
) &
exec "$PY" -m http.server 8765
