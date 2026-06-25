#!/bin/bash
# ============================================================================
# setup-registry.sh
#
# НАЗНАЧЕНИЕ: установить Docker Registry (distribution/registry:2) на devtools-сервере.
# ЗАЧЕМ:     хранить образы app/reactive-demo для pull в K8s.
# ГДЕ:       выполняется НА devtools (SSH pipe), после terraform apply.
# ВНИМАНИЕ:  создаёт/обновляет /opt/registry; существующие образы в volume сохраняются
#             (delete enabled, но скрипт не делает purge). PostgreSQL не трогает.
#
# Запуск с локального ПК (Git Bash, корень репозитория):
#   ssh -i ~/.ssh/id_ed25519 root@<DEVTOOLS_IP> 'bash -s' < scripts/setup-registry.sh
#
# Учётные данные: REGISTRY_USER / REGISTRY_PASSWORD (env перед pipe)
# ============================================================================

set -euo pipefail

REGISTRY_PORT=5000
REGISTRY_USER="${REGISTRY_USER:-docker}"
REGISTRY_PASSWORD="${REGISTRY_PASSWORD:-docker}"
REGISTRY_DATA_DIR="/opt/registry/data"
REGISTRY_CONFIG_DIR="/opt/registry/config"
REGISTRY_AUTH_DIR="/opt/registry/auth"

echo "==> Создаём директории для Registry..."
sudo mkdir -p "${REGISTRY_DATA_DIR}" "${REGISTRY_CONFIG_DIR}" "${REGISTRY_AUTH_DIR}"

echo "==> Создаём htpasswd файл для аутентификации..."
# Устанавливаем apache2-utils для утилиты htpasswd
sudo apt-get install -y apache2-utils

# ЗАМЕНИТЕ REGISTRY_USER / REGISTRY_PASSWORD или задайте через env перед запуском
sudo htpasswd -Bbn "${REGISTRY_USER}" "${REGISTRY_PASSWORD}" | sudo tee "${REGISTRY_AUTH_DIR}/htpasswd" > /dev/null

echo "==> Создаём конфигурацию Registry..."
sudo tee "${REGISTRY_CONFIG_DIR}/config.yml" > /dev/null <<'REGCONF'
version: 0.1
log:
  level: info
storage:
  filesystem:
    rootdirectory: /var/lib/registry
  delete:
    enabled: true
  maintenance:
    uploadpurging:
      enabled: true
      age: 336h
      interval: 24h
      dryrun: false
http:
  addr: :5000
  headers:
    X-Content-Type-Options: [nosniff]
  http2:
    disabled: true
auth:
  htpasswd:
    realm: Registry Realm
    path: /auth/htpasswd
health:
  storagedriver:
    enabled: true
    interval: 10s
    threshold: 3
REGCONF

echo "==> Запускаем Docker Registry..."
sudo apt-get install -y docker-compose
sudo tee /opt/registry/docker-compose.yml > /dev/null <<COMPOSE
version: '3.8'
services:
  registry:
    image: registry:2
    restart: always
    ports:
      - "${REGISTRY_PORT}:5000"
    volumes:
      - ${REGISTRY_DATA_DIR}:/var/lib/registry
      - ${REGISTRY_CONFIG_DIR}/config.yml:/etc/docker/registry/config.yml:ro
      - ${REGISTRY_AUTH_DIR}:/auth:ro
COMPOSE

cd /opt/registry
sudo docker-compose up -d

echo "==> Docker Registry запущен на порту ${REGISTRY_PORT}"
echo "==> Проверка: curl http://localhost:${REGISTRY_PORT}/v2/"
curl -u "${REGISTRY_USER}:${REGISTRY_PASSWORD}" "http://localhost:${REGISTRY_PORT}/v2/"
echo ""
echo "==> Готово! Registry доступен по адресу: http://$(curl -s ifconfig.me):${REGISTRY_PORT}"
