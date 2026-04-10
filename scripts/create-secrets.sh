#!/bin/bash
# =============================================================================
# create-secrets.sh
# Создаёт Kubernetes Secrets во всех namespace.
# Запускать после получения kubeconfig и создания кластера.
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
# =============================================================================

set -euo pipefail

: "${REGISTRY_HOST:?Переменная REGISTRY_HOST не задана}"
: "${REGISTRY_USER:?Переменная REGISTRY_USER не задана}"
: "${REGISTRY_PASSWORD:?Переменная REGISTRY_PASSWORD не задана}"
: "${DB_URL:?Переменная DB_URL не задана}"
: "${DB_USERNAME:?Переменная DB_USERNAME не задана}"
: "${DB_PASSWORD:?Переменная DB_PASSWORD не задана}"

NAMESPACES=("dev" "stage" "prod")

for NS in "${NAMESPACES[@]}"; do
  echo "==> Обрабатываем namespace: ${NS}"

  # Создаём namespace если не существует
  kubectl create namespace "${NS}" --dry-run=client -o yaml | kubectl apply -f -

  # Secret для pull из Docker Registry
  kubectl create secret docker-registry registry-credentials \
    --namespace="${NS}" \
    --docker-server="${REGISTRY_HOST}" \
    --docker-username="${REGISTRY_USER}" \
    --docker-password="${REGISTRY_PASSWORD}" \
    --dry-run=client -o yaml | kubectl apply -f -

  # Secret с переменными окружения приложения
  kubectl create secret generic greeting-service-secret \
    --namespace="${NS}" \
    --from-literal=DB_URL="${DB_URL}" \
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
