# =============================================================================
# kubernetes.tf — Managed K8S (presets API: /api/v1/presets/k8s)
# =============================================================================

data "twc_k8s_preset" "master" {
  location = var.location
  type     = "master"
  cpu      = var.k8s_master_cpu
  ram      = var.k8s_master_ram
}

data "twc_k8s_preset" "worker" {
  location = var.location
  type     = "worker"
  cpu      = var.k8s_worker_cpu
  ram      = var.k8s_worker_ram
}

resource "twc_k8s_cluster" "main" {
  name              = "${var.project_name}-k8s"
  description       = "Кластер для микросервиса ${var.project_name}"
  version           = var.k8s_version
  network_driver    = "flannel"
  ingress           = true
  high_availability = false
  network_id        = twc_vpc.main.id
  preset_id         = data.twc_k8s_preset.master.id
}

resource "twc_k8s_node_group" "workers" {
  cluster_id = twc_k8s_cluster.main.id
  name       = "workers"
  preset_id  = data.twc_k8s_preset.worker.id
  node_count = var.k8s_worker_count
}

output "kubeconfig" {
  value       = twc_k8s_cluster.main.kubeconfig
  sensitive   = true
  description = "Kubeconfig для подключения к кластеру."
}

output "k8s_cluster_id" {
  value       = twc_k8s_cluster.main.id
  description = "ID Kubernetes-кластера в Timeweb Cloud."
}

output "k8s_cluster_status" {
  value       = twc_k8s_cluster.main.status
  description = "Текущий статус кластера."
}
