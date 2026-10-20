# reactive-study — лабораторные работы по Project Reactor

Учебный модуль **Spring Boot WebFlux + R2DBC** для практики по материалам каталога `docs/interview/reactive/`.

| | `app` | `reactive-demo` | `reactive-study` |
|---|-------|-----------------|------------------|
| Порт HTTP | 8080 | 8081 | **8083** |
| Локальный Postgres | `docker-greeting` :5432 | `docker-greeting` :5432 | **`docker-reactive-study` :5434** |
| База | `app` | `reactive_demo` | **`reactive_study`** |

---

## Локальный запуск

> Пошагово: [`src/main/resources/README.md`](src/main/resources/README.md)

**1. PostgreSQL модуля:**

```bash

cd reactive-study/src/main/resources/docker-reactive-study
cp .env.example .env
docker compose up -d
```

**2. Приложение:**

```bash

cd reactive-study
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun --no-daemon
```

Проверка: http://localhost:8083/actuator/health

**Лаборатория HTTP-пути:** `GET /api/orders/first-10` — см. [`docs/HTTP-REQUEST-DEBUG-BREAKPOINTS.md`](docs/HTTP-REQUEST-DEBUG-BREAKPOINTS.md).

**Лаборатория Spring Events:** `GET /api/demo/events/block/{address}` — см. [`docs/SPRING-EVENTS-DEBUG-BREAKPOINTS.md`](docs/SPRING-EVENTS-DEBUG-BREAKPOINTS.md).

---

## Курсы

| Курс | План | Практика |
|---|---|---|
| Project Reactor, 13 тем | [`docs/reactor-course/00-plan.md`](docs/reactor-course/00-plan.md) | `reactorcourse/tNN` в `src/main` и `src/test`; тесты на Postgres в Testcontainers, таблицы `rc_*` (миграция `V17`) |
| SQL | [`docs/sql-course/00-plan.md`](docs/sql-course/00-plan.md) | задачи в файлах тем |
| Java Concurrency | [`docs/java-concurrency-course/00-plan.md`](docs/java-concurrency-course/00-plan.md) | задачи в файлах тем |

Как готовить новый курс в том же стиле: [`docs/COURSE-AUTHORING-GUIDE.md`](docs/COURSE-AUTHORING-GUIDE.md).

Тесты одной темы курса Reactor (нужен запущенный Docker):

```bash

cd /mnt/d/Project_infra/greeting-service-infra/reactive-study
./gradlew test --tests "com.example.reactivestudy.reactorcourse.t01.*"
```

---

## Структура модуля

```
reactive-study/
  src/main/java/com/example/reactivestudy/
    ReactiveStudyApplication.java
    domain/
      model/          ← сущности (позже)
      dto/            ← DTO API (позже)
    service/          ← реактивные сервисы (позже)
    controller/       ← WebFlux REST (позже)
  src/main/resources/
    db/
      BUSINESS-CASE.md
      migration/      ← V0…V11
    docker-reactive-study/
```

---

## Удалённый деплой

См. [`src/main/resources/README.md`](src/main/resources/README.md) и [`db/flyway-r2dbc-migrations.md`](src/main/resources/db/flyway-r2dbc-migrations.md).
