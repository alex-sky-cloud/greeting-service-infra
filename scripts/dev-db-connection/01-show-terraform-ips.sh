#!/usr/bin/env bash
# ============================================================================
# 01-show-terraform-ips.sh
# НАЗНАЧЕНИЕ: показать IP из terraform output (DB, devtools, VPC).
# ГДЕ:       локальный ПК (Git Bash). Только чтение, сервер не меняет.
# ЗАПУСК:    bash scripts/dev-db-connection/01-show-terraform-ips.sh
# ============================================================================set -euo pipefail

source "$(dirname "$0")/lib.sh"
load_ips_from_terraform

echo "=== Адреса из infra/terraform (terraform output) ==="
echo
print_connection_summary
echo
echo "=== Команды terraform (выполняются через WSL Ubuntu) ==="
echo "db_host            = ${DB_HOST}"
echo "db_port            = ${DB_PORT}"
echo "db_jdbc_url        = $(terraform_output_raw db_jdbc_url 2>/dev/null || echo n/a)"
echo "devtools_public_ip = ${DEVTOOLS_PUBLIC_IP}"
echo "devtools_private_ip= ${DEVTOOLS_PRIVATE_IP}"
echo
echo "=== Что означает каждый адрес ==="
cat <<'EOF'
10.10.0.0/24 (VPC)
  Приватная сеть Timeweb Cloud (twc_vpc). Создаётся в infra/terraform/vpc.tf.
  Все сервисы (K8s, PostgreSQL, devtools) общаются по IP из этого диапазона.

10.10.0.5 (db_host) — пример, актуальный IP см. выше
  Внутренний IP managed PostgreSQL (twc_database_cluster) в VPC.
  Источник: infra/terraform/database.tf → output db_host.
  Из домашнего интернета напрямую недоступен.

72.56.249.137 (devtools_public_ip) — пример, актуальный IP см. выше
  Публичный floating IP VPS devtools (Bitbucket + Docker Registry).
  Источник: infra/terraform/registry_server.tf → output devtools_public_ip.
  Используется для SSH jump-хоста и docker push/pull.

10.10.0.6 (devtools_private_ip) — пример
  Приватный IP того же VPS внутри VPC. Registry/K8s ходят сюда по внутренней сети.

15432 (LOCAL_TUNNEL_PORT)
  Локальный порт на вашем ПК — не из Terraform. Вы сами выбираете свободный порт;
  ssh -L пробрасывает localhost:15432 → db_host:5432 через devtools.
EOF
