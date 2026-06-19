# Локальная база `reactive_demo` — инструкция

Как создать базу **`reactive_demo`** в **локальном** Docker Postgres (`local-postgres`) для модуля `reactive-demo`.

> Удалённый сервер (K8s, managed PostgreSQL) — см. `reactive-demo/README.md`, раздел «Деплой на удалённый сервер».

---

## Почему `create-reactive-demo-db.sh: command not found`

Ошибка появляется, если:

1. Вызывают только имя файла **без** `bash` — shell ищет команду в `PATH`, а не `.sh` в текущей папке.
2. Запускают **не из корня** репозитория — путь `scripts/...` не находится.

**Правильно** — из корня репозитория:

```bash
cd /d/Project_infra/greeting-service-infra
bash scripts/create-reactive-demo-db.sh
```

---

## Локальная Postgres — по шагам

### Шаг 1. Поднять Docker Postgres (если ещё не запущен)

```bash
cd app/src/main/resources/docker-greeting
docker compose up -d
```

- Контейнер: **`local-postgres`**
- Порт: **5432**
- База по умолчанию для `app`: **`app`**

Сервис **`reactive-demo-db-init`** в `docker-compose.yml` при `docker compose up` тоже создаёт `reactive_demo`, если её ещё нет.

### Шаг 2. Создать базу `reactive_demo`

**Git Bash** (из **корня** репозитория):

```bash
cd /d/Project_infra/greeting-service-infra
bash scripts/create-reactive-demo-db.sh
```

**Windows (cmd / PowerShell):**

```cmd
cd D:\Project_infra\greeting-service-infra
scripts\create-reactive-demo-db.cmd
```

**Вручную, без скрипта:**

```bash
docker exec local-postgres psql -U app -d app -c "CREATE DATABASE reactive_demo OWNER app;"
```

### Шаг 3. Проверка

```bash
docker exec local-postgres psql -U app -d reactive_demo -c "\dt reactive_demo.*"
```

До первого запуска `reactive-demo` таблиц может не быть — Flyway создаст их при старте приложения.

### Шаг 4. Запуск reactive-demo

```bash
cd reactive-demo
```

**Git Bash:**

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun --no-daemon
```

**PowerShell:**

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
.\gradlew.bat bootRun
```

http://localhost:8081

### Шаг 5. Проверка API

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8081/api/users/1/summary
curl "http://localhost:8081/api/demo/reactor/compare?ids=1,2"
```

---

## Какой скрипт для чего

| Скрипт | Где запускать | Назначение |
|--------|---------------|------------|
| `scripts/create-reactive-demo-db.sh` | Корень репо, Git Bash: `bash scripts/...` | **Локальный** Docker `local-postgres` |
| `scripts/create-reactive-demo-db.cmd` | Корень репо, cmd/PowerShell | То же, Windows |
| `docker compose up -d` в `app/.../docker-greeting/` | Локально | Postgres + init `reactive_demo` |
| `scripts/dev-db-connection/10-create-reactive-demo-db.sh` | Git Bash + SSH-туннель | **Удалённая** managed Postgres |
| `scripts/create-secrets.sh` | После `terraform apply` | Kubernetes Secret, **не** локальная БД |

---

## Типичные ошибки

| Ошибка | Причина | Решение |
|--------|---------|---------|
| `create-reactive-demo-db.sh: command not found` | Вызов без `bash` или не из корня | `bash scripts/create-reactive-demo-db.sh` |
| `[ERROR] Контейнер local-postgres не запущен` | Docker не поднят | `docker compose up -d` в `docker-greeting/` |
| `database "reactive_demo" does not exist` | База не создана | Шаг 2 этой инструкции |
| `Could not resolve placeholder 'R2DBC_URL'` | Запуск без profile `local` и без env | `SPRING_PROFILES_ACTIVE=local` или задайте env |

---

## Сброс локальных данных

```bash
docker exec -i local-postgres psql -U app -d reactive_demo -v ON_ERROR_STOP=1 \
  < reactive-demo/src/main/resources/db/clean-database.sql
```

Перезапустите `reactive-demo` — Flyway применит миграции заново.

---

## Связанные файлы

| Путь | Описание |
|------|----------|
| `app/src/main/resources/docker-greeting/docker-compose.yml` | локальный Postgres |
| `app/src/main/resources/docker-greeting/initdb/002-create-reactive-demo-database.sql` | init при **новом** volume |
| `reactive-demo/README.md` | полная инструкция по модулю |
| `reactive-demo/src/main/resources/application-local.yml` | localhost:5432/reactive_demo |
