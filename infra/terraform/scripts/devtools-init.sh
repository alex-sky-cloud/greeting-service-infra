#!/bin/bash
# cloud-init скрипт для первичной настройки сервера devtools.
# Выполняется автоматически при создании сервера Timeweb Cloud.

set -euo pipefail

# Обновляем пакеты
apt-get update -y
apt-get upgrade -y

# Устанавливаем зависимости
apt-get install -y \
  curl \
  wget \
  gnupg \
  apt-transport-https \
  ca-certificates \
  software-properties-common \
  openjdk-17-jdk \
  nginx \
  git

# Устанавливаем Docker Engine
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Добавляем пользователя ubuntu в группу docker
usermod -aG docker ubuntu

# Включаем Docker при старте системы
systemctl enable docker
systemctl start docker

echo "devtools-init.sh completed at $(date)" >> /var/log/devtools-init.log
