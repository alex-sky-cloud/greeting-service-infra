#!/bin/sh
# cloud-init: первичная настройка devtools (Timeweb ru-3).
# Docker — только из репозитория Ubuntu (download.docker.com с VPS недоступен).

set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

LOG=/var/log/devtools-init.log
exec >>"$LOG" 2>&1
echo "devtools-init.sh started at $(date)"

apt-get update -y
apt-get install -y \
  ca-certificates \
  openjdk-17-jdk \
  nginx \
  git \
  docker.io

usermod -aG docker root
systemctl enable docker
systemctl start docker
docker --version

echo "devtools-init.sh completed at $(date)"
