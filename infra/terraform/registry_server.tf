# =============================================================================
# registry_server.tf — VPS-сервер для Bitbucket Server и Docker Registry.
#
# Сценарий: Bitbucket Server (Data Center) и Docker Registry
# развёртываются на одном или двух отдельных VPS серверах внутри VPC.
#
# Почему отдельный VPS, а не managed-сервис?
# Bitbucket Server — self-hosted продукт Atlassian. Timeweb Cloud
# не предоставляет managed Bitbucket, поэтому разворачиваем сами.
# Docker Registry (distribution/registry:2) — лёгкий open-source registry,
# достаточно одного VPS с SSD диском.
#
# Документация twc_server:
# https://registry.terraform.io/providers/timeweb-cloud/timeweb-cloud/latest/docs/resources/twc_server
# =============================================================================

data "twc_os" "ubuntu" {
  name    = "ubuntu"
  version = "22.04"
}

data "twc_configurator" "server_configurator" {
  location    = var.location
  preset_type = "premium"
}

# SSH-ключ для доступа к серверам.
# Публичный ключ читается с локального ПК оператора.
resource "twc_ssh_key" "operator" {
  name = "${var.project_name}-operator-key"
  body = file("~/.ssh/id_rsa.pub")
}

# ─── Сервер для Bitbucket + Docker Registry ───────────────────────────────────
# Bitbucket Server минимальные требования: 4 CPU, 4 ГБ RAM.
# Docker Registry добавляет небольшую нагрузку — поднимаем на той же машине.
resource "twc_server" "devtools" {
  name = "${var.project_name}-devtools"
  os_id = data.twc_os.ubuntu.id

  ssh_keys_ids = [twc_ssh_key.operator.id]

  configuration {
    configurator_id = data.twc_configurator.server_configurator.id
    cpu             = 4
    ram             = 1024 * 8   # 8 ГБ — Bitbucket рекомендует минимум 6 ГБ
    disk            = 1024 * 100 # 100 ГБ — репозитории занимают место
  }

  # Сервер в той же приватной сети, что и K8S кластер.
  local_network {
    id = twc_vpc.main.id
  }

  # cloud-init скрипт — начальная установка зависимостей.
  # Конкретная установка Bitbucket и Registry — через Ansible или вручную.
  cloud_init = file("${path.module}/scripts/devtools-init.sh")
}

output "devtools_public_ip" {
  value       = twc_server.devtools.main_ip
  description = "Публичный IP сервера Bitbucket / Docker Registry."
}

output "devtools_private_ip" {
  value       = twc_server.devtools.local_networks[0].ip
  description = "Приватный IP сервера внутри VPC."
}
