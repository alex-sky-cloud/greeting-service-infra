# docker-reactive-study

Локальный PostgreSQL **только для модуля reactive-study**.

| Параметр | Значение |
|----------|----------|
| Контейнер | `reactive-study-postgres` |
| База | `reactive_study` (создаётся при первом `compose up`) |
| Порт на хосте | **5434** (чтобы не конфликтовать с `local-postgres` на 5432) |
| Пользователь | `app` / `app` |

## Быстрый старт

```bash

cd reactive-study/src/main/resources/docker-reactive-study
cp .env.example .env
docker compose up -d
```

Проверка:

```bash

docker exec reactive-study-postgres psql -U app -d reactive_study -c "SELECT current_database();"
```

Дальше — запуск приложения с profile `local` (см. [`../README.md`](../README.md)).
