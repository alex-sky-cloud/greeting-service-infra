# Тема 04 — JOIN: INNER JOIN и LEFT JOIN

> **Статус:** теория · **Следующий шаг:** изучить материал → написать в чат *«дай интервью по теме 4»*

---

## Оглавление

- [Что изучаем](#что-изучаем)
- [Зачем JOIN — история из двух таблиц](#зачем-join--история-из-двух-таблиц)
- [Как таблицы связаны в Reactive Shop](#как-таблицы-связаны-in-reactive-shop)
- [Учебные таблицы «до JOIN»](#учебные-таблицы-до-join)
- [INNER JOIN — только пары «клиент ↔ заказ»](#inner-join--только-пары-клиент--заказ)
- [LEFT JOIN — все клиенты, даже без заказов](#left-join--все-клиенты-даже-без-заказов)
- [ORDER BY: NULLS FIRST и NULLS LAST](#order-by-nulls-first-и-nulls-last)
- [INNER vs LEFT — один запрос, два результата](#inner-vs-left--один-запрос-два-результата)
- [ON, WHERE и порядок выполнения](#on-where-и-порядок-выполнения)
- [Псевдонимы таблиц](#псевдонимы-таблиц)
- [Демо-данные для JOIN (миграция V12)](#демо-данные-для-join-миграция-v12)
- [Типичные ошибки на интервью](#типичные-ошибки-на-интервью)
- [Что попробовать самостоятельно](#что-попробовать-самостоятельно)

---

## Что изучаем

До темы 04 вы брали данные **из одной таблицы**. В реальном магазине информация **разнесена**:

| Таблица | Что хранит | ~строк |
|---|---|---|
| `users` | клиенты: имя, email | 5 005 |
| `orders` | заказы: сумма, статус, **ссылка на клиента** | 100 007 |

**JOIN** («соединение») — способ **склеить** две таблицы по общему полю и получить одну «широкую» таблицу для отчёта.

В этой теме — только два вида (их чаще всего спрашивают на интервью):

| JOIN | Простыми словами |
|---|---|
| `INNER JOIN` | только строки, где **есть пара** в обеих таблицах |
| `LEFT JOIN` | **все** строки слева + данные справа; если пары нет — справа **NULL** |

Запросы — в SQL-клиенте:

```sql
SET search_path TO reactive_study;
```

---

## Зачем JOIN — история из двух таблиц

**Ситуация:** оператор поддержки видит заказ № 42 000. 
- В `orders` есть `user_id = 1234`, но **нет имени** клиента — только число.

**Без JOIN** пришлось бы:
1. открыть `orders`, запомнить `user_id`;
2. открыть `users`, искать id = 1234;
3. смотреть имя вручную.

**С JOIN** — один запрос: заказ (**order** из таблицы **orders**) **и** имя клиента (из таблицы **users**) в одной строке.

**Аналогия без программирования:** два списка в Excel.

- **Лист «Заказы»:** номер заказа, **код клиента**, сумма.
- **Лист «Клиенты»:** **код клиента**, имя, email.

**JOIN** = «для каждой строки заказа (**order** из таблицы **orders**) найти строку клиента (из таблицы **users**) с тем же кодом (**id** клиента) и поставить рядом имя(**name**) и **email**».

Поле-связка здесь: 
 - `orders.user_id` = `users.id`.

**Источник:** https://www.postgresql.org/docs/current/queries-table-expressions.html#QUERIES-JOIN

EN:

> A joined table is a table derived from two other (real or derived) tables according to the rules of the particular join type. Inner, outer, and cross-joins are available.

RU:

> Соединённая таблица — это таблица, полученная из двух других (реальных или производных) таблиц по правилам конкретного типа соединения. Доступны inner, outer и cross-join.

---

## Как таблицы связаны in Reactive Shop

```text
users                          orders
┌────┬─────────────┐          ┌────┬─────────┬────────┐
│ id │ full_name   │          │ id │ user_id │ amount │
├────┼─────────────┤          ├────┼─────────┼────────┤
│  1 │ Ann Smith   │◄─────────│  1 │    1    │ 999.99 │
│  2 │ Bob Jones   │◄─────────│  2 │    1    │  29.99 │
│  3 │ Carol Lee   │◄─────────│  3 │    2    │  79.00 │
└────┴─────────────┘          └────┴─────────┴────────┘
     ▲                              │
     └──────── user_id ссылается на id
```

- В `orders` лежит **номер** клиента (`user_id`), не имя.
- Один клиент → **много** заказов (связь «один ко многим»).
- Условие склейки почти всегда: `users.id = orders.user_id`.

---

## Учебные таблицы «до JOIN»

Дальше все примеры строятся на **маленьком фрагменте** данных. Сначала смотрим **исходные таблицы**, потом — **результат JOIN**.

### Таблица `users` (клиенты)

| id | full_name |
|---|---|
| 1 | Ann Smith |
| 2 | Bob Jones |
| 3 | Carol Lee |
| 5001 | JOIN Demo No Orders 1 |

Клиенты **5001** и подобные добавлены миграцией V12 — у них **нет заказов** (нужно для LEFT JOIN).

### Таблица `orders` (заказы)

| id | user_id | amount | status |
|---|---|---|---|
| 12854 | 1 | 141.15 | delivered |
| 17784 | 1 | 1992.83 | pending |
| 24773 | 1 | 1737.84 | shipped |
| … | 2 | … | … |
| 100001 | 5005 | 42.00 | delivered |

- `user_id` — **чей** это заказ (ссылка на `users.id`).
- У Ann (`id = 1`) **много** заказов → в результате JOIN её имя **повторится** на каждой строке заказа.
- У клиента **5001** заказов **нет** → при LEFT JOIN справа будут **NULL**.

---

## INNER JOIN — только пары «клиент ↔ заказ»

**Источник:** https://www.postgresql.org/docs/current/queries-table-expressions.html#QUERIES-JOIN

EN:

> `INNER JOIN` — For each row R1 of T1, the joined table has a row for each row in T2 that satisfies the join condition with R1.

RU:

> `INNER JOIN` — для каждой строки R1 из T1 соединённая таблица содержит строку для каждой строки из T2, которая удовлетворяет условию соединения с R1.

**Простыми словами:** в результат попадают только строки, где условие `ON` **истинно**. Нет пары «клиент ↔ заказ» — строки **нет в ответе**.

---

### Пример 1 — поддержка: «кто сделал этот заказ?»

**Задача:** оператор видит заказ и должен сразу получить **имя покупателя** и **сумму**. Нужны только **реальные** пары «заказ + клиент».

**Запрос:**

```sql
SELECT o.id AS order_id,
       u.full_name,
       o.amount,
       o.status
FROM orders o
INNER JOIN users u ON u.id = o.user_id
WHERE u.id = 1
ORDER BY o.id
LIMIT 3;
```

**Разбор запроса:**

| Часть | Что делает |
|---|---|
| `FROM orders o` | начинаем с заказов |
| `INNER JOIN users u` | подтягиваем клиентов |
| `ON u.id = o.user_id` | склеиваем: id клиента = user_id в заказе |
| `WHERE u.id = 1` | только Ann Smith |
| `LIMIT 3` | первые 3 строки для наглядности |

**Результат на вашей базе:**

| order_id | full_name | amount | status |
|---|---|---|---|
| 12854 | Ann Smith | 141.15 | shipped |
| 17784 | Ann Smith | 1992.83 | processing |
| 24773 | Ann Smith | 1737.84 | shipped |

**Что достигли:** одна таблица — видно **и заказ, и имя**, без ручного поиска по `user_id`.

---

### Пример 2 — на двух маленьких таблицах (механика INNER JOIN)

**Задача:** понять, **какие строки остаются**, а какие **отбрасываются**.

**Исходные данные — `users`:**

| id | full_name |
|---|---|
| 1 | Ann |
| 2 | Bob |
| 3 | Carol |

**Исходные данные — `orders`:**

| id | user_id | amount |
|---|---|---|
| 101 | 1 | 999.99 |
| 102 | 1 | 29.99 |
| 103 | 2 | 79.00 |

Carol (**id = 3**) зарегистрирована, но **заказов нет**.

**Запрос:**

```sql
SELECT u.full_name, o.id AS order_id, o.amount
FROM users u
INNER JOIN orders o ON o.user_id = u.id
ORDER BY u.id, o.id;
```

**Результат:**

| full_name | order_id | amount |
|---|---|---|
| Ann | 101 | 999.99 |
| Ann | 102 | 29.99 |
| Bob | 103 | 79.00 |

**Пошагово:**

| Шаг | Что делает SQL |
|---|---|
| 1 | Берёт Ann (`id=1`) → находит заказы 101 и 102 → **2 строки** |
| 2 | Берёт Bob (`id=2`) → находит заказ 103 → **1 строка** |
| 3 | Берёт Carol (`id=3`) → заказов нет → **0 строк** |

**Вывод:** Carol **исчезла** из результата — для INNER JOIN нужна **пара** слева и справа.

---

### Пример 3 — клиент без заказов: INNER возвращает 0 строк

**Задача:** проверить учебного клиента `join-demo-no-orders-1` — у него **нет заказов** (миграция V12).

**Запрос:**

```sql
SELECT u.email, u.full_name, o.id AS order_id
FROM users u
INNER JOIN orders o ON o.user_id = u.id
WHERE u.email = 'join-demo-no-orders-1@example.com';
```

**Результат:**

| email | full_name | order_id |
|---|---|---|
| *(пусто — 0 строк)* | | |

**Вывод:** INNER JOIN **не подходит**, если нужно видеть клиентов **без покупок**.

---

## LEFT JOIN — все клиенты, даже без заказов

**Источник:** https://www.postgresql.org/docs/current/queries-table-expressions.html#QUERIES-JOIN

EN:

> `LEFT OUTER JOIN` — First, an inner join is performed. Then, for each row in T1 that does not satisfy the join condition with any row in T2, a joined row is added with null values in columns of T2. Thus, the joined table always has at least one row for each row in T1.

RU:

> `LEFT OUTER JOIN` — сначала выполняется inner join. Затем для каждой строки в T1, не удовлетворившей условию соединения ни с одной строкой в T2, добавляется соединённая строка со значениями NULL в столбцах T2. Таким образом, соединённая таблица всегда содержит хотя бы одну строку для каждой строки в T1.

**Простыми словами:**

1. Сначала — как **INNER JOIN** (все найденные пары).
2. Потом — для каждой «лишней» строки **слева** добавляется строка, где столбцы **справа = NULL**.

`LEFT JOIN` = `LEFT OUTER JOIN` (слова `OUTER` можно опускать).

---

### Пример 4 — на тех же маленьких таблицах (механика LEFT JOIN)

**Задача:** маркетинг хочет **всех** клиентов и их заказы **если есть**. Carol **не должна пропасть**.

**Исходные данные** — те же, что в примере 2 (Ann, Bob, Carol + 3 заказа).

**Запрос:**

```sql
SELECT u.full_name, o.id AS order_id, o.amount
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
ORDER BY u.id, o.id;
```

**Результат:**

| full_name | order_id | amount |
|---|---|---|
| Ann | 101 | 999.99 |
| Ann | 102 | 29.99 |
| Bob | 103 | 79.00 |
| Carol | **NULL** | **NULL** |

**Пошагово (после цитаты из документации):**

| Шаг | Что делает SQL |
|---|---|
| 1 (inner) | Ann + 2 заказа, Bob + 1 заказ — как в INNER JOIN |
| 2 (добавление) | Carol не нашла пару → **одна строка** с NULL справа |

**Вывод:** LEFT JOIN **сохраняет всех** клиентов из левой таблицы (`users`). Нет заказа — видим **NULL**, а не пустой ответ.

---

### Пример 5 — CRM: «спящие» клиенты (NULL на реальной базе)

**Задача:** найти клиентов, которые **зарегистрировались, но ни разу не заказали** — для re-activation рассылки.  
Используем учебных клиентов `join-demo-no-orders-*` (миграция V12).

**Запрос:**

```sql
SELECT u.email,
       u.full_name,
       o.id AS order_id,
       o.amount
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
WHERE u.email LIKE 'join-demo-no-orders-%'
ORDER BY u.email;
```

**Разбор запроса:**

| Часть | Что делает |
|---|---|
| `FROM users u` | **слева** — клиенты (главная таблица) |
| `LEFT JOIN orders o` | справа — заказы; если нет — NULL |
| `ON o.user_id = u.id` | правило склейки |
| `WHERE u.email LIKE …` | только 4 учебных клиента без заказов |

**Результат на вашей базе:**

| email | full_name | order_id | amount |
|---|---|---|---|
| join-demo-no-orders-1@example.com | JOIN Demo No Orders 1 | **NULL** | **NULL** |
| join-demo-no-orders-2@example.com | JOIN Demo No Orders 2 | **NULL** | **NULL** |
| join-demo-no-orders-3@example.com | JOIN Demo No Orders 3 | **NULL** | **NULL** |
| join-demo-no-orders-4@example.com | JOIN Demo No Orders 4 | **NULL** | **NULL** |

**Что достигли:** видим **4 клиента**; столбцы заказа **пустые (NULL)** — пара справа не нашлась.

---

### Пример 6 — один клиент с заказами + один без (сравнение в одном запросе)

**Задача:** в одном отчёте показать **оба** случая: клиент **с** заказами и клиент **без**.

**Запрос:**

```sql
SELECT u.email,
       u.full_name,
       o.id AS order_id,
       o.amount
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
WHERE u.email IN (
    'join-demo-no-orders-1@example.com',
    'join-demo-single-order@example.com'
)
ORDER BY u.email, o.id NULLS FIRST;
```

**Результат (первые строки):**

| email | full_name | order_id | amount |
|---|---|---|---|
| join-demo-no-orders-1@example.com | JOIN Demo No Orders 1 | **NULL** | **NULL** |
| join-demo-single-order@example.com | JOIN Demo Single Order | 100001 | 42.00 |
| join-demo-single-order@example.com | JOIN Demo Single Order | 100002 | 11.11 |
| join-demo-single-order@example.com | JOIN Demo Single Order | 100003 | 22.22 |
| … | … | … | … |

**Вывод:**

| Клиент | Строк в результате | order_id |
|---|---|---|
| No Orders 1 | **1** | NULL |
| Single Order | **7** (по числу заказов) | заполнен |

Имя клиента **повторяется** — это нормально: у одного человека несколько заказов.

---

## ORDER BY: NULLS FIRST и NULLS LAST

После LEFT JOIN в столбцах справа часто появляется **NULL**. При сортировке нужно понимать, **куда попадают NULL** — в начало или в конец.

**Это не отдельный оператор.** `NULLS FIRST` / `NULLS LAST` — **дополнение к конкретному столбцу** в `ORDER BY`, сразу после него:

```sql
ORDER BY u.email, o.id NULLS FIRST
--            ↑           ↑
--      1-й ключ      2-й ключ: id по возрастанию,
--                    NULL — в начале среди равного email
```

**Источник:** https://www.postgresql.org/docs/current/sql-select.html#SQL-ORDERBY

EN:

> If `NULLS LAST` is specified, null values sort after all non-null values; if `NULLS FIRST` is specified, null values sort before all non-null values. If neither is specified, the default behavior is `NULLS LAST` when `ASC` is specified or implied, and `NULLS FIRST` when `DESC` is specified.

RU:

> Если указано `NULLS LAST`, значения NULL идут после всех non-null; если `NULLS FIRST` — до всех non-null. Если не указано: по умолчанию `NULLS LAST` при `ASC` (или когда направление не задано), и `NULLS FIRST` при `DESC`.

### Таблица по умолчанию (PostgreSQL)

| Запись | Куда деваются NULL |
|---|---|
| `ORDER BY o.id` (ASC по умолчанию) | **в конце** (`NULLS LAST`) |
| `ORDER BY o.id DESC` | **в начале** (`NULLS FIRST`) |
| `ORDER BY o.id NULLS FIRST` | **явно в начале** |
| `ORDER BY o.id NULLS LAST` | **явно в конце** |

### Пример — когда NULLS FIRST меняет порядок

**Задача:** отсортировать **только по id заказа** — у «спящего» клиента id = NULL.

```sql
SELECT u.email, o.id AS order_id
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
WHERE u.email IN (
    'join-demo-no-orders-1@example.com',
    'join-demo-single-order@example.com'
);
```

**`ORDER BY o.id` (по умолчанию NULL в конце):**

| email | order_id |
|---|---|
| join-demo-single-order@example.com | 100001 |
| join-demo-single-order@example.com | 100002 |
| … | … |
| join-demo-no-orders-1@example.com | **NULL** |

**`ORDER BY o.id NULLS FIRST`:**

| email | order_id |
|---|---|
| join-demo-no-orders-1@example.com | **NULL** |
| join-demo-single-order@example.com | 100001 |
| join-demo-single-order@example.com | 100002 |
| … | … |

### Пример — когда NULLS FIRST **не меняет** результат

**Задача:** сортировка **сначала по email**, потом по id (как в практике, задание 12).

```sql
ORDER BY u.email, o.id
```

Клиент без заказов имеет email `join-demo-no-orders-1@…` — он **и так** идёт раньше `join-demo-single-order@…` по алфавиту.  
Поэтому строка с NULL **уже сверху** — не из‑за `NULLS FIRST`, а из‑за **первого** ключа `u.email`.

**Вывод:** `NULLS FIRST` нужен, когда сортируете по столбцу с NULL **без** более главного ключа выше; иначе порядок может не измениться.

---

### Пример 7 — отбор только «без заказов» (`WHERE o.id IS NULL`)

**Задача:** из **всех** клиентов оставить только тех, у кого **нет ни одного** заказа.

**Запрос:**

```sql
SELECT u.id, u.full_name, u.email
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
WHERE o.id IS NULL
ORDER BY u.id
LIMIT 5;
```

**Результат (фрагмент):**

| id | full_name | email |
|---|---|---|
| 5001 | JOIN Demo No Orders 1 | join-demo-no-orders-1@example.com |
| 5002 | JOIN Demo No Orders 2 | join-demo-no-orders-2@example.com |
| 5003 | JOIN Demo No Orders 3 | join-demo-no-orders-3@example.com |
| 5004 | JOIN Demo No Orders 4 | join-demo-no-orders-4@example.com |

**Почему работает:** LEFT JOIN сначала ставит NULL там, где заказ не найден; `WHERE o.id IS NULL` оставляет **только такие** строки.  
С **INNER JOIN** этот приём **не работает** — строк без заказа просто нет.

---

### GROUP BY после JOIN — что указывать

**Задача (задание 11):** посчитать **число заказов** и **сумму** по каждому клиенту.

После `INNER JOIN` у каждой строки **`u.id = o.user_id`** — это один и тот же клиент.  
`GROUP BY u.id` собирает **все строки заказов** этого клиента в одну «корзину»; `COUNT(o.id)` считает строки внутри.

**Достаточно:**

```sql
SELECT u.id,
       u.full_name,
       COUNT(o.id) AS cnt_orders,
       SUM(o.amount) AS amount_orders
FROM users u
INNER JOIN orders o ON o.user_id = u.id
WHERE u.id IN (1, 2, 3)
GROUP BY u.id, u.full_name
ORDER BY u.id;
```

**Результат:**

| id | full_name | cnt_orders | amount_orders |
|---|---|---|---|
| 1 | Ann Smith | 23 | 24106.88 |
| 2 | Bob Jones | 14 | 16205.29 |
| 3 | Carol Lee | 18 | 21619.10 |

**Почему `o.user_id` в GROUP BY не обязателен:** после `ON u.id = o.user_id` поле `o.user_id` **в каждой строке группы одинаково** и равно `u.id`. Добавить его в `GROUP BY` можно — результат **тот же**; SQL просто требует, чтобы все столбцы в `SELECT` (кроме агрегатов) были в `GROUP BY`.

| GROUP BY | cnt для Ann |
|---|---|
| `u.id, u.full_name` | 23 |
| `u.full_name` | 23 |
| `u.full_name, o.user_id` | 23 |

**Главное:** считает **`COUNT(o.id)`**, а не `GROUP BY o.user_id`. Группировка — **по клиенту**; заказы уже «размножены» строками **до** `GROUP BY`.

---

## INNER vs LEFT — один запрос, два результата

**Задача:** для **одних и тех же** учебных клиентов сравнить INNER и LEFT.

**Запрос A — INNER JOIN:**

```sql
SELECT u.email, o.id AS order_id
FROM users u
INNER JOIN orders o ON o.user_id = u.id
WHERE u.email LIKE 'join-demo-no-orders-%';
```

**Результат A:** **0 строк** (нет пар «клиент + заказ»).

**Запрос B — LEFT JOIN:**

```sql
SELECT u.email, o.id AS order_id
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
WHERE u.email LIKE 'join-demo-no-orders-%';
```

**Результат B:**

| email | order_id |
|---|---|
| join-demo-no-orders-1@example.com | **NULL** |
| join-demo-no-orders-2@example.com | **NULL** |
| join-demo-no-orders-3@example.com | **NULL** |
| join-demo-no-orders-4@example.com | **NULL** |

### Когда что выбирать

| Вопрос бизнеса | JOIN |
|---|---|
| «Покажи заказы **с именами** клиентов» | `INNER JOIN` |
| «Покажи **всех** клиентов и их заказы, если есть» | `LEFT JOIN` |
| «Кто зарегистрировался, но **не покупал**?» | `LEFT JOIN` + `WHERE o.id IS NULL` |
| «Сумма выручки по **реальным** заказам» | `INNER JOIN` |

**Правило:** INNER — только **совпадения**; LEFT — важна **левая** таблица целиком, правая опциональна.

`RIGHT JOIN` — зеркало LEFT: «главная» таблица **справа**. На практике чаще пишут `LEFT JOIN`, просто меняя таблицы местами.

## ON, WHERE и порядок выполнения

**`ON`** — **как** склеивать таблицы (правило «какой id = какому user_id»).

**`WHERE`** — **какие строки уже после склейки** оставить в отчёте.

Упрощённый порядок (как в теме 03):

```text
FROM / JOIN  →  ON  →  WHERE  →  GROUP BY  →  HAVING  →  SELECT  →  ORDER BY
```

**Источник:** https://www.postgresql.org/docs/current/queries-table-expressions.html

EN:

> The optional `WHERE`, `GROUP BY`, and `HAVING` clauses in the table expression specify a pipeline of successive transformations performed on the table derived in the `FROM` clause.

RU:

> Необязательные предложения `WHERE`, `GROUP BY` и `HAVING` в выражении таблицы задают последовательность преобразований, выполняемых над таблицей, полученной в предложении `FROM`.

### Пример 8 — INNER JOIN + фильтр по статусу

**Задача:** складу нужны **доставленные** заказы с **email** клиента для SMS-опроса.

**Запрос:**

```sql
SELECT o.id,
       u.email,
       o.amount
FROM orders o
INNER JOIN users u ON u.id = o.user_id
WHERE o.status = 'delivered'
  AND u.id = 1
ORDER BY o.id
LIMIT 3;
```

**Разбор:**

| Шаг | Что происходит |
|---|---|
| 1. JOIN | к заказу подставляется email клиента |
| 2. WHERE | только `delivered` и только Ann |
| 3. SELECT | id заказа, email, сумма |

**Результат (фрагмент — у Ann только 2 доставленных заказа):**

| id | email | amount |
|---|---|---|
| 28238 | ann@example.com | 1179.42 |
| 32482 | ann@example.com | 1130.22 |

**Вывод:** `ON` — **как склеить** таблицы; `WHERE` — **что оставить** после склейки.

---

## Псевдонимы таблиц

Длинно:

```sql
SELECT orders.id, users.full_name
FROM orders
INNER JOIN users ON users.id = orders.user_id;
```

Короче и читаемее (так пишут в production и на интервью):

```sql
SELECT o.id, u.full_name
FROM orders o
INNER JOIN users u ON u.id = o.user_id;
```

| Запись | Смысл |
|---|---|
| `orders o` | таблица `orders`, в запросе звать её `o` |
| `o.id` | столбец `id` из заказов |
| `u.full_name` | столбец `full_name` из клиентов |

**Зачем:** в обеих таблицах есть `id` — без префикса `o.` / `u.` PostgreSQL выдаст ошибку «столбец неоднозначен».

---

## Демо-данные для JOIN (миграция V12)

Миграция `V12__join_demo_seed.sql` добавляет строки, чтобы **NULL** было видно в клиенте (см. примеры 5–7).

| Кого добавили | Зачем |
|---|---|
| `join-demo-no-orders-1` … `4@example.com` | 4 клиента **без заказов** |
| `join-demo-single-order@example.com` | клиент с учебными заказами |

**Ограничение:** заказ без клиента в базе **невозможен** (`orders.user_id` + FK).  
Для **RIGHT JOIN** с NULL слева:

```sql
SELECT o.id AS order_id, u.full_name, u.email
FROM orders o
RIGHT JOIN users u ON u.id = o.user_id
WHERE u.email LIKE 'join-demo-no-orders-%'
ORDER BY u.email;
```

**Результат:** те же 4 строки — `order_id` = **NULL**, имя и email заполнены.

---

## Типичные ошибки на интервью

| Ошибка | Почему плохо |
|---|---|
| Забыли `ON` | непонятно, **как** склеивать таблицы |
| `SELECT id` при JOIN двух таблиц | обе имеют `id` — нужно `o.id` или `u.id` |
| Искать «клиентов без заказов» через INNER JOIN | INNER **не покажет** клиента без заказов |
| Путать LEFT и INNER | LEFT сохраняет **всех** слева; INNER — только пары |
| Думать, что JOIN «умножает клиентов» | одна строка клиента **повторяется** на каждый его заказ — это нормально |

### Мини-пример ошибки

```sql
-- ❌ неоднозначный id
SELECT id, full_name
FROM orders o
INNER JOIN users u ON u.id = o.user_id;

-- ✅
SELECT o.id AS order_id, u.full_name
FROM orders o
INNER JOIN users u ON u.id = o.user_id;
```

---

## Что попробовать самостоятельно

1. **Поддержка:** заказы со статусом `pending` — id заказа, имя клиента, сумма (`INNER JOIN`).
2. **Маркетинг:** топ-5 клиентов по **числу** заказов — `full_name` и количество (JOIN + `GROUP BY` из темы 03).
3. **CRM:** есть ли клиенты без заказов? Сколько их (`LEFT JOIN` + `WHERE o.id IS NULL` + `COUNT`).
4. **Сравнение:** один и тот же фильтр `u.id = 1` — сначала `INNER JOIN`, потом `LEFT JOIN`. Чем результат отличается?

---

*Следующий шаг: когда будете готовы — напишите в чат **«дай интервью по теме 4»**.*
