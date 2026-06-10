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
  body = file("/mnt/c/Users/sky/.ssh/id_ed25519.pub")
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
    cpu             = 2
    ram             = 4096
    disk            = 51200
  }

  # Сервер в той же приватной сети, что и K8S кластер.
  local_network {
    id = twc_vpc.main.id
  }

  # cloud-init скрипт — начальная установка зависимостей.
  # Конкретная установка Bitbucket и Registry — через Ansible или вручную.
  cloud_init = file("${path.module}/scripts/devtools-init.sh")

  project_id        = 701321
  availability_zone = "msk-1" # ru-3 — та же зона, что у БД и VPC
}

# Публичный IPv4 для devtools.
# В ru-3 Timeweb часто выдаёт на сервер только IPv6 → main_ipv4 остаётся null.
# Floating IP даёт стабильный IPv4 для SSH, Bitbucket и Docker Registry.
resource "twc_floating_ip" "devtools" {
  availability_zone = "msk-1"
  comment           = "Public IPv4 for ${var.project_name}-devtools"

  resource {
    type = "server"
    id   = twc_server.devtools.id
  }
}

output "devtools_public_ip" {
  value = coalesce(
    twc_server.devtools.main_ipv4,
    twc_floating_ip.devtools.ip,
    "ещё не создан",
  )
  description = "Публичный IPv4 сервера Bitbucket / Docker Registry."
}

output "devtools_private_ip" {
  value = coalesce(
    try(twc_server.devtools.local_network[0].ip, null),
    try([for net in twc_server.devtools.networks : net.ips[0].ip if net.type == "local"][0], null),
    "ещё не создан",
  )
  description = "Приватный IP сервера внутри VPC."
}
