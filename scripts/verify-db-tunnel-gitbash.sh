#!/usr/bin/env bash
# Обратная совместимость: делегирует в scripts/dev-db-connection/07-verify-all.sh
exec bash "$(dirname "$0")/dev-db-connection/07-verify-all.sh" "$@"
