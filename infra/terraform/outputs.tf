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
  reactive_demo_jdbc_url = "jdbc:postgresql://${twc_database_cluster.postgres.networks[0].ips[0].ip}:${twc_database_cluster.postgres.port}/reactive_demo"
  reactive_demo_r2dbc_url = "r2dbc:postgresql://${twc_database_cluster.postgres.networks[0].ips[0].ip}:${twc_database_cluster.postgres.port}/reactive_demo"

  # coalesce: try() в heredoc не обрабатывает null — apply падал на output "summary".
  # main_ipv4 часто null в ru-3 — берём floating IP (см. registry_server.tf).
  devtools_public_ip_display = coalesce(
    twc_server.devtools.main_ipv4,
    twc_floating_ip.devtools.ip,
    "ещё не создан",
  )
  s3_hostname_display        = coalesce(twc_s3_bucket.artifacts.hostname, "n/a")
  s3_full_name_display       = coalesce(twc_s3_bucket.artifacts.full_name, "n/a")
}

output "db_jdbc_url" {
  value       = local.db_jdbc_url
  description = "JDBC URL для подключения Spring Boot к PostgreSQL."
}

output "reactive_demo_jdbc_url" {
  value       = local.reactive_demo_jdbc_url
  description = "JDBC URL для reactive-demo (Flyway)."
}

output "reactive_demo_r2dbc_url" {
  value       = local.reactive_demo_r2dbc_url
  description = "R2DBC URL для reactive-demo (runtime)."
}

output "summary" {
  value = <<-EOT
    =========================================================
    Инфраструктура ${var.project_name} развёрнута.
    =========================================================

    Kubernetes cluster ID : ${twc_k8s_cluster.main.id}
    Kubernetes status     : ${twc_k8s_cluster.main.status}

    Devtools server IP    : ${local.devtools_public_ip_display}
    (Bitbucket + Registry)

    S3 hostname           : ${local.s3_hostname_display}
    S3 full name          : ${local.s3_full_name_display}

    Следующий шаг:
    1. terraform output -raw kubeconfig > ~/.kube/timeweb-greeting.yaml
    2. export KUBECONFIG=~/.kube/timeweb-greeting.yaml
    3. kubectl get nodes
    =========================================================
  EOT
  description = "Краткая сводка всех созданных ресурсов."
}
