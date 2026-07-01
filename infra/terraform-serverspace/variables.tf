variable "api_key" {
  description = "API key проекта Serverspace для аутентификации провайдера"
  type        = string
  sensitive   = true
}

variable "ssh_public_key" {
  description = "Публичный SSH-ключ для ресурса serverspace_ssh"
  type        = string
}

variable "location" {
  description = "Локация Serverspace для создаваемых серверов"
  type        = string
  default     = "am2"
}

variable "image_family" {
  description = "Образ ОС для serverspace_server"
  type        = string
  default     = "Ubuntu-20.04-X64"
}

variable "control_plane_name" {
  description = "Имя сервера control plane"
  type        = string
}

variable "control_plane_cpu" {
  description = "Количество vCPU для control plane"
  type        = number
}

variable "control_plane_ram" {
  description = "Объём RAM для control plane в МБ"
  type        = number
}

variable "control_plane_disk" {
  description = "Размер диска control plane в ГБ; в main.tf обычно преобразуется в boot_volume_size"
  type        = number
}

variable "apps_name" {
  description = "Имя сервера приложений"
  type        = string
}

variable "apps_cpu" {
  description = "Количество vCPU для сервера приложений"
  type        = number
}

variable "apps_ram" {
  description = "Объём RAM для сервера приложений в МБ"
  type        = number
}

variable "apps_disk" {
  description = "Размер диска сервера приложений в ГБ; в main.tf обычно преобразуется в boot_volume_size"
  type        = number
}

variable "postgres_name" {
  description = "Имя сервера PostgreSQL"
  type        = string
}

variable "postgres_cpu" {
  description = "Количество vCPU для сервера PostgreSQL"
  type        = number
}

variable "postgres_ram" {
  description = "Объём RAM для сервера PostgreSQL в МБ"
  type        = number
}

variable "postgres_disk" {
  description = "Размер диска сервера PostgreSQL в ГБ; в main.tf обычно преобразуется в boot_volume_size"
  type        = number
}

variable "gitlab_name" {
  description = "Имя сервера GitLab"
  type        = string
}

variable "gitlab_cpu" {
  description = "Количество vCPU для сервера GitLab"
  type        = number
}

variable "gitlab_ram" {
  description = "Объём RAM для сервера GitLab в МБ"
  type        = number
}

variable "gitlab_disk" {
  description = "Размер диска сервера GitLab в ГБ; в main.tf обычно преобразуется в boot_volume_size"
  type        = number
}
