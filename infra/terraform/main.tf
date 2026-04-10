# =============================================================================
# main.tf — точка входа Terraform-конфигурации для Timeweb Cloud.
# Создаёт: VPC, Kubernetes-кластер, управляемую БД PostgreSQL,
# S3-хранилище (для артефактов), DNS-запись.
# =============================================================================

terraform {
  required_providers {
    twc = {
      # Зеркало провайдера для работы из России.
      # Официальная документация: https://timeweb.cloud/docs/terraform/nachalo-raboty-s-terraform
      source  = "tf.timeweb.cloud/timeweb-cloud/timeweb-cloud"
      version = "~> 1.0"
    }
  }
  required_version = ">= 1.4.4"

  # Рекомендуется хранить state в S3-совместимом хранилище, а не локально.
  # Раскомментируйте и заполните после создания S3 bucket вручную или
  # через отдельный bootstrap Terraform workspace.
  #
  # backend "s3" {
  #   endpoint                    = "https://s3.timeweb.cloud"
  #   bucket                      = "my-tf-state"
  #   key                         = "greeting-service/terraform.tfstate"
  #   region                      = "ru-1"
  #   access_key                  = var.s3_access_key
  #   secret_key                  = var.s3_secret_key
  #   skip_credentials_validation = true
  #   skip_metadata_api_check     = true
  #   skip_region_validation      = true
  #   force_path_style            = true
  # }
}

# Провайдер читает токен из переменной окружения TWC_TOKEN.
# Никогда не вписывайте токен напрямую в .tf файл.
provider "twc" {
  token = var.twc_token
}
