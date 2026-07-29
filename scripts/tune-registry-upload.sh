#!/bin/bash
# ============================================================================
# tune-registry-upload.sh
#
# НАЗНАЧЕНИЕ: подготовить devtools-registry к долгим и обрывистым push с интернета.
# ЗАЧЕМ:     Registry API v2 поддерживает докачку (resumable upload), но:
#             - NAT/фаервол рвёт «медленные» TCP без keepalive;
#             - maintenance uploadpurging удаляет незавершённые upload слишком рано.
# ГДЕ:       выполняется НА devtools (SSH pipe), после setup-registry.sh.
#
# Запуск с локального ПК (Git Bash, корень репозитория):
#   export DEVTOOLS_IP=72.56.249.137
#   ssh -i ~/.ssh/id_ed25519 root@${DEVTOOLS_IP} 'bash -s' < scripts/tune-registry-upload.sh
#
# После обрыва push на клиенте: снова выполните docker push — докачка по UUID upload.
# ============================================================================

set -euo pipefail

REGISTRY_CONFIG_DIR="/opt/registry/config"
SYSCTL_FILE="/etc/sysctl.d/99-registry-upload.conf"

echo "==> TCP keepalive (меньше обрывов на медленном канале / NAT)..."
tee "${SYSCTL_FILE}" > /dev/null <<'EOF'
# Долгие docker push: не давать соединению «засыпать» для NAT/firewall.
net.ipv4.tcp_keepalive_time = 60
net.ipv4.tcp_keepalive_intvl = 10
net.ipv4.tcp_keepalive_probes = 10
EOF
sysctl --system > /dev/null

echo "==> Обновляем config.yml Registry (докачка upload, HTTP/1.1)..."
tee "${REGISTRY_CONFIG_DIR}/config.yml" > /dev/null <<'REGCONF'
version: 0.1
log:
  level: info
storage:
  filesystem:
    rootdirectory: /var/lib/registry
  delete:
    enabled: true
  maintenance:
  # Незавершённые upload хранить дольше — клиент докачает после обрыва (Registry API v2).
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

echo "==> Перезапуск Registry..."
cd /opt/registry
docker-compose restart registry
sleep 3

echo "==> Проверка..."
curl -fsS -u docker:docker "http://127.0.0.1:5000/v2/" > /dev/null
docker ps --filter name=registry --format '{{.Names}} {{.Status}}'

echo ""
echo "OK: registry готов к долгим push и докачке."
echo "На клиенте после обрыва: docker login ... && docker push ... (повтор — не с нуля)."
