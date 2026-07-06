#!/usr/bin/env bash
set -euo pipefail

# Каталог infra/terraform-serverspace — на два уровня выше самого скрипта
TF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$TF_DIR"

echo "Очистка локальных служебных файлов Terraform в: $TF_DIR"

rm -rf .terraform
rm -f .terraform.lock.hcl
rm -f .terraform.tfstate.lock.info
rm -f terraform.tfstate
rm -f terraform.tfstate.backup
rm -f tfplan

echo "Готово. Каталог подготовлен для нового init."