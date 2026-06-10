# =============================================================================
# s3.tf — S3-хранилище для хранения артефактов сборки и Terraform state.
#
# S3 endpoint Timeweb Cloud: https://s3.timeweb.cloud
# Управление через веб-интерфейс: https://timeweb.cloud/my/storage
#
# Документация провайдера:
# https://registry.terraform.io/providers/timeweb-cloud/timeweb-cloud/latest/docs/resources/s3_bucket
# =============================================================================

# ВАЖНО:
# data "twc_s3_preset" в ru-3 не находит пресеты ("no Presets with provided properties found").
# Поэтому preset_id берём из сгенерированного манифеста Timeweb Cloud (минимальный тариф).

resource "twc_s3_bucket" "artifacts" {
  # Имя bucket. Должно быть глобально уникальным в Object Storage Timeweb.
  # Суффикс -dev — чтобы не пересекаться с уже существующим bucket.
  name = "${var.project_name}-artifacts-dev"

  type       = "private" # для артефактов сборки, не public
  preset_id  = 2667      # из манифеста Timeweb Cloud (минимальный тариф)
  project_id = 701321    # тот же project_id, что и у twc_database_cluster
}

# Access key и secret key — чувствительные outputs.
# Используются для настройки Docker Registry (Distribution) с S3 backend
# и для сохранения Terraform state.
output "s3_access_key" {
  value       = twc_s3_bucket.artifacts.access_key
  sensitive   = true
  description = "Access key для S3 bucket. Получить: terraform output -raw s3_access_key"
}

output "s3_secret_key" {
  value       = twc_s3_bucket.artifacts.secret_key
  sensitive   = true
  description = "Secret key для S3 bucket."
}

output "s3_hostname" {
  value       = twc_s3_bucket.artifacts.hostname
  description = "Hostname S3 bucket для подключения клиентов."
}

output "s3_full_name" {
  value       = twc_s3_bucket.artifacts.full_name
  description = "Полное имя bucket (с автоматическим префиксом Timeweb Cloud)."
}
