# Тема 12 — аналитика по логу событий

У заказа в таблице `orders` есть поле `status`. Оно показывает только, где заказ находится **сейчас**. Чтобы ответить, когда заказ ушёл в сборку, сколько он пролежал на складе, из какого шага его отменили и что было с заказами на прошлой неделе, нужна **история**. Её хранит журнал событий `order_status_events`: каждая смена статуса — отдельная строка.

В этой теме разбирается, как из такого журнала собрать путь заказа, текущий статус и статус на прошлую дату, воронку, время в каждом статусе, нарушения сроков. Отдельно — как проверить, что журнал не сломан.

Все числа ниже получены из учебной базы `reactive_study`. Часовой пояс сессии базы — `UTC`. Из прошлых тем понадобятся `FILTER` (тема 07), `NOT EXISTS` (тема 06), `row_number`, `lag` и `lead` (тема 09), интервалы (тема 10).

## Оглавление

- [Что лежит в журнале событий](#что-лежит-в-журнале-событий)
- [Путь заказа одной строкой: string_agg](#путь-заказа-одной-строкой-string_agg)
- [Какие пути встречаются и как часто](#какие-пути-встречаются-и-как-часто)
- [Из журнала в одну строку на заказ](#из-журнала-в-одну-строку-на-заказ)
- [Последнее событие заказа: row_number и DISTINCT ON](#последнее-событие-заказа-row_number-и-distinct-on)
- [Сверка журнала с текущим статусом](#сверка-журнала-с-текущим-статусом)
- [Статус заказа на прошлую дату](#статус-заказа-на-прошлую-дату)
- [Воронка заказов](#воронка-заказов)
- [Сколько заказ провёл в каждом статусе: lead](#сколько-заказ-провёл-в-каждом-статусе-lead)
- [Нарушение срока передачи в доставку: lag](#нарушение-срока-передачи-в-доставку-lag)
- [Из какого статуса отменяют заказы](#из-какого-статуса-отменяют-заказы)
- [Проверка журнала: недопустимые переходы](#проверка-журнала-недопустимые-переходы)
- [Проверка журнала: разрывы в цепочке](#проверка-журнала-разрывы-в-цепочке)
- [События по дням](#события-по-дням)
- [Что обычно спрашивают на собеседованиях](#что-обычно-спрашивают-на-собеседованиях)
- [Карта выбора конструкции](#карта-выбора-конструкции)
- [Что попробовать самостоятельно](#что-попробовать-самостоятельно)

---

## Что лежит в журнале событий

Таблица `order_status_events`:

| Столбец | Что хранит |
|---|---|
| `id` | номер события; растёт по порядку записи |
| `order_id` | номер заказа из `orders` |
| `old_status` | статус до события; у первого события заказа — `NULL`, потому что до создания статуса не было |
| `new_status` | статус после события |
| `occurred_at` | момент события, `timestamptz` |

Статусы и их смысл:

| Статус | Что значит для клиента |
|---|---|
| `pending` | заказ создан и ждёт обработки |
| `processing` | заказ собирают на складе |
| `shipped` | заказ передан в доставку |
| `delivered` | заказ доставлен |
| `cancelled` | заказ отменён |

Допустимые пути заказа: создан → в сборке → передан в доставку → доставлен. Отменить можно только заказ, который ещё не передан в доставку: из `pending` или из `processing`.

В журнале 249966 событий по 100007 заказам. Первое событие каждого заказа — `NULL → pending`, его момент совпадает с `orders.created_at`.

**Задача.** Поддержка разбирает обращения по трём заказам и хочет видеть их историю целиком.

```sql

SELECT id,
       order_id,
       old_status,
       new_status,
       occurred_at
FROM order_status_events
WHERE order_id IN (2, 9, 10)
ORDER BY order_id, occurred_at;
```

**Как читается запрос**

- `FROM order_status_events WHERE order_id IN (2, 9, 10)` — все события трёх заказов.
- `ORDER BY order_id, occurred_at` — сначала по заказу, внутри заказа — по времени. Так история каждого заказа читается сверху вниз.

| id | order_id | old_status | new_status | occurred_at |
|---:|---:|---|---|---|
| 100004 | 2 | NULL | pending | 2025-11-24 00:07:56.927714+00 |
| 100018 | 9 | NULL | pending | 2026-06-02 15:50:17.791221+00 |
| 100019 | 9 | pending | processing | 2026-06-04 03:36:17.791221+00 |
| 100020 | 9 | processing | cancelled | 2026-06-04 18:29:17.791221+00 |
| 100021 | 10 | NULL | pending | 2026-06-01 17:09:10.808572+00 |
| 100022 | 10 | pending | processing | 2026-06-03 02:41:10.808572+00 |
| 100023 | 10 | processing | shipped | 2026-06-03 22:28:10.808572+00 |
| 100024 | 10 | shipped | delivered | 2026-06-06 17:28:10.808572+00 |

Как это читать:

- Заказ 2 создан и с тех пор не двигался: одно событие.
- Заказ 9 создан, ушёл в сборку и был отменён прямо со сборки.
- Заказ 10 прошёл весь путь до доставки: четыре события.

**Нюансы**

- Журнал только дописывается: старые строки не меняются. Поэтому по нему можно восстановить состояние на любой момент в прошлом. Поле `orders.status` такого не умеет: оно перезаписывается.
- Порядок событий внутри заказа задаёт `occurred_at`. Если у двух событий одного заказа одинаковое время, порядок между ними решает `id` — он растёт по порядку записи. Поэтому в запросах ниже сортировка внутри заказа везде `occurred_at, id`.

---

## Путь заказа одной строкой: string_agg

Читать историю по строкам неудобно, когда заказов много. Удобнее склеить статусы одного заказа в одну строку: `pending -> processing -> shipped`.

### Синтаксис

```sql

string_agg(value, delimiter ORDER BY sort_expression)
```

| Часть | Что означает |
|---|---|
| `string_agg` | агрегатная функция: склеивает значения строк одной группы в одну строку |
| `value` | что склеивать, например `new_status` |
| `delimiter` | что вставить между значениями, например `' -> '` |
| `ORDER BY sort_expression` | в каком порядке склеивать; пишется **внутри скобок** функции, без запятой перед `ORDER BY` |

Как любой агрегат, `string_agg` работает вместе с `GROUP BY`: одна группа — одна склеенная строка. Значения `NULL` функция пропускает.

**Источник:** https://www.postgresql.org/docs/16/functions-aggregate.html

**EN:**

> Concatenates the non-null input values into a string. Each value after the first is preceded by the corresponding delimiter (if it's not null).

**RU:**

> Склеивает входные значения, кроме NULL, в одну строку. Перед каждым значением, кроме первого, ставится соответствующий разделитель (если он не NULL).

Без `ORDER BY` внутри скобок порядок склейки не гарантирован.

**Источник:** https://www.postgresql.org/docs/16/sql-expressions.html

**EN:**

> Ordinarily, the input rows are fed to the aggregate function in an unspecified order. In many cases this does not matter; for example, `min` produces the same result no matter what order it receives the inputs in. However, some aggregate functions (such as `array_agg` and `string_agg`) produce results that depend on the ordering of the input rows. When using such an aggregate, the optional `order_by_clause` can be used to specify the desired ordering.

**RU:**

> Обычно строки подаются в агрегатную функцию в неопределённом порядке. Часто это неважно: например, `min` даёт один и тот же результат при любом порядке входа. Но у некоторых агрегатных функций (таких как `array_agg` и `string_agg`) результат зависит от порядка входных строк. Для такого агрегата можно указать необязательное `order_by_clause`, чтобы задать нужный порядок.

**Задача.** Поддержке нужен короткий вид истории: по каждому из заказов 1, 2, 9, 10 — путь одной строкой.

```sql

SELECT order_id,
       string_agg(new_status, ' -> ' ORDER BY occurred_at, id) AS path
FROM order_status_events
WHERE order_id IN (1, 2, 9, 10)
GROUP BY order_id
ORDER BY order_id;
```

**Как читается запрос**

- `WHERE order_id IN (1, 2, 9, 10)` — события четырёх заказов: 3 + 1 + 3 + 4 = 11 строк.
- `GROUP BY order_id` — четыре группы, по одной на заказ.
- `string_agg(new_status, ' -> ' ORDER BY occurred_at, id)` — внутри группы статусы выстраиваются по времени события и склеиваются через ` -> `.

Что попадает в группу заказа 10 и что из неё получается:

| occurred_at | new_status |
|---|---|
| 2026-06-01 17:09:10 | pending |
| 2026-06-03 02:41:10 | processing |
| 2026-06-03 22:28:10 | shipped |
| 2026-06-06 17:28:10 | delivered |

↓ `string_agg` ↓

`pending -> processing -> shipped -> delivered`

Результат:

| order_id | path |
|---:|---|
| 1 | pending -> processing -> shipped |
| 2 | pending |
| 9 | pending -> processing -> cancelled |
| 10 | pending -> processing -> shipped -> delivered |

**Нюансы**

- Склеивается `new_status`, а не `old_status`. Если склеить `old_status`, у заказа 10 получится `pending -> processing -> shipped`: `NULL` первого события пропущен, а `delivered` не встречается в `old_status` ни в одной строке. Последний статус потеряется.

---

## Какие пути встречаются и как часто

**Задача.** Продакт-менеджер хочет знать, какими путями реально ходят заказы и сколько заказов на каждом пути. Учитываются все заказы.

```sql

WITH order_path AS (
    SELECT order_id,
           string_agg(new_status, ' -> ' ORDER BY occurred_at, id) AS path
    FROM order_status_events
    GROUP BY order_id
)
SELECT path,
       count(*) AS orders
FROM order_path
GROUP BY path
ORDER BY orders DESC, path;
```

**Как читается запрос**

- `order_path` — первый шаг: путь каждого заказа одной строкой, как в прошлом разделе, но для всех заказов. В нём 100007 строк: одна на заказ.
- Второй шаг группирует уже **пути**: заказы с одинаковой строкой `path` попадают в одну группу, `count(*)` считает их.

Первые строки шага `order_path`:

| order_id | path |
|---:|---|
| 1 | pending -> processing -> shipped |
| 2 | pending |
| … | … |
| 9 | pending -> processing -> cancelled |
| 10 | pending -> processing -> shipped -> delivered |

Результат:

| path | orders |
|---|---:|
| pending -> processing | 20081 |
| pending -> processing -> shipped -> delivered | 20076 |
| pending | 19946 |
| pending -> processing -> shipped | 19833 |
| pending -> cancelled | 10158 |
| pending -> processing -> cancelled | 9913 |

Сумма по всем путям — 100007: каждый заказ попал ровно в один путь.

**Нюансы**

- Так ищут неожиданные пути. Если бы появился путь вроде `pending -> shipped`, это значило бы, что склад пропустил сборку или журнал записан с ошибкой.

---

## Из журнала в одну строку на заказ

Журнал «узкий»: одно событие — одна строка. Для отчётов часто нужен «широкий» вид: одна строка на заказ, а в столбцах — моменты каждого шага. Это делается агрегатом с `FILTER` из темы 07: для каждого столбца берётся момент только того события, которое нужно.

**Задача.** Аналитику нужна таблица по заказам 1, 2, 9, 10: когда заказ создан, отправлен в сборку, передан в доставку, доставлен, отменён. Время — с точностью до минуты.

```sql

SELECT order_id,
       date_trunc('minute', max(occurred_at) FILTER (WHERE new_status = 'pending')) AS created_at,
       date_trunc('minute', max(occurred_at) FILTER (WHERE new_status = 'processing')) AS processing_at,
       date_trunc('minute', max(occurred_at) FILTER (WHERE new_status = 'shipped')) AS shipped_at,
       date_trunc('minute', max(occurred_at) FILTER (WHERE new_status = 'delivered')) AS delivered_at,
       date_trunc('minute', max(occurred_at) FILTER (WHERE new_status = 'cancelled')) AS cancelled_at
FROM order_status_events
WHERE order_id IN (1, 2, 9, 10)
GROUP BY order_id
ORDER BY order_id;
```

**Как читается запрос**

- `GROUP BY order_id` — одна строка результата на заказ.
- `max(occurred_at) FILTER (WHERE new_status = 'processing')` — из событий заказа берутся только события со статусом `processing`, и из них — момент. Такое событие у заказа одно, поэтому `max` просто достаёт его время. Если такого события нет, получается `NULL`.
- `date_trunc('minute', …)` — отбрасывает секунды, чтобы таблица читалась.

| order_id | created_at | processing_at | shipped_at | delivered_at | cancelled_at |
|---:|---|---|---|---|---|
| 1 | 2026-04-05 18:53:00+00 | 2026-04-06 22:43:00+00 | 2026-04-07 13:56:00+00 | NULL | NULL |
| 2 | 2025-11-24 00:07:00+00 | NULL | NULL | NULL | NULL |
| 9 | 2026-06-02 15:50:00+00 | 2026-06-04 03:36:00+00 | NULL | NULL | 2026-06-04 18:29:00+00 |
| 10 | 2026-06-01 17:09:00+00 | 2026-06-03 02:41:00+00 | 2026-06-03 22:28:00+00 | 2026-06-06 17:28:00+00 | NULL |

**Нюансы**

- `NULL` в столбце означает «этого шага у заказа не было». Например, заказ 9 не передавался в доставку.
- Из широкой строки удобно считать сроки: `delivered_at - created_at` — сколько заказ шёл от создания до доставки.
- `max` здесь выбран потому, что агрегат нужен для `GROUP BY`, а событие каждого статуса у заказа одно. Если бы заказ мог возвращаться в статус повторно, пришлось бы решать, какой из моментов нужен: первый (`min`) или последний (`max`).

---

## Последнее событие заказа: row_number и DISTINCT ON

Самый частый вопрос к журналу: «какое событие у каждого заказа последнее». Последнее событие = текущий статус.

### Способ 1: row_number из темы 09

Пронумеровать события внутри каждого заказа от нового к старому и оставить номер 1.

**Задача.** Поддержка хочет видеть текущий статус заказов 1, 2, 9, 10, вычисленный по журналу. Сначала посмотрим нумерацию целиком.

```sql

WITH ranked AS (
    SELECT order_id,
           new_status,
           occurred_at,
           row_number() OVER (
               PARTITION BY order_id
               ORDER BY occurred_at DESC, id DESC
           ) AS event_rank
    FROM order_status_events
    WHERE order_id IN (1, 2, 9, 10)
)
SELECT order_id,
       new_status,
       occurred_at,
       event_rank
FROM ranked
ORDER BY order_id, event_rank;
```

**Как читается запрос**

- `PARTITION BY order_id` — нумерация начинается заново в каждом заказе.
- `ORDER BY occurred_at DESC, id DESC` — внутри заказа самое новое событие получает номер 1.

| order_id | new_status | occurred_at | event_rank |
|---:|---|---|---:|
| 1 | shipped | 2026-04-07 13:56:01.570686+00 | 1 |
| 1 | processing | 2026-04-06 22:43:01.570686+00 | 2 |
| 1 | pending | 2026-04-05 18:53:01.570686+00 | 3 |
| 2 | pending | 2025-11-24 00:07:56.927714+00 | 1 |
| 9 | cancelled | 2026-06-04 18:29:17.791221+00 | 1 |
| 9 | processing | 2026-06-04 03:36:17.791221+00 | 2 |
| 9 | pending | 2026-06-02 15:50:17.791221+00 | 3 |
| 10 | delivered | 2026-06-06 17:28:10.808572+00 | 1 |
| 10 | shipped | 2026-06-03 22:28:10.808572+00 | 2 |
| 10 | processing | 2026-06-03 02:41:10.808572+00 | 3 |
| 10 | pending | 2026-06-01 17:09:10.808572+00 | 4 |

Строки с `event_rank = 1` — и есть текущие статусы. Чтобы оставить только их, во внешнем запросе добавляют `WHERE event_rank = 1`.

### Способ 2: DISTINCT ON

В PostgreSQL для этой задачи есть короткая запись.

```sql

SELECT DISTINCT ON (group_expression) column_list
FROM table_name
ORDER BY group_expression, sort_expression;
```

| Часть | Что означает |
|---|---|
| `DISTINCT ON (group_expression)` | из всех строк с одинаковым значением `group_expression` оставить **одну — первую** |
| `ORDER BY group_expression, …` | какая строка в группе первая. `ORDER BY` обязан начинаться с тех же выражений, что в `DISTINCT ON` |
| `sort_expression` | порядок внутри группы; от него зависит, какая строка останется |

**Источник:** https://www.postgresql.org/docs/16/sql-select.html

**EN:**

> `SELECT DISTINCT ON ( expression [, ...] )` keeps only the first row of each set of rows where the given expressions evaluate to equal. … Note that the “first row” of each set is unpredictable unless `ORDER BY` is used to ensure that the desired row appears first.
>
> The `DISTINCT ON` expression(s) must match the leftmost `ORDER BY` expression(s).

**RU:**

> `SELECT DISTINCT ON ( выражение [, ...] )` оставляет только первую строку из каждого набора строк, в которых заданные выражения равны. … «Первая строка» набора непредсказуема, если не использовать `ORDER BY`, который ставит нужную строку первой.
>
> Выражения `DISTINCT ON` должны совпадать с самыми левыми выражениями `ORDER BY`.

**Задача.** Та же: текущий статус заказов 1, 2, 9, 10 по журналу.

```sql

SELECT DISTINCT ON (order_id)
       order_id,
       new_status,
       occurred_at
FROM order_status_events
WHERE order_id IN (1, 2, 9, 10)
ORDER BY order_id, occurred_at DESC, id DESC;
```

**Как читается запрос**

- `ORDER BY order_id, occurred_at DESC, id DESC` — события выстраиваются так же, как в таблице с `event_rank` выше: по заказу, внутри заказа — от новых к старым.
- `DISTINCT ON (order_id)` — из каждого заказа остаётся первая строка этого порядка, то есть самое новое событие.

| order_id | new_status | occurred_at |
|---:|---|---|
| 1 | shipped | 2026-04-07 13:56:01.570686+00 |
| 2 | pending | 2025-11-24 00:07:56.927714+00 |
| 9 | cancelled | 2026-06-04 18:29:17.791221+00 |
| 10 | delivered | 2026-06-06 17:28:10.808572+00 |

**Нюансы**

- `DISTINCT ON` есть только в PostgreSQL. `row_number` есть во всех крупных базах — на собеседовании полезно знать оба способа.
- `DISTINCT ON` оставляет ровно одну строку на группу. Если нужно «последние 2 события каждого заказа», подходит только `row_number` с `event_rank <= 2`.
- Порядок результата `DISTINCT ON` задан тем же `ORDER BY`. Если отчёт нужно отсортировать иначе, например по времени события, запрос с `DISTINCT ON` оборачивают в CTE и сортируют снаружи.

---

## Сверка журнала с текущим статусом

Статус заказа хранится в двух местах: в `orders.status` и в последнем событии журнала. Если приложение где-то обновило одно и забыло другое, они разойдутся. Такую сверку делают регулярно.

**Задача.** Команда данных проверяет, совпадает ли у всех заказов текущий статус в карточке заказа и последнее событие журнала.

```sql

WITH last_event AS (
    SELECT DISTINCT ON (order_id)
           order_id,
           new_status
    FROM order_status_events
    ORDER BY order_id, occurred_at DESC, id DESC
)
SELECT count(*) AS orders_checked,
       count(*) FILTER (WHERE le.new_status <> o.status) AS mismatched
FROM last_event le
JOIN orders o ON o.id = le.order_id;
```

**Как читается запрос**

- `last_event` — последнее событие каждого заказа: 100007 строк.
- `JOIN orders o ON o.id = le.order_id` — к последнему событию пристыковывается карточка того же заказа.
- `count(*)` — сколько заказов проверено.
- `count(*) FILTER (WHERE le.new_status <> o.status)` — у скольких статусы не совпали.

| orders_checked | mismatched |
|---:|---:|
| 100007 | 0 |

Расхождений нет.

**Нюансы**

- `JOIN` не увидит заказ, у которого нет ни одного события: такой заказ не попадёт в `last_event`. Чтобы найти и такие заказы, сверку строят от `orders` через `LEFT JOIN` и отдельно считают строки, где события не нашлось.

---

## Статус заказа на прошлую дату

Вопрос «сколько заказов было в сборке на 1 июня» по `orders.status` не решить: поле показывает только сегодняшнее состояние. По журналу — можно: нужно взять последнее событие **не позже** нужного момента.

Это тот же приём «последнее событие», только с условием на время в `WHERE`.

**Задача.** Поддержка разбирает жалобу, поданную 5 июня 2026 года в 00:00 UTC. Нужно узнать, в каком статусе тогда были заказы 9 и 10.

```sql

SELECT DISTINCT ON (order_id)
       order_id,
       new_status,
       occurred_at
FROM order_status_events
WHERE order_id IN (9, 10)
  AND occurred_at <= '2026-06-05 00:00:00+00'
ORDER BY order_id, occurred_at DESC, id DESC;
```

**Как читается запрос**

- `occurred_at <= '2026-06-05 00:00:00+00'` — события после этого момента отбрасываются: на тот момент их ещё не было.
- Из оставшихся у каждого заказа берётся самое новое.

Что остаётся после `WHERE`:

| order_id | new_status | occurred_at | Осталось? |
|---:|---|---|---|
| 9 | pending | 2026-06-02 15:50 | да |
| 9 | processing | 2026-06-04 03:36 | да |
| 9 | cancelled | 2026-06-04 18:29 | да, самое новое |
| 10 | pending | 2026-06-01 17:09 | да |
| 10 | processing | 2026-06-03 02:41 | да |
| 10 | shipped | 2026-06-03 22:28 | да, самое новое |
| 10 | delivered | 2026-06-06 17:28 | нет: позже 5 июня |

Результат:

| order_id | new_status | occurred_at |
|---:|---|---|
| 9 | cancelled | 2026-06-04 18:29:17.791221+00 |
| 10 | shipped | 2026-06-03 22:28:10.808572+00 |

Сегодня заказ 10 доставлен, а 5 июня он был в доставке.

**Задача.** Операционный директор хочет срез на 1 июня 2026 года, 00:00 UTC: сколько заказов в тот момент было в каждом статусе.

```sql

WITH status_at_moment AS (
    SELECT DISTINCT ON (order_id)
           order_id,
           new_status
    FROM order_status_events
    WHERE occurred_at <= '2026-06-01 00:00:00+00'
    ORDER BY order_id, occurred_at DESC, id DESC
)
SELECT new_status,
       count(*) AS orders
FROM status_at_moment
GROUP BY new_status
ORDER BY orders DESC;
```

**Как читается запрос**

- `status_at_moment` — статус каждого заказа на 1 июня. Заказы, созданные позже, сюда не попадают: у них нет ни одного события до этого момента.
- Второй шаг считает заказы по статусам.

| new_status | orders |
|---|---:|
| processing | 16828 |
| pending | 16817 |
| cancelled | 16783 |
| shipped | 16689 |
| delivered | 16515 |

Сумма — 83632. Ровно столько заказов создано до 1 июня 2026 года: каждый заказ, существовавший в тот момент, получил ровно один статус.

---

## Воронка заказов

Воронка отвечает на вопрос: сколько заказов дошло до каждого шага и какая доля переходит со шага на шаг. Заказ «дошёл до шага», если в журнале есть событие с этим статусом.

**Задача.** Продакт-менеджер просит воронку: сколько заказов создано, дошло до сборки, до доставки, доставлено. И процент перехода с каждого шага на следующий, а также от создания до доставки.

```sql

WITH funnel AS (
    SELECT count(DISTINCT order_id) FILTER (WHERE new_status = 'pending') AS created,
           count(DISTINCT order_id) FILTER (WHERE new_status = 'processing') AS processing,
           count(DISTINCT order_id) FILTER (WHERE new_status = 'shipped') AS shipped,
           count(DISTINCT order_id) FILTER (WHERE new_status = 'delivered') AS delivered
    FROM order_status_events
)
SELECT created,
       processing,
       shipped,
       delivered,
       round(100.0 * processing / created, 2) AS to_processing_pct,
       round(100.0 * shipped / processing, 2) AS to_shipped_pct,
       round(100.0 * delivered / shipped, 2) AS to_delivered_pct,
       round(100.0 * delivered / created, 2) AS total_pct
FROM funnel;
```

**Как читается запрос**

- `count(DISTINCT order_id) FILTER (WHERE new_status = 'processing')` — сколько **разных заказов** имеют событие «в сборке». `DISTINCT` защищает от двойного счёта, если у заказа окажется два одинаковых события.
- `funnel` — одна строка с четырьмя числами.
- `100.0 * processing / created` — доля перешедших в сборку от созданных. `100.0`, а не `100`, чтобы деление было дробным.

| created | processing | shipped | delivered | to_processing_pct | to_shipped_pct | to_delivered_pct | total_pct |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 100007 | 69903 | 39909 | 20076 | 69.90 | 57.09 | 50.30 | 20.07 |

Как это читать: из 100 созданных заказов в сборку уходит почти 70, в доставку — около 40, доставляется около 20.

**Нюансы**

- Отменённые заказы не выделены отдельным шагом, но видны как «потери» между шагами: 100007 − 69903 = 30104 заказа не дошли до сборки. Из них 10158 отменены из `pending`, остальные 19946 до сих пор ждут обработки.
- Воронку часто строят по когортам: заказы, созданные в одном месяце. Для этого к событиям пристыковывают дату создания заказа и добавляют её в `GROUP BY`.

---

## Сколько заказ провёл в каждом статусе: lead

Время в статусе = момент следующего события минус момент текущего. Следующее событие того же заказа читает `lead` из темы 09.

**Задача.** Руководитель склада разбирает заказы 9 и 10: сколько времени каждый провёл в каждом статусе.

```sql

SELECT order_id,
       new_status,
       occurred_at,
       lead(occurred_at) OVER (
           PARTITION BY order_id
           ORDER BY occurred_at, id
       ) AS left_status_at,
       lead(occurred_at) OVER (
           PARTITION BY order_id
           ORDER BY occurred_at, id
       ) - occurred_at AS time_in_status
FROM order_status_events
WHERE order_id IN (9, 10)
ORDER BY order_id, occurred_at;
```

**Как читается запрос**

- `PARTITION BY order_id` — `lead` не заглядывает в чужой заказ.
- `lead(occurred_at)` — момент следующего события того же заказа, то есть когда заказ **вышел** из текущего статуса.
- Разница двух `timestamptz` — `interval` (тема 10).

| order_id | new_status | occurred_at | left_status_at | time_in_status |
|---:|---|---|---|---|
| 9 | pending | 2026-06-02 15:50:17.791221+00 | 2026-06-04 03:36:17.791221+00 | 1 day 11:46:00 |
| 9 | processing | 2026-06-04 03:36:17.791221+00 | 2026-06-04 18:29:17.791221+00 | 14:53:00 |
| 9 | cancelled | 2026-06-04 18:29:17.791221+00 | NULL | NULL |
| 10 | pending | 2026-06-01 17:09:10.808572+00 | 2026-06-03 02:41:10.808572+00 | 1 day 09:32:00 |
| 10 | processing | 2026-06-03 02:41:10.808572+00 | 2026-06-03 22:28:10.808572+00 | 19:47:00 |
| 10 | shipped | 2026-06-03 22:28:10.808572+00 | 2026-06-06 17:28:10.808572+00 | 2 days 19:00:00 |
| 10 | delivered | 2026-06-06 17:28:10.808572+00 | NULL | NULL |

`NULL` у последнего события значит: заказ из этого статуса ещё не вышел.

**Задача.** Руководитель склада хочет сводку по всем заказам: по каждому статусу — сколько раз заказ из него вышел, сколько заказов в нём до сих пор, среднее время в статусе в часах и самое долгое время.

```sql

WITH status_time AS (
    SELECT new_status,
           lead(occurred_at) OVER (
               PARTITION BY order_id
               ORDER BY occurred_at, id
           ) - occurred_at AS time_in_status
    FROM order_status_events
)
SELECT new_status,
       count(time_in_status) AS left_status,
       count(*) - count(time_in_status) AS still_in_status,
       round(avg(extract(epoch FROM time_in_status)) / 3600, 2) AS avg_hours,
       max(time_in_status) AS max_time
FROM status_time
GROUP BY new_status
ORDER BY new_status;
```

**Как читается запрос**

- `status_time` — у каждого события время, которое заказ провёл в этом статусе; у последнего события заказа — `NULL`.
- `count(time_in_status)` — считает только не-`NULL`, то есть выходы из статуса.
- `count(*) - count(time_in_status)` — события, после которых ничего не было: заказ до сих пор в этом статусе.
- `extract(epoch FROM time_in_status) / 3600` — интервал в часах (тема 10). `avg` пропускает `NULL`.

| new_status | left_status | still_in_status | avg_hours | max_time |
|---|---:|---:|---:|---|
| cancelled | 0 | 20071 | NULL | NULL |
| delivered | 0 | 20076 | NULL | NULL |
| pending | 80061 | 19946 | 17.91 | 1 day 12:59:00 |
| processing | 49822 | 20081 | 21.66 | 3 days 22:50:00 |
| shipped | 20076 | 19833 | 95.27 | 6 days 23:00:00 |

**Нюансы**

- `cancelled` и `delivered` — конечные статусы: из них заказ не выходит никогда, поэтому среднего нет.
- Время в `pending` здесь 17.91 ч, а в теме 10 ожидание сборки было 19.00 ч. Это не ошибка: здесь выход из `pending` — любой следующий шаг, включая отмену, а в теме 10 считались только заказы, ушедшие в сборку.

---

## Нарушение срока передачи в доставку: lag

Предыдущее событие того же заказа читает `lag`. Это удобно, когда вопрос задан про конкретный переход: «сколько прошло от сборки до передачи в доставку».

**Задача.** По договору со службой доставки склад передаёт заказ в доставку не позже чем через 48 часов после начала сборки. Руководитель склада хочет знать, сколько передач в доставку нарушили срок и из скольких всего.

```sql

WITH transition_time AS (
    SELECT order_id,
           new_status,
           occurred_at - lag(occurred_at) OVER (
               PARTITION BY order_id
               ORDER BY occurred_at, id
           ) AS time_from_previous
    FROM order_status_events
)
SELECT count(*) AS shipped_orders,
       count(*) FILTER (WHERE time_from_previous > interval '48 hours') AS late_orders
FROM transition_time
WHERE new_status = 'shipped';
```

**Как читается запрос**

- `lag(occurred_at)` — момент предыдущего события того же заказа. Для события `shipped` предыдущее всегда `processing`: другого пути в доставку нет.
- `WHERE new_status = 'shipped'` — стоит во **внешнем** запросе. Если поставить его внутрь `transition_time`, из журнала уйдут события `processing`, и `lag` не найдёт предыдущее событие (тема 09: окно считается после `WHERE`).
- `interval '48 hours'` — граница срока.

| shipped_orders | late_orders |
|---:|---:|
| 39909 | 3977 |

Почти каждая десятая передача в доставку опоздала.

---

## Из какого статуса отменяют заказы

**Задача.** Продакт-менеджер хочет понять, где теряются заказы: сколько отмен произошло из ожидания обработки и сколько — из сборки.

```sql

SELECT old_status AS cancelled_from,
       count(*) AS cancellations
FROM order_status_events
WHERE new_status = 'cancelled'
GROUP BY old_status
ORDER BY cancellations DESC;
```

**Как читается запрос**

- В событии отмены `old_status` — статус, из которого отменили. Поэтому отдельный `lag` не нужен: журнал уже хранит предыдущий статус в самой строке.

| cancelled_from | cancellations |
|---|---:|
| pending | 10158 |
| processing | 9913 |

Отмены делятся почти поровну. Половина отменённых заказов успела занять сборщиков склада.

---

## Проверка журнала: недопустимые переходы

Журнал полезен, только если ему можно верить. Первая проверка: нет ли переходов, которых по правилам магазина быть не может, например `delivered → pending`.

Список допустимых переходов удобно задать прямо в запросе через `VALUES` (темы 06 и 08) и искать события, которых в этом списке нет, через `NOT EXISTS` (тема 06).

Есть одна сложность: у первого события `old_status` равен `NULL`. Обычное сравнение `old_status = NULL` даёт не «да», а `NULL`, поэтому строку не найдёт. Для таких сравнений есть `IS NOT DISTINCT FROM`.

### Синтаксис

```sql

a IS NOT DISTINCT FROM b
```

| a | b | `a = b` | `a IS NOT DISTINCT FROM b` |
|---|---|---|---|
| `'pending'` | `'pending'` | true | true |
| `'pending'` | `'shipped'` | false | false |
| `NULL` | `'pending'` | NULL | false |
| `NULL` | `NULL` | NULL | true |

**Источник:** https://www.postgresql.org/docs/16/functions-comparison.html

**EN:**

> Ordinary comparison operators yield null (signifying “unknown”), not true or false, when either input is null. … When this behavior is not suitable, use the `IS [ NOT ] DISTINCT FROM` predicates … `IS NOT DISTINCT FROM` is identical to `=` for non-null inputs, but it returns true when both inputs are null, and false when only one input is null.

**RU:**

> Обычные операторы сравнения возвращают null (что значит «неизвестно»), а не true или false, если хотя бы один вход равен null. … Когда такое поведение не подходит, используйте предикаты `IS [ NOT ] DISTINCT FROM` … `IS NOT DISTINCT FROM` совпадает с `=` для входов, не равных null, но возвращает true, когда оба входа null, и false, когда null только один.

**Задача.** Команда данных проверяет, есть ли в журнале переходы, которых нет в правилах магазина.

```sql

WITH allowed_transition (old_status, new_status) AS (
    VALUES (NULL::text, 'pending'),
           ('pending', 'processing'),
           ('pending', 'cancelled'),
           ('processing', 'shipped'),
           ('processing', 'cancelled'),
           ('shipped', 'delivered')
)
SELECT count(*) AS invalid_events
FROM order_status_events e
WHERE NOT EXISTS (
    SELECT 1
    FROM allowed_transition a
    WHERE a.old_status IS NOT DISTINCT FROM e.old_status
      AND a.new_status = e.new_status
);
```

**Как читается запрос**

- `allowed_transition (old_status, new_status) AS (VALUES …)` — шаг из шести строк-переходов. В скобках после имени шага — имена его столбцов: у `VALUES` своих имён нет.
- `NULL::text` — у `NULL` нет своего типа, и PostgreSQL нужно подсказать, что столбец текстовый.
- `NOT EXISTS (…)` — событие попадает в результат, если в списке нет перехода с такой же парой статусов.
- `a.old_status IS NOT DISTINCT FROM e.old_status` — первое событие заказа (`NULL → pending`) находит в списке строку `(NULL, 'pending')`. С обычным `=` все 100007 первых событий оказались бы «недопустимыми».

| invalid_events |
|---:|
| 0 |

---

## Проверка журнала: разрывы в цепочке

Вторая проверка: `old_status` каждого события должен совпадать с `new_status` предыдущего события того же заказа. Если заказ перешёл `pending → processing`, следующее событие обязано начинаться с `processing`. Иначе в журнале пропущено событие.

**Задача.** Команда данных ищет события, у которых статус «до» не совпадает с тем, чем закончилось предыдущее событие заказа.

```sql

WITH chained AS (
    SELECT order_id,
           old_status,
           lag(new_status) OVER (
               PARTITION BY order_id
               ORDER BY occurred_at, id
           ) AS previous_new_status
    FROM order_status_events
)
SELECT count(*) AS broken_events
FROM chained
WHERE old_status IS DISTINCT FROM previous_new_status;
```

**Как читается запрос**

- `lag(new_status)` — чем закончилось предыдущее событие заказа. У первого события предыдущего нет — `NULL`.
- `old_status IS DISTINCT FROM previous_new_status` — «значения различаются», где два `NULL` считаются равными. У первого события оба значения `NULL`, и оно разрывом не считается.

| broken_events |
|---:|
| 0 |

---

## События по дням

**Задача.** Операционный директор хочет таблицу на первую неделю июня 2026 года: по каждому дню — сколько заказов создано, отправлено в сборку, передано в доставку, доставлено, отменено. Дни — по UTC.

```sql

SELECT occurred_at::date AS event_date,
       count(*) FILTER (WHERE new_status = 'pending') AS created,
       count(*) FILTER (WHERE new_status = 'processing') AS processing,
       count(*) FILTER (WHERE new_status = 'shipped') AS shipped,
       count(*) FILTER (WHERE new_status = 'delivered') AS delivered,
       count(*) FILTER (WHERE new_status = 'cancelled') AS cancelled
FROM order_status_events
WHERE occurred_at >= '2026-06-01'
  AND occurred_at < '2026-06-08'
GROUP BY occurred_at::date
ORDER BY event_date;
```

**Как читается запрос**

- `occurred_at >= '2026-06-01' AND occurred_at < '2026-06-08'` — полуоткрытый промежуток из темы 10: семь полных дней.
- `GROUP BY occurred_at::date` — одна строка на день.
- Каждый `count(*) FILTER (…)` считает события своего типа за день.

| event_date | created | processing | shipped | delivered | cancelled |
|---|---:|---:|---:|---:|---:|
| 2026-06-01 | 306 | 190 | 100 | 41 | 57 |
| 2026-06-02 | 279 | 206 | 113 | 52 | 47 |
| 2026-06-03 | 282 | 179 | 123 | 59 | 70 |
| 2026-06-04 | 275 | 220 | 101 | 61 | 58 |
| 2026-06-05 | 292 | 200 | 112 | 54 | 72 |
| 2026-06-06 | 267 | 195 | 127 | 59 | 50 |
| 2026-06-07 | 279 | 177 | 124 | 67 | 32 |

**Нюансы**

- Это события **дня**, а не заказы дня. Заказ 10 создан 1 июня и доставлен 6 июня: он посчитан в `created` за 1 июня и в `delivered` за 6 июня.

---

## Что обычно спрашивают на собеседованиях

- Чем журнал событий отличается от поля «текущий статус» и какие вопросы можно решить только журналом.
- Как найти последнее событие каждой сущности. Два способа: `row_number` и `DISTINCT ON`, плюс чем они отличаются.
- Как узнать состояние на прошлую дату.
- Как посчитать время между событиями: `lead` и `lag`, почему нужен `PARTITION BY`.
- Как построить воронку и почему считают `count(DISTINCT …)`.
- Как развернуть узкий журнал в одну строку на сущность: агрегат с `FILTER`.
- Как склеить путь: `string_agg` с `ORDER BY` внутри.
- Как проверить журнал на недопустимые переходы и пропуски; как сравнить с `NULL`.
- Почему условие на тип события нельзя ставить в `WHERE` того же запроса, где считается `lag`.

---

## Карта выбора конструкции

| Вопрос | Конструкция |
|---|---|
| Путь сущности одной строкой | `string_agg(value, ', ' ORDER BY …)` + `GROUP BY` |
| Момент каждого шага в отдельном столбце | `max(occurred_at) FILTER (WHERE …)` + `GROUP BY` |
| Последнее событие каждой сущности | `DISTINCT ON (…) … ORDER BY …, occurred_at DESC` или `row_number() … = 1` |
| Последние N событий | `row_number() … <= N` |
| Состояние на момент | `WHERE occurred_at <= момент` + последнее событие |
| Сколько дошло до шага | `count(DISTINCT id) FILTER (WHERE …)` |
| Время в статусе | `lead(occurred_at) OVER (PARTITION BY … ORDER BY …) - occurred_at` |
| Время от предыдущего шага | `occurred_at - lag(occurred_at) OVER (…)` |
| Сравнение, где `NULL` равен `NULL` | `IS NOT DISTINCT FROM` / `IS DISTINCT FROM` |
| Список правил прямо в запросе | `WITH rules (…) AS (VALUES …)` + `NOT EXISTS` |

---

## Что попробовать самостоятельно

1. Какой статус был у заказа 1 на 7 апреля 2026 года, 00:00 UTC? Ответ: `processing`.
2. Какая доля доставленных заказов доставлена не позже чем через 7 дней после создания? Ответ: 13978 из 20076, то есть 69.63%.
3. Сколько в среднем часов заказ провёл в последнем статусе перед отменой — отдельно для отмен из `pending` и из `processing`? Ответ: 10.41 ч и 11.45 ч.
