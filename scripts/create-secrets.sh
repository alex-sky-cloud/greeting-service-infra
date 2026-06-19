#!/usr/bin/env bash
# ============================================================================
# create-secrets.sh
#
# НАЗНАЧЕНИЕ: создать/обновить Kubernetes Secrets (registry, greeting-service, reactive-demo).
# ЗАЧЕМ:     деплой app и reactive-demo в K8s читают DB_URL/R2DBC_URL из Secret.
# ГДЕ:       локальный ПК с kubectl → кластер Timeweb (dev/stage/prod namespace).
# БЕЗОПАСНО: PostgreSQL не трогает. Обновляет только объекты Secret в K8s (kubectl apply).
#             Существующие данные в БД не удаляются.
#
# Использование:
#   export KUBECONFIG=~/.kube/timeweb-greeting.yaml
#   REGISTRY_HOST=1.2.3.4:5000 \
#   REGISTRY_USER=registryuser \
#   REGISTRY_PASSWORD=registrypassword \
#   DB_URL="jdbc:postgresql://10.10.0.5:5432/greeting_db" \
#   DB_USERNAME=greeting_user \
#   DB_PASSWORD=your_password \
#   bash scripts/create-secrets.sh
#
# Для reactive-demo (опционально):
#   REACTIVE_DEMO_DB_URL="jdbc:postgresql://10.10.0.5:5432/reactive_demo" \
#   REACTIVE_DEMO_R2DBC_URL="r2dbc:postgresql://10.10.0.5:5432/reactive_demo"
# ============================================================================

set -euo pipefail

: "${REGISTRY_HOST:?Переменная REGISTRY_HOST не задана}"
: "${REGISTRY_USER:?Переменная REGISTRY_USER не задана}"
: "${REGISTRY_PASSWORD:?Переменная REGISTRY_PASSWORD не задана}"
: "${DB_URL:?Переменная DB_URL не задана}"
: "${DB_USERNAME:?Переменная DB_USERNAME не задана}"
: "${DB_PASSWORD:?Переменная DB_PASSWORD не задана}"

REACTIVE_DEMO_DB_URL="${REACTIVE_DEMO_DB_URL:-${DB_URL/greeting_db/reactive_demo}}"
REACTIVE_DEMO_R2DBC_URL="${REACTIVE_DEMO_R2DBC_URL:-${REACTIVE_DEMO_DB_URL/jdbc:/r2dbc:}}"

NAMESPACES=("dev" "stage" "prod")

for NS in "${NAMESPACES[@]}"; do
  echo "==> Обрабатываем namespace: ${NS}"

  kubectl create namespace "${NS}" --dry-run=client -o yaml | kubectl apply -f -

  kubectl create secret docker-registry registry-credentials \
    --namespace="${NS}" \
    --docker-server="${REGISTRY_HOST}" \
    --docker-username="${REGISTRY_USER}" \
    --docker-password="${REGISTRY_PASSWORD}" \
    --dry-run=client -o yaml | kubectl apply -f -

  kubectl create secret generic greeting-service-secret \
    --namespace="${NS}" \
    --from-literal=DB_URL="${DB_URL}" \
    --from-literal=DB_USERNAME="${DB_USERNAME}" \
    --from-literal=DB_PASSWORD="${DB_PASSWORD}" \
    --dry-run=client -o yaml | kubectl apply -f -

  kubectl create secret generic reactive-demo-secret \
    --namespace="${NS}" \
    --from-literal=DB_URL="${REACTIVE_DEMO_DB_URL}" \
    --from-literal=R2DBC_URL="${REACTIVE_DEMO_R2DBC_URL}" \
    --from-literal=DB_USERNAME="${DB_USERNAME}" \
    --from-literal=DB_PASSWORD="${DB_PASSWORD}" \
    --dry-run=client -o yaml | kubectl apply -f -

  echo "   Secrets созданы в namespace ${NS}"
done

echo ""
echo "==> Проверка:"
for NS in "${NAMESPACES[@]}"; do
  echo "--- ${NS} ---"
  kubectl get secrets -n "${NS}"
done
