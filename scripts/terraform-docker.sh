#!/usr/bin/env bash
# Локальный Terraform в Docker (Git Bash).
# Использование из корня репозитория:
#   ./scripts/terraform-docker.sh init
#   ./scripts/terraform-docker.sh plan
#   ./scripts/terraform-docker.sh apply

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TF_DIR="$REPO_ROOT/infra/terraform"

cd "$TF_DIR"

args=(compose -f docker/docker-compose.yml)
if [[ -f docker/.env ]]; then
  args+=(--env-file docker/.env)
fi
args+=(run --rm terraform)

if [[ $# -gt 0 ]]; then
  args+=("$@")
else
  args+=(-help)
fi

exec docker "${args[@]}"
