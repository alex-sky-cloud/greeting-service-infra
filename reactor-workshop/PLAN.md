# reactor-workshop — план работ

Живой документ. Сюда же вносятся корректировки пользователя.
Статусы: `todo` | `in_progress` | `done` | `blocked`

## Цель

Отдельный учебный модуль (не копия `reactive-study`) со **практическими заданиями** по файлам интервью.
Каждая тема интервью = один пакет внутри `src/main/java/com/example/reactorworkshop/`.
Внутри пакета темы: `controller/`, `domain/`, `repository/`, `service/`.
Примеры закрепляют операторы Reactor на реальном WebFlux + R2DBC стеке.

Рабочий цикл: **правка у себя → в чат только изменённые файлы** (не zip всего модуля). Пользователь кладёт их в локальный проект. Полный архив не отдаём, пока явно не попросят.

Очередь лаб после правки бэклога: первая практическая — `t02_backpressure` (файлы 2.1+2.2). Глава 10 (`t01_map_flatmap`) остаётся в репозитории до переименования в t10, новой темой не считается.

## Имя и изоляция от образца

| | Образец `reactive-study` | Этот модуль |
|---|---|---|
| Имя | reactive-study | **reactor-workshop** |
| Пакет | com.example.reactivestudy | **com.example.reactorworkshop** |
| HTTP | 8083 | **8084** |
| Docker Postgres | :5434 | **:5435** |
| БД / схема | reactive_study | **reactor_workshop** |
| Контейнер | reactive-study-postgres | **reactor-workshop-postgres** |
| JAR | reactive-study.jar | **reactor-workshop.jar** |

Пользователь / пароль локально: `app` / `app` (как в образце).

## Требования к оформлению (из образца Gradle)

- Сборка: **Gradle**, не Maven.
- Spring Boot **4.0.5**, `io.spring.dependency-management` **1.1.7**, **Java 21**, `mavenCentral()`.
- Стартеры: `webflux`, `data-r2dbc`, `flyway`, `actuator`, `validation`.
- Runtime: `postgresql`, `r2dbc-postgresql`, `flyway-database-postgresql`.
- Lombok: `compileOnly` + `annotationProcessor`.
- Тесты: `webflux-test`, `starter-test`, `boot-testcontainers`, testcontainers junit-jupiter/postgresql/r2dbc, `reactor-test`.
- `test { useJUnitPlatform() }`, `bootJar { archiveFileName }`.
- Комментарии на русском, технические имена на английском.

## Конфиг: local и VPS

- `application.yml` — env: `R2DBC_URL`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
- `application-local.yml` — профиль `local`, localhost:5435.
- Flyway **только JDBC** (`spring.flyway.url`). При R2DBC `spring.datasource.*` **не** создаёт DataSource.
- `schemas` / `default-schema` = `reactor_workshop`, `create-schemas: true`, `baseline-on-migrate: true`, `locations: classpath:db/migration`.
- Actuator: health, info, metrics; liveness/readiness.
- Один набор миграций и для Docker, и для managed Postgres (VPS).
- DDL только в Flyway, не в Terraform/Helm.
- Два clean-скрипта: docker и remote (`DROP SCHEMA … CASCADE`).
- Docker: `postgres:16.4`, `.env` из `.env.example`, volume свой, `initdb` → `/docker-entrypoint-initdb.d` (на пустой volume; сейчас `SELECT 1`), healthcheck `pg_isready`. Порт хоста не 5432.

Стиль SQL: `V0` схема; далее `set search_path`; PK `generated always as identity`; `timestamptz default now()`; FK + индекс; seed отдельно; объём через `generate_series`.

## Структура пакетов (как на скрине образца)

```
reactor-workshop/
  PLAN.md                 ← этот файл
  README.md
  build.gradle
  settings.gradle
  src/main/java/com/example/reactorworkshop/
    ReactorWorkshopApplication.java
    t02_backpressure/…   ← следующая лаба
    t10_map_flatmap/…   ← сейчас лежит как t01_map_flatmap, переименовать
  src/main/resources/
    application.yml
    application-local.yml
    db/migration/
    docker-reactor-workshop/
  src/test/java/…
```

Общие entity/repository для таблиц можно держать в теме, пока таблиц мало — дублировать не надо без нужды. Общий слой `shared` появится по корректировке, если пользователь попросит.

## Бэклог тем интервью

Источник порядка: папка `docs/.../reactive/` (скрин 2026-08-24). **Не** порядок вложений в чат.

Колонка «пакет»: `lab` = отдельный Java-пакет с controller/domain/repository/service;
`lab-group` = входит в общий пакет с соседними файлами той же главы;
`theory` = в код как HTTP-лаба не ставим (комментарий / README темы, без отдельного API).

Группировка глав — предложение, правки пользователя важнее.

| Файл | Пакет (предложение) | Тип | Статус |
|---|---|---|---|
| `1 - Как читать upstream и downstream в реальном коде.md` | комментарии во всех лабах | theory | todo |
| `2.1 - Что такое backpressure.md` | `t02_backpressure` | lab-group | **in_progress: пакет создан** |
| `2.2 - onBackpressureDrop vs Latest при открытии demand.md` | `t02_backpressure` | lab-group | вместе с 2.1 |
| `2.3 - Reactive Streams vs TCP flow control.md` | README внутри t02 | theory | todo |
| `2.4 - TCP Zero Window.md` | README внутри t02 | theory | todo |
| `3.1` Context vs ThreadLocal | `t03_context` | lab-group | todo |
| `3.2` Context, типичная ошибка | `t03_context` | lab-group | todo |
| `3.3` Context vs ScopedValue | README t03 | theory | todo |
| `4.1` Schedulers | `t04_schedulers` | lab-group | todo |
| `4.2` ParallelFlux vs publishOn(parallel) | `t04_schedulers` | lab-group | todo |
| `5.1` Hot / Cold | `t05_hotcold` | lab-group | todo |
| `5.2` connect vs autoConnect | `t05_hotcold` | lab-group | todo |
| `5.3` dispose после connect/autoConnect | `t05_hotcold` | lab-group | todo |
| `E - Hot/Cold примеры.md` | тот же `t05_hotcold` | lab-group | todo |
| `6` Observer vs Reactor | README модуля | theory | todo |
| `7` Event Loop, Selector, Reactor Netty | `t07_netty` или theory | см. вопрос | todo |
| `8` interest set Selector | вместе с 7/9 | theory/lab | todo |
| `9` Event Loop и Selector | вместе с 7/8 | theory/lab | todo |
| `10.1` flatMap vs concatMap vs map | `t10_map_flatmap` | lab-group | код уже есть как `t01_map_flatmap` — **переименовать, не первая тема** |
| `10.2` Mono.flatMap vs map | `t10_map_flatmap` | lab-group | частично сделано |
| `10.3` flatMap vs concatMap | `t10_map_flatmap` | lab-group | ещё нет concatMap |
| `10.4` concurrency, prefetch, overflow | `t10_map_flatmap` или отдельный | lab | todo |
| `11` zip vs merge vs concat | `t11_zip_merge_concat` | lab | todo |
| `12` concatMap / concat / then / flatMap | `t12_concat_then` | lab-group | todo |
| `14` concat, concatWith, concatMap глубокий разбор | `t12_concat_then` | lab-group | todo |
| `14.2` prefetch Flux.concat | `t12_concat_then` | lab-group | todo |
| `14.3` concatMap inner порядок | `t12_concat_then` | lab-group | todo |
| `21` thenMany and then | `t12_concat_then` | lab-group | todo |
| `B` когда zip, когда concatMap/then | `t12_concat_then` | lab-group | todo |
| `A` условные цепочки вместо then | `t12_concat_then` или `tA_conditionals` | lab | todo |
| `C` утилита безопасного then | позже, после A/12 | lab | todo |
| `D` пояснение concatWith | theory к t12 | theory | todo |
| `13` путь HTTP-запроса Netty → WebFlux | theory / `t13_http_path` | theory | todo |
| `20` две двери HTTP transport | вместе с 13 | theory | todo |
| `data_path_two_doors_netty` | вместе с 13/20 | theory | todo |
| `15` overhead map/flatMap | короткий lab или theory | ? | todo |
| `16` boundedElastic | `t04_schedulers` (тот же пакет, что 4.x) | lab-group | todo |
| `17` cache(), heap/stack | `t17_cache` | lab | todo |
| `18` checkpoint() | `t18_checkpoint` | lab | todo |
| `19.1` retryWhen backoff | `t19_errors_retry` | lab-group | todo |
| `19.2` ошибка inner в flatMap | `t19_errors_retry` | lab-group | todo |
| `19.3` retryWhen + onErrorResume котировки | `t19_errors_retry` | lab-group | todo |
| `22` backpressure + reactor-kafka | `t22_kafka` (нужен Kafka в зависимостях) | lab | todo, отдельно |
| `BLOCK-O-INIT-PATH-VERIFICATION.md` | не лаба | theory | skip пока |
| `reactor_timeouts_review.md` | timeouts lab или theory | ? | todo |

### Очередь практических пакетов (после согласования)

1. `t02_backpressure` ← 2.1 + 2.2 (теория 2.3/2.4 в README пакета)
2. `t03_context` ← 3.1 + 3.2
3. `t04_schedulers` ← 4.1 + 4.2 + 16 boundedElastic
4. `t05_hotcold` ← 5.1–5.3 + E
5. `t10_map_flatmap` ← 10.1–10.4 (текущий код переехать сюда)
6. `t11_zip_merge_concat`
7. `t12_concat_then` ← 12, 14, 14.2, 14.3, 21, B, D
8. дальше 15 / 17 / 18 / 19 / A / C / 22

Netty 7–9, 13, 20: по умолчанию **theory** (иначе это не операторы, а схема транспорта). Если нужно — один пакет `t07_netty` с неблокирующим vs blocking endpoint.

## Процесс

1. Обновить статус в таблице бэклога.
2. Править файлы у себя.
3. Собрать/проверить у себя при необходимости.
4. В чат отдать **только изменённые файлы** (пути как в модуле).
5. Корректировки пользователя сразу писать в этот PLAN.md.


## Оформление Java-лаб (сверяться перед каждой темой)

- Перед работой открыть **этот PLAN.md**.
- У каждого класса: JavaDoc — тема интервью, цель, что проверяем.
- JavaDoc на **каждый** метод, в том числе **private**.
- У каждого оператора Reactor (`map`, `flatMap`, `limitRate`, `collectList`, `take`, …) однострочный `//`: что делает **здесь**, какой профит. Не оставлять оператор «на потом» без пояснения — тема ещё не пройдена.
- У каждой локальной переменной, если имя само не объясняет роль — `//` зачем она.
- Между `if` / `return` / логическими шагами метода — пустая строка.
- Списки в JavaDoc — HTML `<ul><li>`. Примеры кода: `<pre>{@code ... }</pre>`.
- **ТРИГГЕР — никаких магических цифр нигде:** не только в `limitRate(50)`, но и `pagesLeft - 1`, `Math.max(x, 0)`, `Flux.range(1, n)`, сравнение с `1`. Любой литерал числа → именованная константа или локальная переменная (`int emptyCount = 0`, `int firstId = 1`). Исключение: `defaultValue` в аннотации, если константа уже объявлена рядом.
- Лаба темы N не использует операторы из темы N+1 без крайней нужды. Если без них никак — упростить пример, не городить рекурсию/`collectList`+`flatMapMany`+`concatWith`.
- README.md в пакете: цель, что проверяем, HTTP.

- Имена типов с префиксом `T01`/`T02`/… Zip в чат: только реально изменённые файлы/пакеты.
- Markdown: у каждого fenced-блока кода сразу после открывающих тройных кавычек — **пустая строка**, потом код, потом закрывающие кавычки. Без этой пустой строки сверху блока нельзя.


## Сущности R2DBC

- Всегда `@Table(value = "…", schema = "reactor_workshop")`. Иначе 42P01 (`public`).
- Аннотация поля — **строкой выше** имени. Между полями record — пустая строка.

## Именование в одном приложении

Префикс темы в **каждом** public-типе: `T01UserRepository`, `T02OrderRepository`, контроллеры, entity, DTO, сервисы.
Bean name Spring Data = имя интерфейса. `OrderRepository` во втором пакете даёт `BeanDefinitionOverrideException`.
`spring.main.allow-bean-definition-overriding=true` **запрещён**.

## Корректировки пользователя

- 2026-08-24: бэклог должен повторять **нумерацию файлов в папке reactive**, не порядок вложений в чат. Backpressure (глава 2) — среди первых практических. Часть файлов — общий материал без HTTP-лабы.
- 2026-08-24: пакет `t01_map_flatmap` сделан раньше времени (это глава 10). Каркас Gradle/Docker оставляем. Код t01 не считаем «первой темой»; переименуем в t10 после согласия.

## Сделано в этой итерации

- [x] PLAN.md
- [x] Каркас Gradle + YAML + Docker + Flyway V0–V3
- [x] Каркас; ошибочно начата глава 10 как t01
- [ ] Согласовать группировку lab-group
- [ ] Первая лаба: t02_backpressure


- 2026-08-24: в чат только дельты файлов, не весь проект zip.

- 2026-08-24: уникальные имена бинов (T01/T02); убрать app.version из YAML; overriding бинов не включать.

- 2026-08-24: JavaDoc на класс/метод, HTML-списки, pre+{@code} для примеров, README цели пакета, без magic numbers; zip только дельта.


## Домашние задания

После демо-лабы по теме: Markdown ТЗ в пакете (`TASK-2.1.md` и т.д.). Пользователь пишет **новые** классы, присылает отдельные `.java`. Я раскладываю в те же пакеты у себя, проверяю compile/тест, отвечаю по существу. Демо-классы лабы не переписываем под ДЗ.

- 2026-08-24: ТЗ 2.1 — новые T02OrderExport* файлы, limitRate, без drop.

- 2026-08-24: в Markdown code fence первая строка внутри блока — пустая.

- 2026-08-24: schema на @Table; оформление record; limitRate ≠ SQL LIMIT, для лога QUERY — LIMIT/OFFSET путь.


## Домен БД (не копия reactive-study)

Схема `reactor_workshop`: `meters` (250) + `readings` (100 000) через `generate_series`.
После смены миграций на уже живой БД: `DROP SCHEMA reactor_workshop CASCADE` и повторный старт.

- 2026-08-24: свои миграции meters/readings 100k; Java t01/t02 переведены с users/orders.

- 2026-08-24: JavaDoc и на private; пустые строки в методе; магия даже в `- 1` запрещена; t02 SQL-пагинацию упростить до одного LIMIT.

- 2026-08-24: pretty SQL log = `io.r2dbc:r2dbc-proxy` + `LocalR2dbcSqlLogConfig` (профиль `local`). `hibernate.format_sql` на R2DBC не действует — Hibernate нет.

