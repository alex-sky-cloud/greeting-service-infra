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
#
# ── Права пользователя и миграции Flyway ───────────────────────────────────
#
# При старте приложение (Spring Boot + Flyway) накатывает DDL в greeting_db:
# CREATE SCHEMA (V0), таблицы, индексы, процедуры в схемах iso_demo / shop_demo.
# Без прав CREATE / DROP / ALTER / INDEX миграции падают ещё до запуска HTTP.
#
# В провайдере Timeweb (twc_database_user) права задаются в одном из двух мест —
# одновременно использовать оба нельзя (terraform plan: "only one of instance,
# privileges can be specified"):
#
#   1) cluster-level privileges = [...]
#      Права на ВЕСЬ кластер PostgreSQL: все базы (instances) внутри кластера.
#      Подходит, если одному login нужен одинаковый набор прав везде.
#
#   2) instance { instance_id = ...; privileges = [...] }
#      Права только на ОДНУ базу (twc_database_instance), здесь — greeting_db.
#      Принцип наименьших привилегий: greeting_user не трогает другие БД кластера.
#
# Мы используем вариант (2): пользователь приложения работает только с greeting_db.
# Расширение списка (DROP, ALTER, INDEX) относительно исходного CREATE/SELECT/… —
# чтобы Flyway мог менять уже созданные объекты, а не только INSERT/SELECT.
#
# Примечание: для Flyway create-schemas нужен CREATE на базу greeting_db.
# Проверка: scripts/dev-db-connection/09-check-db-user-privileges-wsl.sh
resource "twc_database_user" "app_user" {
  cluster_id = twc_database_cluster.postgres.id
  login      = "greeting_user"
  password   = var.db_password

  instance {
    instance_id = twc_database_instance.app_db.id

    # Привилегии PostgreSQL на базу greeting_db (см. блок комментариев выше).
    # SELECT/INSERT/UPDATE/DELETE — данные; REFERENCES — FK; TRUNCATE — seed-процедуры.
    privileges = [
      "CREATE",
      "DROP",
      "ALTER",
      "INSERT",
      "UPDATE",
      "DELETE",
      "SELECT",
      "REFERENCES",
      "TRUNCATE",
      "INDEX",
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