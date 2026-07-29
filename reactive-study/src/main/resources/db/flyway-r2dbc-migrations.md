# Flyway + R2DBC: почему нужен отдельный JDBC URL

Модуль **reactive-study** использует два способа доступа к PostgreSQL:

| Назначение | Стек | Когда |
|------------|------|--------|
| Миграции схемы (DDL, seed) | **Flyway + JDBC** | один раз при старте приложения |
| Запросы из WebFlux | **Spring Data R2DBC** | на каждый HTTP-запрос |

SQL-файлы миграций лежат в каталоге `db/migration/` (`V0`…`V3`).

---

## Проблема: `spring.datasource.*` не работает с R2DBC

Flyway — **JDBC**-инструмент. Он не умеет подключаться через `r2dbc:postgresql://...`.

Казалось бы, достаточно задать JDBC в конфиге:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5434/reactive_study
```

Но в **Spring Boot 3/4**, если в приложении есть R2DBC (`ConnectionFactory`), срабатывает правило:

> при наличии `ConnectionFactory` автокonfiguration JDBC `DataSource` **отключается**.

Тогда Flyway не находит ни `DataSource`, ни JDBC URL и **не запускает миграции**.

### Симптомы

- приложение стартует «успешно», но таблиц в БД нет;
- в логах **нет** строк `org.flywaydb.core.FlywayExecutor`;
- API падает с ошибками «relation … does not exist».

---

## Решение: явный `spring.flyway.url`

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5434/reactive_study
    username: app
    password: app
  flyway:
    enabled: true
    url: jdbc:postgresql://localhost:5434/reactive_study
    user: app
    password: app
    schemas: reactive_study
    default-schema: reactive_study
    create-schemas: true
    locations: classpath:db/migration
```

Конфигурация в проекте:

- `src/main/resources/application.yml` — env `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` для удалённого деплоя;
- `src/main/resources/application-local.yml` — локальный Docker Postgres;
- `src/test/resources/application-test.yml` — интеграционные тесты (Testcontainers).

---

## Локальный запуск

**1. PostgreSQL** — каталог `src/main/resources/docker-reactive-study/`:

```bash

cd reactive-study/src/main/resources/docker-reactive-study
cp .env.example .env
docker compose up -d
```

Или из корня репозитория: `bash scripts/create-reactive-study-db.sh`

**2. Приложение:**

```bash

cd reactive-study
./gradlew bootRun --args='--spring.profiles.active=local'
```

В логах должно появиться:

```
FlywayExecutor - Database: jdbc:postgresql://localhost:5434/reactive_study
Successfully applied 4 migrations to schema "reactive_study", now at version v3
```

---

## Удалённая БД / Kubernetes

| Переменная | Пример | Для чего |
|------------|--------|----------|
| `DB_URL` | `jdbc:postgresql://10.10.0.5:5432/reactive_study` | Flyway (JDBC) |
| `R2DBC_URL` | `r2dbc:postgresql://10.10.0.5:5432/reactive_study` | WebFlux runtime |
| `DB_USERNAME` | `greeting_user` | оба подключения |
| `DB_PASSWORD` | `…` | оба подключения |

Через SSH-туннель на ноутбуке порт обычно `15432`:

```bash

DB_URL=jdbc:postgresql://localhost:15432/reactive_study \
R2DBC_URL=r2dbc:postgresql://localhost:15432/reactive_study \
DB_USERNAME=greeting_user \
DB_PASSWORD=... \
./gradlew bootRun
```

---

## Схема и файлы миграций

| Файл | Содержание |
|------|------------|
| `V0__create_schema.sql` | схема `reactive_study` |
| `V1__users_table.sql` | таблица `users` |
| `V2__orders_table.sql` | таблица `orders` |
| `V3__seed_data.sql` | тестовые данные |

DDL **только** здесь — не в Terraform и не в Helm.

Сброс локальных данных: `db/clean-database.sql`, затем перезапуск приложения.

---

## Кратко

1. **R2DBC** — для runtime, **Flyway** — для миграций (JDBC).
2. **`spring.datasource.*` с R2DBC не подхватывается** — используйте **`spring.flyway.url`**.
3. URL для Flyway всегда с префиксом **`jdbc:`**, для R2DBC — **`r2dbc:`**.
4. Одна и та же БД `reactive_study`, два протокола подключения.

См. также: [reactive-study/README.md](../../../../README.md).
