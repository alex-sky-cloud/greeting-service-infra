# =============================================================================
# outputs.tf — сводный файл выходных значений всей инфраструктуры.
# =============================================================================

# Инструкция для получения kubeconfig после terraform apply:
# terraform output -raw kubeconfig > ~/.kube/timeweb-greeting.yaml
# export KUBECONFIG=~/.kube/timeweb-greeting.yaml
# kubectl get nodes

locals {
  # Строка подключения к PostgreSQL для использования в K8S Secret.
  # Используем внутренний IP из VPC — снаружи БД не доступна.
  db_jdbc_url = "jdbc:postgresql://${twc_database_cluster.postgres.networks[0].ips[0].ip}:${twc_database_cluster.postgres.port}/greeting_db"
}

output "db_jdbc_url" {
  value       = local.db_jdbc_url
  description = "JDBC URL для подключения Spring Boot к PostgreSQL."
}

output "summary" {
  value = <<-EOT
    =========================================================
    Инфраструктура ${var.project_name} развёрнута.
    =========================================================

    Kubernetes cluster ID : ${twc_k8s_cluster.main.id}
    Kubernetes status     : ${twc_k8s_cluster.main.status}

    Devtools server IP    : ${twc_server.devtools.main_ip}
    (Bitbucket + Registry)

    S3 hostname           : ${twc_s3_bucket.artifacts.hostname}
    S3 full name          : ${twc_s3_bucket.artifacts.full_name}

    Следующий шаг:
    1. terraform output -raw kubeconfig > ~/.kube/timeweb-greeting.yaml
    2. export KUBECONFIG=~/.kube/timeweb-greeting.yaml
    3. kubectl get nodes
    =========================================================
  EOT
  description = "Краткая сводка всех созданных ресурсов."
}
