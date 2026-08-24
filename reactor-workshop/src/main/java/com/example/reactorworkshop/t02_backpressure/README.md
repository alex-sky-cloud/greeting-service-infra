# t02_backpressure (2.1)

## Цель

Таблица `readings` ≈ 100 000 строк (думайте как о миллионе). HTTP/WebFlux не может обработать их все сразу.
Drop не используем: продюсер должен замедляться. В лабе **два разных механизма** — их путают, потому что
в обоих фигурирует слово «limit».

## Сюжет 1 — JVM backpressure (`limitRate` / `request(n)` / prefetch)

Эндпоинт:

```

GET /api/t02/readings-limit-rate

```

Цепочка: `findAll().limitRate(50)`.

Reactive Streams: subscriber говорит `request(n)`. Prefetch Reactor часто 256. Здесь prefetch / high-tide = 50:
вниз уходят **все** строки, но demand режется на `request(50)`, потом ещё `request(50)`, пока таблица не кончится.

Это **не** «вернуть 50 строк и остановиться». HTTP-ответ стримит все ~100 000. Число 50 — пачка спроса, не SQL `LIMIT`.

Что делает PostgreSQL R2DBC-драйвер:

- один statement: `SELECT ... FROM readings` **без** `LIMIT` (это видно в pretty QUERY-логе);
- portal/cursor на сервере открыт; на каждый `request(50)` драйвер Fetch-ит следующие 50 из того же ResultSet;
- нового SQL `LIMIT 50 OFFSET x` нет;
- медленный клиент → `request(50)` позже → Postgres не заталкивает миллион строк в heap;
- cancel HTTP → `Flux` cancel → cursor закрыт, хвост не грузится.

Почему Time:~23s и Query без `LIMIT`: метод попросил у Postgres **всю** таблицу; `limitRate` только темп входа в JVM.

Ожидаемый размер HTTP: ~100 000 JSON-объектов (пока клиент не оборвал поток).

## Сюжет 2 — SQL-пагинация (`LIMIT` / `OFFSET`), не Reactive Streams

Эндпоинт:

```

GET /api/t02/readings-sql-page

```

Студент после сюжета 1 смотрит QUERY и не видит `LIMIT`. Чтобы пачка была **на стороне БД**, native SQL пишем сами:

```

SELECT id, meter_id, kwh, recorded_at
FROM reactor_workshop.readings
ORDER BY id
LIMIT :limit OFFSET :offset

```

По строкам:

- `ORDER BY id` — страницы стабильны; без `ORDER BY` `OFFSET` бессмысленен (любые 5 строк).
- `LIMIT 5` — Postgres вернёт не больше пяти строк и STOP. JVM не видит остаток 100k. Демо специально маленькое, лог короткий.
- `OFFSET 0` — сколько строк пропустить. Первая страница = ids 1..5 (если id с 1). `OFFSET 5 LIMIT 5` была бы вторая.
  Обход всех страниц — `collectList` / `flatMapMany` / `concatWith`, это следующие темы.

Зачем оба: `LIMIT` один — «первые N». `OFFSET` именует страницу (`pageIndex * pageSize`). Классическая offset-пагинация.
Минус: большой `OFFSET` дорогой (сервер всё равно проходит пропущенные строки). Оставляем, потому что этот SQL виден в логе
и это ответ на «как постраничить в SQL».

Ожидаемый QUERY (pretty, профиль `local`): один `SELECT` **с** `LIMIT 5 OFFSET 0`.

Ожидаемый HTTP: ровно **5** объектов, затем конец. Не 100k. Не prefetch.

## Контраст

| | `limitRate` | `LIMIT` / `OFFSET` |
|---|---|---|
| Где режется поток | JVM (`request` / prefetch) | Postgres (ResultSet) |
| SQL | без `LIMIT` | с `LIMIT` и `OFFSET` |
| Сколько строк в HTTP | все ~100k (пока не cancel) | только эта страница (5) |

## Синтетика: `pacedIds`

```

GET /api/t02/paced-ids?count=10&rate=3

```

`Flux.range` без БД. Десять id при rate 3 всё равно эмитят 1..10: `limitRate` не drop.

## Сброс схемы

Если уже накатывали users/orders:

```

DROP SCHEMA IF EXISTS reactor_workshop CASCADE;

```
