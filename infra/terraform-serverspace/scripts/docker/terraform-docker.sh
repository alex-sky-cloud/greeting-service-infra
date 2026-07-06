#!/usr/bin/env bash
set -euo pipefail

# Каталог infra/terraform-serverspace — на два уровня выше самого скрипта
TF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="docker/docker-compose.yml"
ENV_FILE="docker/.env"

cd "$TF_DIR"

run_tf() {
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" run --rm terraform "$@"
}

case "${1:-}" in
  init)
    run_tf init
    ;;
  validate)
    run_tf validate
    ;;
  plan)
    run_tf plan -out=tfplan
    ;;
  apply)
    run_tf apply tfplan
    ;;
  destroy)
    run_tf destroy
    ;;
  *)
    echo "Использование: $0 {init|validate|plan|apply|destroy}"
    exit 1
    ;;
esac