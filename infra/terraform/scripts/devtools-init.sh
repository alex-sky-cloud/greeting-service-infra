#!/bin/sh
# cloud-init: первичная настройка devtools (Timeweb ru-3).
# Docker — только из репозитория Ubuntu (download.docker.com с VPS недоступен).
# nginx не ставим: GitLab CE использует свой nginx на :80.

set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

LOG=/var/log/devtools-init.log
exec >>"$LOG" 2>&1
echo "devtools-init.sh started at $(date)"

apt-get update -y
apt-get install -y \
  ca-certificates \
  openjdk-17-jdk \
  git \
  docker.io

usermod -aG docker root
systemctl enable docker
systemctl start docker
docker --version

if [ ! -f /swapfile ]; then
  fallocate -l 4G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "devtools-init.sh completed at $(date)"
