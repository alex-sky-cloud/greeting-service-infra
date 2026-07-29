# reactive-study — подготовка перед запуском

Flyway накатывает миграции **автоматически при старте** `ReactiveStudyApplication`, но только если PostgreSQL уже поднят и база `reactive_study` доступна.

---

## Почему «ничего не создаётся»

| Симптом | Причина |
|---------|---------|
| `database "reactive_study" does not exist` | **Не запущен** `docker-compose` модуля или контейнер поднят без базы |
| `Connection refused` | Контейнер `reactive-study-postgres` **не запущен** |
| `Could not resolve placeholder 'R2DBC_URL'` | Запуск **без** profile `local` и без env-переменных |
| Старт OK, таблиц нет | Смотрите не тот порт/БД (нужны `localhost:5434` / `reactive_study`) |

**Важно:** `docker-compose` создаёт **базу** `reactive_study`. Flyway при старте приложения создаёт **схему, таблицы и seed** (`db/migration/V0`…`V3`).

---

## Шаг 1. Поднять PostgreSQL через docker-compose модуля

Каталог: **`docker-reactive-study/`** (рядом с этим README).

```bash

cd reactive-study/src/main/resources/docker-reactive-study
cp .env.example .env
docker compose up -d
```

Должен работать контейнер **`reactive-study-postgres`**, порт на хосте **5434**, база **`reactive_study`**, пользователь **`app`** / **`app`**.

Проверка:

```bash

docker ps --filter name=reactive-study-postgres
docker exec reactive-study-postgres psql -U app -d reactive_study -c "SELECT current_database();"
```

Из **корня репозитория** (обёртка над тем же compose):

```bash

bash scripts/create-reactive-study-db.sh
```

Windows:

```cmd

scripts\create-reactive-study-db.cmd
```

---

## Шаг 2. Запустить приложение (profile `local`)

`application-local.yml` подключается к `localhost:5434/reactive_study`.

### IDE (IntelliJ / Cursor)

- Main class: `com.example.reactivestudy.ReactiveStudyApplication`
- Working directory: `reactive-study`
- Profile / VM options: `--spring.profiles.active=local`

Или конфигурация: **ReactiveStudyApplication (local)** (файл `.run/ReactiveStudyApplication.run.xml`).

### Gradle

```bash

cd reactive-study
./gradlew bootRun --args='--spring.profiles.active=local'
```

---

## Шаг 3. Проверить, что миграции прошли

В логах:

```
FlywayExecutor - Database: jdbc:postgresql://localhost:5434/reactive_study
Successfully applied 4 migrations to schema "reactive_study", now at version v3
```

Таблицы:

```bash

docker exec reactive-study-postgres psql -U app -d reactive_study -c "\dt reactive_study.*"
```

Ожидаются: `users`, `orders`, seed (3 пользователя, 4 заказа).

Health: http://localhost:8083/actuator/health

---

## Удалённая БД (без profile `local`)

1. База `reactive_study` должна существовать на managed PostgreSQL.
2. Запуск с env (profile `local` **не** использовать):

```bash

DB_URL=jdbc:postgresql://host:5432/reactive_study \
R2DBC_URL=r2dbc:postgresql://host:5432/reactive_study \
DB_USERNAME=greeting_user \
DB_PASSWORD=... \
./gradlew bootRun
```

---

## Сброс данных

```bash

docker exec -i reactive-study-postgres psql -U app -d reactive_study -v ON_ERROR_STOP=1 \
  < reactive-study/src/main/resources/db/clean-database.sql
```

Перезапустите приложение — Flyway снова накатит `V0`…`V3`.

Полный сброс volume Postgres:

```bash

cd reactive-study/src/main/resources/docker-reactive-study
docker compose down -v
docker compose up -d
```

---

## Структура resources

```
src/main/resources/
  README.md                    ← этот файл
  application.yml
  application-local.yml
  docker-reactive-study/       ← локальный PostgreSQL
    docker-compose.yml
    .env.example
    initdb/
  db/
    BUSINESS-CASE.md           ← домен и сценарии Reactor
    migration/                 ← Flyway V0…V11
    clean-database.sql
    flyway-r2dbc-migrations.md
```

Подробнее про Flyway + R2DBC: [`db/flyway-r2dbc-migrations.md`](db/flyway-r2dbc-migrations.md).
