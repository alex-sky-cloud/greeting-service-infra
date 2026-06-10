# =============================================================================
# database.tf — управляемая база данных PostgreSQL в Timeweb Cloud.
#
# Используем managed PostgreSQL (twc_database_cluster):
# 1. Timeweb Cloud автоматически делает бэкапы.
# 2. Нет необходимости администрировать PostgreSQL вручную.
# 3. Автоматические обновления безопасности.
#
# Сгенерированный манифест от Timeweb показал:
# - type = "postgres18"
# - preset_id = 1139
# - availability_zone = "msk-1"
# - location = "ru-3" для VPC
# Поэтому приводим конфигурацию к этим значениям.
# =============================================================================

# preset_id 1139 — из сгенерированного манифеста Timeweb Cloud.
# availability_zone spb-1 — зона для локации ru-1 (Санкт-Петербург).
resource "twc_database_cluster" "postgres" {
  name = var.db_name
  type = "postgres18"

  preset_id         = 1139
  replications      = 1
  availability_zone = "msk-1"

  network {
    id = twc_vpc.main.id
  }
}

# Создаём отдельную базу данных внутри кластера.
# Если вам это нужно.
resource "twc_database_instance" "app_db" {
  cluster_id = twc_database_cluster.postgres.id
  name       = "greeting_db"
}

# Создаём пользователя для приложения.
# НИКОГДА не используйте суперпользователя postgres в приложении.
resource "twc_database_user" "app_user" {
  cluster_id = twc_database_cluster.postgres.id
  login      = "greeting_user"
  password   = var.db_password

  instance {
    instance_id = twc_database_instance.app_db.id

    # Нужные привилегии для работы приложения с таблицами.
    # Список подобран под PostgreSQL:
    # - базовые операции с данными (SELECT/INSERT/UPDATE/DELETE)
    # - создание/удаление таблиц (CREATE)
    # - REFERENCES — для внешних ключей
    # - TRUNCATE — для очистки таблиц
    privileges = [
      "CREATE",
      "INSERT",
      "UPDATE",
      "DELETE",
      "SELECT",
      "REFERENCES",
      "TRUNCATE",
    ]
  }
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