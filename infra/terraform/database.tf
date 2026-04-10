# =============================================================================
# database.tf — управляемая база данных PostgreSQL в Timeweb Cloud.
#
# Используем managed PostgreSQL (twc_database_cluster) — это удобнее,
# чем разворачивать PostgreSQL вручную на VPS, потому что:
# 1. Timeweb Cloud автоматически делает бэкапы.
# 2. Нет необходимости администрировать PostgreSQL вручную.
# 3. Автоматические обновления безопасности.
#
# Документация: https://registry.terraform.io/providers/timeweb-cloud/timeweb-cloud/latest/docs/resources/database_cluster
# =============================================================================

# Пресет с минимальной конфигурацией PostgreSQL для dev/stage.
# В prod нужно выбрать пресет с большим диском и replica.
data "twc_database_preset" "pg_preset" {
  location = var.location
  type     = "postgres"

  # 8 ГБ диска — минимум для старта.
  disk = 8 * 1024

  price_filter {
    from = 0
    to   = 2000
  }
}

resource "twc_database_cluster" "postgres" {
  name      = var.db_name
  type      = "postgres"
  preset_id = data.twc_database_preset.pg_preset.id

  # Размещаем БД в той же приватной сети, что и кластер K8S.
  # Это обеспечивает связь без публичного IP.
  network {
    id = twc_vpc.main.id
  }

  # is_external_ip = false — БД доступна только из VPC, не из интернета.
  # Раскомментируйте и установите true ТОЛЬКО для временной отладки.
  # is_external_ip = false
}

# Создаём отдельную базу данных внутри кластера.
resource "twc_database_instance" "app_db" {
  cluster_id = twc_database_cluster.postgres.id
  name       = "greeting_db"
}

# Создаём пользователя для приложения.
# НИКОГДА не используйте суперпользователя postgres в приложении.
resource "twc_database_user" "app_user" {
  cluster_id = twc_database_cluster.postgres.id
  name       = "greeting_user"
  password   = var.db_password
}

# Outputs — нужны для формирования строки подключения.
output "db_host" {
  value       = twc_database_cluster.postgres.networks[0].ips[0].ip
  description = "IP-адрес кластера PostgreSQL (внутри VPC)."
}

output "db_port" {
  value       = twc_database_cluster.postgres.port
  description = "Порт PostgreSQL."
}
