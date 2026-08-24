# Тема 04 — JOIN: INNER JOIN и LEFT JOIN

> **Статус:** теория · **Следующий шаг:** изучить материал → написать в чат *«дай интервью по теме 4»*

---

## Оглавление

- [Что изучаем](#что-изучаем)
- [Зачем JOIN — история из двух таблиц](#зачем-join--история-из-двух-таблиц)
- [Как таблицы связаны в Reactive Shop](#как-таблицы-связаны-in-reactive-shop)
- [INNER JOIN — только пары «клиент ↔ заказ»](#inner-join--только-пары-клиент--заказ)
- [LEFT JOIN — все клиенты, даже без заказов](#left-join--все-клиенты-даже-без-заказов)
- [INNER vs LEFT — когда что выбирать](#inner-vs-left--когда-что-выбирать)
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

## INNER JOIN — только пары «клиент ↔ заказ»

**Ситуация:** менеджеру нужен отчёт «заказ (**order**) + имя покупателя(**name**)» — только по **существующим** заказам с **известным** клиентом.

**INNER JOIN** оставляет строки, где условие в `ON` **выполнилось**. Нет клиента для заказа или нет заказа у клиента — такая пара **не попадает** в результат.

**Источник:** https://www.postgresql.org/docs/current/queries-table-expressions.html#QUERIES-JOIN

EN:

> `INNER JOIN` — For each row R1 of T1, the joined table has a row for each row in T2 that satisfies the join condition with R1.

RU:

> `INNER JOIN` — для каждой строки R1 из T1 соединённая таблица содержит строку для каждой строки из T2, которая удовлетворяет условию соединения с R1.

### Пример 1 — заказы Ann Smith (id = 1)

```sql
SELECT o.id AS order_id,
       u.full_name,
       o.amount,
       o.status
FROM orders o
INNER JOIN users u ON u.id = o.user_id
WHERE u.id = 1
ORDER BY o.id
LIMIT 5;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `FROM orders o` | основа — таблица заказов; `o` — короткое имя (псевдоним) |
| `INNER JOIN users u` | подтянуть таблицу клиентов |
| `ON u.id = o.user_id` | **склеить:** id клиента = user_id в заказе |
| `WHERE u.id = 1` | только заказы Ann |
| `LIMIT 5` | первые 5 для просмотра |

**Применение:** экран поддержки, чек «кто заказал», выгрузка для бухгалтерии.

### Пример 2 — что «отсекается» INNER JOIN

Упрощённая картинка (как в документации PostgreSQL):

```text
Клиенты          Заказы           INNER JOIN (по id = user_id)
 id=1 Ann    +    user_id=1    →   Ann + заказ ✓
 id=2 Bob    +    user_id=3    →   нет пары для Bob с этим заказом *
 id=3 Carol  +    user_id=2    →   Bob + заказ ✓ (Carol не участвует)

* В реальной базе у каждого заказа есть user_id; «осиротевший» заказ
  без клиента здесь не бывает — но INNER всё равно не покажет
  клиента Bob, если у него нет заказа в выборке.
```

**Главное:** INNER JOIN отвечает на вопрос «покажи **только те** комбинации, где **обе** стороны нашлись».

---

## LEFT JOIN — все клиенты, даже без заказов

**Ситуация:** отдел маркетинга хочет список **всех** зарегистрированных клиентов и понять, делали ли они заказ. Клиенты **без** заказов тоже должны быть в отчёте.

**LEFT JOIN** (то же, что `LEFT OUTER JOIN`):

1. Сначала работает как INNER JOIN.
2. Потом добавляет **каждую** строку из **левой** таблицы, для которой **не нашлось** пары справа — столбцы справа заполняются **NULL**.

**Источник:** https://www.postgresql.org/docs/current/queries-table-expressions.html#QUERIES-JOIN

EN:

> `LEFT OUTER JOIN` — First, an inner join is performed. Then, for each row in T1 that does not satisfy the join condition with any row in T2, a joined row is added with null values in columns of T2. Thus, the joined table always has at least one row for each row in T1.

RU:

> `LEFT OUTER JOIN` — сначала выполняется inner join. Затем для каждой строки в T1, не удовлетворившей условию соединения ни с одной строкой в T2, добавляется соединённая строка со значениями NULL в столбцах T2. Таким образом, соединённая таблица всегда содержит хотя бы одну строку для каждой строки в T1.

### Пример 3 — все клиенты и их заказы (фрагмент)

```sql
SELECT u.id AS user_id,
       u.full_name,
       o.id AS order_id,
       o.amount
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
WHERE u.id <= 3
ORDER BY u.id, o.id
LIMIT 10;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `FROM users u` | **слева** — клиенты (главная таблица отчёта) |
| `LEFT JOIN orders o` | справа — заказы; если нет — NULL |
| `ON o.user_id = u.id` | условие склейки |
| `u.id <= 3` | три первых демо-клиента для наглядности |

У Ann (id=1) может быть **несколько** строк — по одной на каждый заказ. У клиента без заказов была бы **одна** строка: имя есть, `order_id` и `amount` — **NULL**.

### Пример 4 — классика интервью: клиенты без заказов

**Ситуация:** CRM ищет «спящих» пользователей для re-activation рассылки.

```sql
SELECT u.id,
       u.full_name,
       u.email
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
WHERE u.email LIKE 'join-demo-no-orders-%'
ORDER BY u.email;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `LEFT JOIN` | все клиенты + попытка найти заказ |
| `WHERE u.email LIKE 'join-demo-no-orders-%'` | четыре учебных клиента **без заказов** (миграция V12) |
| **Результат** | 4 строки: имя и email есть, столбцы заказа — **NULL** |

> Демо-клиенты добавлены специально: в bulk-данных у каждого из 5 000 пользователей был хотя бы один заказ, и NULL на практике не видно было.

> **Важно:** `WHERE o.id IS NULL` **после** LEFT JOIN — частый паттерн на собеседованиях. С INNER JOIN такой трюк **не работает** (строк без заказа просто нет).

---

## INNER vs LEFT — когда что выбирать

| Вопрос бизнеса | JOIN |
|---|---|
| «Покажи заказы **с именами** клиентов» | `INNER JOIN` |
| «Покажи **всех** клиентов и их заказы, если есть» | `LEFT JOIN` |
| «Кто зарегистрировался, но **не покупал**?» | `LEFT JOIN` + `WHERE o.id IS NULL` |
| «Сумма выручки только по **реальным** заказам с клиентом» | `INNER JOIN` (или только `orders`, если FK гарантирует клиента) |

**Правило большого пальца:**

- **INNER** — «мне нужны только **совпадения**».
- **LEFT** — «мне важна **левая** таблица целиком; правая — опциональна».

`RIGHT JOIN` и `FULL JOIN` существуют, но на практике их заменяют перестановкой таблиц и `LEFT JOIN`. В этом курсе достаточно **INNER** и **LEFT**.

---

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

### Пример 5 — INNER JOIN + фильтр по статусу

**Ситуация:** складу нужны **доставленные** заказы с email клиента для SMS-опроса.

```sql
SELECT o.id,
       u.email,
       o.amount
FROM orders o
INNER JOIN users u ON u.id = o.user_id
WHERE o.status = 'delivered'
ORDER BY o.id
LIMIT 5;
```

**Разбор:**

| Шаг | Что происходит |
|---|---|
| 1. JOIN | к каждому заказу подставляется email клиента |
| 2. WHERE | только `delivered` |
| 3. SELECT | id заказа, email, сумма |

Фильтр по `status` — свойство **заказа**, поэтому логично в `WHERE`, а не в `ON`.  
(В `ON` тоже можно, но для обучения держите в `ON` только **связь таблиц**.)

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

Миграция `V12__join_demo_seed.sql` добавляет **учебные строки**, чтобы NULL при JOIN было видно в клиенте.

| Кого добавили | Зачем |
|---|---|
| `join-demo-no-orders-1` … `4@example.com` | 4 клиента **без заказов** → LEFT JOIN: справа NULL |
| `join-demo-single-order@example.com` | 1 клиент с несколькими учебными заказами → INNER JOIN |
| Заказы `JOIN-DEMO-NO-PRODUCT-*` | `product_id IS NULL` (для темы 05) |
| Заказы `JOIN-DEMO-NO-PAYMENT-*` | нет строк в `payment_attempts` |
| Заказы `JOIN-DEMO-NO-EVENT-*` | нет строк в `order_status_events` |

**Ограничение схемы:** `orders.user_id` обязателен и ссылается на `users.id` — заказа «без клиента» в базе **не бывает**.  
Для **RIGHT JOIN** с NULL слева используйте перестановку:

```sql
SELECT o.id AS order_id,
       u.full_name,
       u.email
FROM orders o
RIGHT JOIN users u ON u.id = o.user_id
WHERE u.email LIKE 'join-demo-no-orders-%'
ORDER BY u.email;
```

Здесь **справа** — все выбранные клиенты; у четырёх `order_id` будет **NULL** (то же по смыслу, что LEFT JOIN, но «главная» таблица справа).

### Проверка LEFT JOIN — NULL видно явно

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

Ожидаемый результат: **4 строки**, столбцы `order_id` и `amount` — пустые (NULL).

### Проверка INNER JOIN — «сирот» нет

```sql
SELECT u.email, o.product_name
FROM users u
INNER JOIN orders o ON o.user_id = u.id
WHERE u.email LIKE 'join-demo-no-orders-%';
```

Ожидаемый результат: **0 строк** (у этих клиентов нет заказов — INNER их отбросил).

Если миграция ещё не применена — **запустите приложение reactive-study** (Flyway выполнит V12) или попросите ассистента применить миграцию.

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
