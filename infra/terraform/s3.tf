# =============================================================================
# s3.tf — S3-хранилище для хранения артефактов сборки и Terraform state.
#
# S3 endpoint Timeweb Cloud: https://s3.timeweb.cloud
# Управление через веб-интерфейс: https://timeweb.cloud/my/storage
#
# Документация провайдера:
# https://registry.terraform.io/providers/timeweb-cloud/timeweb-cloud/latest/docs/resources/s3_bucket
# =============================================================================

data "twc_s3_preset" "artifacts_preset" {
  location = var.location

  # 10 ГБ хватит для хранения нескольких версий Docker layers и артефактов.
  disk = 10 * 1024

  price_filter {
    from = 0
    to   = 500
  }
}

resource "twc_s3_bucket" "artifacts" {
  name      = "${var.project_name}-artifacts"
  type      = "private"
  preset_id = data.twc_s3_preset.artifacts_preset.id
}

# Access key и secret key — чувствительные outputs.
# Используются для настройки Docker Registry (Distribution) с S3 backend
# и для сохранения Terraform state.
output "s3_access_key" {
  value     = twc_s3_bucket.artifacts.access_key
  sensitive = true
  description = "Access key для S3 bucket. Получить: terraform output -raw s3_access_key"
}

output "s3_secret_key" {
  value     = twc_s3_bucket.artifacts.secret_key
  sensitive = true
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
