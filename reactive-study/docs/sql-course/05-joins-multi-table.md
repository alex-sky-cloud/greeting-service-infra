# Тема 05 — JOIN нескольких таблиц

> **Статус:** теория · **Следующий шаг:** изучить материал → написать в чат *«дай интервью по теме 5»*

---

## Оглавление

- [Что изучаем](#что-изучаем)
- [Схема: цепочка таблиц магазина](#схема-цепочка-таблиц-магазина)
- [JOIN трёх таблиц — orders → products → categories](#join-трёх-таблиц--orders--products--categories)
- [LEFT JOIN по цепочке — заказ без товара в каталоге](#left-join-по-цепочке--заказ-без-товара-в-каталоге)
- [JOIN четырёх таблиц — клиент + заказ + товар + категория](#join-четырёх-таблиц--клиент--заказ--товар--категория)
- [Порядок JOIN и фильтры](#порядок-join-и-фильтры)
- [JOIN + GROUP BY — сводки для бизнеса](#join--group-by--сводки-для-бизнеса)
- [Что спрашивают на интервью](#что-спрашивают-на-интервью)
- [Типичные ошибки](#типичные-ошибки)
- [Что попробовать самостоятельно](#что-попробовать-самостоятельно)

---

## Что изучаем

В теме 04 вы соединяли **две** таблицы (`users` + `orders`).  
В реальном отчёте нужно **3–4 источника** сразу:

| Таблица | Что даёт для отчёта |
|---|---|
| `orders` | заказ, сумма, статус |
| `products` | SKU, название, цена в каталоге |
| `product_categories` | «Электроника», «Офис», … |
| `users` | имя и email покупателя |

**Навык темы 05:** строить **цепочку JOIN** — каждый следующий JOIN подтягивает таблицу по **внешнему ключу** предыдущей.

На интервью ждут:

- цепочку `INNER` / `LEFT JOIN` на 3+ таблицах;
- понимание, **куда деваются строки**, если связь оборвана (`product_id IS NULL`);
- отличие «склеить» (`ON`) от «отфильтровать» (`WHERE`);
- JOIN + `GROUP BY` (сводка по категориям).

```sql
SET search_path TO reactive_study;
```

---

## Схема: цепочка таблиц магазина

```text
users                orders                 products           product_categories
┌────┬──────────┐    ┌────┬───────── ─┐    ┌────┬────────────┐   ┌────┬────────────┐
│ id │ full_name│◄───│user│ product_id│───►│ id │ category_id│──►│ id │ name       │
└────┘          │    │ _id│ (может    │    │ sku│ name       │   └────┴────────────┘
                │    │    │ быть NULL)│    └────┴────────────┘
                └────┴────┴───────────┘
```

| Связь | Поля |
|---|---|
| клиент → заказ | `users.id` = `orders.user_id` |
| заказ → товар | `orders.product_id` = `products.id` |
| товар → категория | `products.category_id` = `product_categories.id` |

**Важно для интервью:** `orders.product_id` **может быть NULL** — заказ оформлен «свободным текстом» в `product_name`, без карточки в каталоге (учебные строки `JOIN-DEMO-NO-PRODUCT-*`).

### Что такое SKU в нашей базе

**SKU** (Stock Keeping Unit) — **артикул товара**, уникальный код в каталоге.  
В Reactive Shop это столбец **`products.sku`** (тип `text`, уникальный).

| products.id | products.sku | products.name | products.category_id |
|---|---|---|---|
| 1 | SKU-00001 | Product 1 | 1 |
| 10 | SKU-00010 | Product 10 | 10 |
| 20 | SKU-00020 | Product 20 | 10 |

Связь с заказом:

```text
orders.product_id = 10  →  products.id = 10  →  products.sku = 'SKU-00010'
```

В отчётах **`p.sku`** — это «артикул из каталога»; **`o.product_name`** — текст на заказе (может отличаться, особенно если `product_id` пустой).

---

## JOIN трёх таблиц — orders → products → categories

**Источник:** https://www.postgresql.org/docs/current/queries-table-expressions.html#QUERIES-JOIN

EN:

> Joins of all types can be chained together, or nested: either or both T1 and T2 can be joined tables. Parentheses can be used around JOIN clauses to control the join order. In the absence of parentheses, JOIN clauses nest left-to-right.

RU:

> Соединения всех типов можно выстраивать в цепочку или вкладывать друг в друга. Скобки вокруг JOIN задают порядок. Без скобок JOIN выполняются **слева направо**.

### Пример 1 — отчёт по категории для одного заказа

**Задача:** по заказу **100001** показать: id заказа, SKU товара, название товара, **категорию**, сумму.  
Нужны только заказы, где товар **привязан** к каталогу.

**Исходные фрагменты:**

| orders.id | product_id | amount |
|---|---|---|
| 100001 | 10 | 42.00 |

| products.id | sku | name | category_id |
|---|---|---|---|
| 10 | SKU-00010 | Product 10 | 10 |

| product_categories.id | name |
|---|---|
| 10 | Офис |

**Запрос:**

```sql
SELECT o.id AS order_id,
       p.sku,
       p.name AS product_name,
       pc.name AS category_name,
       o.amount
FROM orders o
INNER JOIN products p ON p.id = o.product_id
INNER JOIN product_categories pc ON pc.id = p.category_id
WHERE o.id = 100001;
```

**Разбор:**

| Шаг | JOIN | Условие |
|---|---|---|
| 1 | `orders` + `products` | `p.id = o.product_id` |
| 2 | результат + `product_categories` | `pc.id = p.category_id` |

**Результат:**

| order_id | sku | product_name | category_name | amount |
|---|---|---|---|---|
| 100001 | SKU-00010 | Product 10 | Офис | 42.00 |

**Вывод:** три таблицы → одна строка «заказ + товар + категория».

---

### Пример 2 — INNER отсекает заказы без каталога

**Задача:** те же столбцы для заказов **100001** (есть товар) и **100002** (`product_id` NULL).

**Запрос:**

```sql
SELECT o.id AS order_id,
       o.product_name,
       p.sku,
       pc.name AS category_name
FROM orders o
INNER JOIN products p ON p.id = o.product_id
INNER JOIN product_categories pc ON pc.id = p.category_id
WHERE o.id IN (100001, 100002)
ORDER BY o.id;
```

**Результат:**

| order_id | product_name | sku | category_name |
|---|---|---|---|
| 100001 | JOIN-DEMO-SINGLE-ORDER | SKU-00010 | Офис |

**Вывод:** заказ **100002 исчез** — для INNER нет строки в `products` → цепочка обрывается.  
На интервью: «покажи **все** заказы, даже без товара» → нужен **LEFT JOIN** (пример 3).

---

## LEFT JOIN по цепочке — заказ без товара в каталоге

**Задача:** каталогу нужен список заказов **включая** оформленные без `product_id`.  
Если товара или категории нет — справа **NULL**, но **строка заказа остаётся**.

**Запрос:**

```sql
SELECT o.id AS order_id,
       o.product_name,
       p.sku,
       pc.name AS category_name
FROM orders o
LEFT JOIN products p ON p.id = o.product_id
LEFT JOIN product_categories pc ON pc.id = p.category_id
WHERE o.id IN (100001, 100002)
ORDER BY o.id;
```

**Результат:**

| order_id | product_name | sku | category_name |
|---|---|---|---|
| 100001 | JOIN-DEMO-SINGLE-ORDER | SKU-00010 | Офис |
| 100002 | JOIN-DEMO-NO-PRODUCT-1 | **NULL** | **NULL** |

**Пошагово:**

| order_id | LEFT JOIN products | LEFT JOIN categories |
|---|---|---|
| 100001 | Product 10 найден | категория «Офис» |
| 100002 | `product_id` NULL → p.* NULL | pc.* тоже NULL |

**Интервью-вопрос:** почему при NULL `product_id` категория тоже NULL?  
**Ответ:** второй JOIN идёт от `p.category_id`; если `p` не найден, ссылаться не на что.

---

## JOIN четырёх таблиц — клиент + заказ + товар + категория

**Задача (классика):** «Покажи **кто** купил, **что**, из **какой категории**, на **какую сумму**».

**Запрос:**

```sql
SELECT o.id AS order_id,
       u.full_name,
       p.sku,
       pc.name AS category_name,
       o.amount
FROM orders o
INNER JOIN users u ON u.id = o.user_id
INNER JOIN products p ON p.id = o.product_id
INNER JOIN product_categories pc ON pc.id = p.category_id
WHERE o.id = 100001;
```

**Результат:**

| order_id | full_name | sku | category_name | amount |
|---|---|---|---|---|
| 100001 | JOIN Demo Single Order | SKU-00010 | Офис | 42.00 |

**Разбор порядка JOIN (слева направо):**

```text
orders ──► users        (кто купил)
   └──► products       (что в каталоге)
           └──► product_categories  (отдел магазина)
```

Порядок `users` и `products` **можно менять** — главное, чтобы каждый `ON` был корректен.  
На интервью часто пишут сначала `orders`, потом «ветки» к клиенту и товару.

---

## Порядок JOIN и фильтры

| Конструкция | Роль |
|---|---|
| `ON` | **как** связаны таблицы (равенство ключей) |
| `WHERE` | **какие строки** оставить после всех JOIN |
| `JOIN` без `ON` | **ошибка** (кроме `CROSS JOIN`) |

**Источник:** https://www.postgresql.org/docs/current/queries-table-expressions.html

EN:

> If more than one table reference is listed in the FROM clause, the tables are cross-joined (that is, the Cartesian product of their rows is formed; see below).

RU:

> Если в FROM перечислено несколько таблиц через запятую, выполняется cross-join (декартово произведение строк).

### Пример 4 — фильтр по категории, не по ключу

**Задача:** все заказы категории **«Электроника»** — id заказа, SKU, сумма (5 строк для просмотра).

**Запрос:**

```sql
SELECT o.id AS order_id,
       p.sku,
       o.amount
FROM orders o
INNER JOIN products p ON p.id = o.product_id
INNER JOIN product_categories pc ON pc.id = p.category_id
WHERE pc.name = 'Электроника'
ORDER BY o.id
LIMIT 5;
```

**Разбор:** «Электроника» — свойство **категории** → фильтр в **`WHERE`**, не в `ON`.  
В `ON` оставляйте только **связи ключей**.

---

## JOIN + GROUP BY — сводки для бизнеса

**Задача:** топ **5 категорий** по **числу заказов** (не по выручке).

**Запрос:**

```sql
SELECT pc.name AS category_name,
       COUNT(o.id) AS order_count
FROM orders o
INNER JOIN products p ON p.id = o.product_id
INNER JOIN product_categories pc ON pc.id = p.category_id
GROUP BY pc.name
ORDER BY order_count DESC
LIMIT 5;
```

**Результат (фрагмент на вашей базе):**

| category_name | order_count |
|---|---|
| Офис | 10140 |
| Книги | 10078 |
| Здоровье | 10067 |
| Электроника | 10040 |
| Спорт | 10009 |

**Разбор:**

| Часть | Смысл |
|---|---|
| 3× JOIN | заказ → товар → категория |
| `GROUP BY pc.name` | одна строка на категорию |
| `COUNT(o.id)` | сколько заказов попало в категорию |
| INNER JOIN | заказы **без** `product_id` **не участвуют** |

**Интервью:** «как включить заказы без товара в отчёт?» → `LEFT JOIN products` и отдельная группа «Без категории» или фильтр `WHERE p.id IS NULL`.

---

## Что спрашивают на интервью

| Вопрос | Короткий ответ |
|---|---|
| Склеить 3 таблицы | цепочка JOIN, каждый `ON` — по FK |
| Заказ без `product_id` | `LEFT JOIN products`; sku и category = NULL |
| INNER vs LEFT на 3 таблицах | INNER — только полные цепочки; LEFT — сохраняет левую таблицу |
| Порядок JOIN | слева направо; скобки меняют порядок |
| Забыли JOIN между таблицами | декартово произведение (взрыв строк) |
| Фильтр по категории | `WHERE pc.name = …` после JOIN |
| Сводка по категориям | JOIN + `GROUP BY` + `COUNT` |
| 4 таблицы | `orders` + `users` + `products` + `product_categories` |

---

## Типичные ошибки

| Ошибка | Последствие |
|---|---|
| `JOIN products ON products.id = products.id` | бессмыслица / все строки |
| `ON pc.id = o.product_id` | **неверный ключ** — категория ≠ товар |
| INNER JOIN, нужны заказы без товара | строки **пропадают** |
| `SELECT sku` при JOIN orders + products | неоднозначность → `p.sku` |
| Две таблицы в `FROM` через запятую без `WHERE` | **Cartesian product** |

### Мини-пример декартова произведения

```sql
-- ❌ 100 000 × 200 = 20 000 000 строк
SELECT COUNT(*)
FROM orders, products;

-- ✅ только пары «заказ ↔ его товар»
SELECT COUNT(*)
FROM orders o
INNER JOIN products p ON p.id = o.product_id;
```

---

## Что попробовать самостоятельно

1. Заказ **100003** (`JOIN-DEMO-NO-PRODUCT-2`) — LEFT JOIN: показать `product_name` из заказа и SKU из каталога (ожидается NULL).
2. Все заказы категории **«Книги»** — id, SKU, amount — 5 строк.
3. Полный отчёт по заказу **1** (bulk): имя клиента, SKU, категория, сумма.
4. Сколько заказов **без** привязки к каталогу (`product_id IS NULL`)?
5. Топ **3** категории по **сумме** `amount` (JOIN + `GROUP BY` + `SUM`).

---

*Следующий шаг: когда будете готовы — напишите в чат **«дай интервью по теме 5»**.*
