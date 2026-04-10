# =============================================================================
# kubernetes.tf — управляемый Kubernetes-кластер в Timeweb Cloud.
#
# Документация по ресурсу twc_k8s_cluster:
# https://registry.terraform.io/providers/timeweb-cloud/timeweb-cloud/latest/docs/resources/k8s_cluster
#
# Документация по ресурсу twc_k8s_node_group:
# https://registry.terraform.io/providers/timeweb-cloud/timeweb-cloud/latest/docs/resources/k8s_node_group
# =============================================================================

# Получаем ID конфигуратора для master-узлов.
# Конфигуратор — это шаблон параметров узла (CPU, RAM, disk).
data "twc_configurator" "k8s_master" {
  location   = var.location
  preset_type = "premium"
}

# Создаём Kubernetes-кластер.
resource "twc_k8s_cluster" "main" {
  name        = "${var.project_name}-k8s"
  description = "Кластер для микросервиса ${var.project_name}"

  # Версия Kubernetes. Если указанная версия устарела,
  # Timeweb Cloud автоматически возьмёт последний patch.
  version = var.k8s_version

  # Сетевой драйвер: flannel — простой и достаточный для одного кластера.
  # Альтернатива: calico (поддерживает Network Policy, сложнее в настройке).
  network_driver = "flannel"

  # ingress = true — Timeweb Cloud автоматически установит NGINX Ingress Controller.
  ingress = true

  # high_availability = false — достаточно для dev/stage.
  # Для prod рекомендуется true (несколько master-узлов).
  high_availability = false

  # Привязываем кластер к приватной сети.
  network_id = twc_vpc.main.id

  # Конфигурация master-узла через конфигуратор.
  configuration {
    configurator_id = data.twc_configurator.k8s_master.id
    cpu             = var.k8s_master_cpu
    ram             = var.k8s_master_ram
    disk            = var.k8s_master_disk
  }
}

# Группа worker-узлов.
# Worker-узлы — это машины, на которых реально запускаются поды приложений.
resource "twc_k8s_node_group" "workers" {
  cluster_id = twc_k8s_cluster.main.id
  name       = "workers"

  # Используем тот же конфигуратор, что и для master.
  # В продакшене можно использовать другой preset (например, с большим RAM).
  configuration {
    configurator_id = data.twc_configurator.k8s_master.id
    cpu             = var.k8s_worker_cpu
    ram             = var.k8s_worker_ram
    disk            = var.k8s_worker_disk
  }

  # Начальное количество узлов.
  node_count = var.k8s_worker_count
}

# Kubeconfig — чувствительный output. Terraform сохраняет его в state.
# Используется для настройки kubectl на локальном ПК.
# ВНИМАНИЕ: не выводите kubeconfig в логи CI/CD!
output "kubeconfig" {
  value     = twc_k8s_cluster.main.kubeconfig
  sensitive = true
  description = "Kubeconfig для подключения к кластеру. Сохранить: terraform output -raw kubeconfig > ~/.kube/config"
}

output "k8s_cluster_id" {
  value       = twc_k8s_cluster.main.id
  description = "ID Kubernetes-кластера в Timeweb Cloud."
}

output "k8s_cluster_status" {
  value       = twc_k8s_cluster.main.status
  description = "Текущий статус кластера."
}
