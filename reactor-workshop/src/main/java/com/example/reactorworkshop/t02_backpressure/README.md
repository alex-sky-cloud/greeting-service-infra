# t02_backpressure (2.1)

## Цель

Показать `request(n)` / `limitRate` на потоке ~100 000 показаний.

## Два пути

- `GET /api/t02/readings-limit-rate` — `findAll().limitRate(...)`. В QUERY один SELECT без LIMIT.
- `GET /api/t02/readings-sql-page` — один `SELECT ... LIMIT 5 OFFSET 0`. Так пачка видна в SQL.

Рекурсию страниц, `collectList` + `flatMapMany` + `concatWith` убрали: это операторы следующих тем.

## Сброс схемы

Если уже накатывали users/orders:

```

DROP SCHEMA IF EXISTS reactor_workshop CASCADE;

```
