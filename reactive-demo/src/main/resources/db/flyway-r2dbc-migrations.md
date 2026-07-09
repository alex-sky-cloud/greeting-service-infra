# Flyway + R2DBC: почему нужен отдельный JDBC URL

Модуль **reactive-demo** использует два способа доступа к PostgreSQL:

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
    url: jdbc:postgresql://localhost:5432/reactive_demo
```

Но в **Spring Boot 3/4**, если в приложении есть R2DBC (`ConnectionFactory`), срабатывает правило:

> при наличии `ConnectionFactory` автоконfiguration JDBC `DataSource` **отключается**.

Тогда Flyway не находит ни `DataSource`, ни JDBC URL и **не запускает миграции**.

### Симптомы

- приложение стартует «успешно», но таблиц в БД нет;
- в логах **нет** строк `org.flywaydb.core.FlywayExecutor`;
- кажется, что миграция «зависла» (на самом деле Flyway может ждать подключение, если URL задан неверно или БД недоступна);
- API падает с ошибками «relation … does not exist».

Проверка с `--debug`:

```
FlywayAutoConfiguration:
  @ConditionalOnProperty (spring.flyway.url) did not find property 'spring.flyway.url'
DataSourceAutoConfiguration:
  found beans of type ConnectionFactory → backed off
```

---

## Решение: явный `spring.flyway.url`

Flyway должен получить **собственный** JDBC URL — не через `spring.datasource.*`:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/reactive_demo
    username: app
    password: app
  flyway:
    enabled: true
    url: jdbc:postgresql://localhost:5432/reactive_demo   # обязательно jdbc:
    user: app
    password: app
    schemas: reactive_demo
    default-schema: reactive_demo
    create-schemas: true
    locations: classpath:db/migration
```

Конфигурация в проекте:

- `src/main/resources/application.yml` — env `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` для Flyway;
- `src/main/resources/application-local.yml` — локальный Docker Postgres;
- `src/test/resources/application-test.yml` — интеграционные тесты (Testcontainers подставляет порт через `@ServiceConnection`).

---

## Локальный запуск

```bash
# из корня репозитория
bash scripts/create-reactive-demo-db.sh

cd reactive-demo
./gradlew bootRun --args='--spring.profiles.active=local'
```

В логах должно появиться:

```
FlywayExecutor - Database: jdbc:postgresql://localhost:5432/reactive_demo
Successfully applied 4 migrations to schema "reactive_demo", now at version v3
```

---

## Удалённая БД / Kubernetes

Те же переменные, что и для основного приложения, но **два URL**:

| Переменная | Пример | Для чего |
|------------|--------|----------|
| `DB_URL` | `jdbc:postgresql://10.10.0.5:5432/reactive_demo` | Flyway (JDBC) |
| `R2DBC_URL` | `r2dbc:postgresql://10.10.0.5:5432/reactive_demo` | WebFlux runtime |
| `DB_USERNAME` | `greeting_user` | оба подключения |
| `DB_PASSWORD` | `…` | оба подключения |

Через SSH-туннель на ноутбуке порт обычно `15432`:

```bash
DB_URL=jdbc:postgresql://localhost:15432/reactive_demo \
R2DBC_URL=r2dbc:postgresql://localhost:15432/reactive_demo \
DB_USERNAME=greeting_user \
DB_PASSWORD=... \
./gradlew bootRun
```

---

## Схема и файлы миграций

| Файл | Содержание |
|------|------------|
| `V0__create_schema.sql` | схема `reactive_demo` |
| `V1__users_table.sql` | таблица `users` |
| `V2__orders_table.sql` | таблица `orders` |
| `V3__seed_data.sql` | тестовые данные |

DDL **только** здесь — не в Terraform и не в Helm.

Сброс локальных данных: `db/clean-database.sql`, затем перезапуск приложения (Flyway накатит миграции снова).

---

## Кратко

1. **R2DBC** — для runtime, **Flyway** — для миграций (JDBC).
2. **`spring.datasource.*` с R2DBC не подхватывается** — используйте **`spring.flyway.url`**.
3. URL для Flyway всегда с префиксом **`jdbc:`**, для R2DBC — **`r2dbc:`**.
4. Одна и та же БД `reactive_demo`, два протокола подключения.

См. также: [reactive-demo/README.md](../../../../README.md) (раздел «Flyway и миграции»).
