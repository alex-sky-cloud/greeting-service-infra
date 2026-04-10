# =============================================================================
# variables.tf — все входные параметры Terraform.
# Чувствительные переменные передаются через переменные окружения:
# export TF_VAR_twc_token="ваш-токен"
# export TF_VAR_db_password="пароль-базы"
# =============================================================================

variable "twc_token" {
  description = "API-токен Timeweb Cloud. Создать: https://timeweb.cloud/my/api-keys"
  type        = string
  sensitive   = true
}

variable "location" {
  description = "Зона доступности. Доступные значения: ru-1, ru-2, de-1, kz-1."
  type        = string
  default     = "ru-1"
}

variable "project_name" {
  description = "Имя проекта — используется как префикс для всех ресурсов."
  type        = string
  default     = "greeting-service"
}

variable "k8s_version" {
  description = "Версия Kubernetes. Проверяйте актуальные версии в панели Timeweb Cloud."
  type        = string
  default     = "v1.28.9"
}

variable "k8s_master_cpu" {
  description = "Количество CPU для master-узлов кластера."
  type        = number
  default     = 2
}

variable "k8s_master_ram" {
  description = "Объём RAM (МБ) для master-узлов. 4096 = 4 ГБ."
  type        = number
  default     = 4096
}

variable "k8s_master_disk" {
  description = "Размер диска (МБ) для master-узлов. 51200 = 50 ГБ."
  type        = number
  default     = 51200
}

variable "k8s_worker_cpu" {
  description = "Количество CPU для worker-узлов."
  type        = number
  default     = 2
}

variable "k8s_worker_ram" {
  description = "Объём RAM (МБ) для worker-узлов."
  type        = number
  default     = 4096
}

variable "k8s_worker_disk" {
  description = "Размер диска (МБ) для worker-узлов."
  type        = number
  default     = 51200
}

variable "k8s_worker_count" {
  description = "Количество worker-узлов."
  type        = number
  default     = 2
}

variable "db_name" {
  description = "Имя кластера управляемой СУБД PostgreSQL."
  type        = string
  default     = "greeting-db"
}

variable "db_password" {
  description = "Пароль пользователя базы данных. Передавать только через TF_VAR_db_password."
  type        = string
  sensitive   = true
}

variable "vpc_subnet" {
  description = "CIDR приватной сети для изоляции ресурсов."
  type        = string
  default     = "10.10.0.0/24"
}

variable "s3_access_key" {
  description = "Access key для S3-хранилища (используется для backend state)."
  type        = string
  sensitive   = true
  default     = ""
}

variable "s3_secret_key" {
  description = "Secret key для S3-хранилища."
  type        = string
  sensitive   = true
  default     = ""
}
