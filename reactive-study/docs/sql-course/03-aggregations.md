# Тема 03 — Агрегации: COUNT, SUM, AVG, GROUP BY, HAVING

> **Статус:** теория · **Следующий шаг:** изучить материал → написать в чат *«дай интервью по теме 3»*

---

## Оглавление

- [Что изучаем](#что-изучаем)
- [Таблица orders](#таблица-orders)
- [Зачем агрегации](#зачем-агрегации)
- [Агрегатные функции без GROUP BY](#агрегатные-функции-без-group-by)
- [COUNT — подсчёт](#count--подсчёт)
- [SUM — сумма](#sum--сумма)
- [AVG — среднее](#avg--среднее)
- [MIN и MAX](#min-и-max)
- [GROUP BY — группировка](#group-by--группировка)
- [Как GROUP BY работает по шагам](#как-group-by-работает-по-шагам)
- [HAVING — фильтр групп](#having--фильтр-групп)
- [WHERE и HAVING вместе](#where-и-having-вместе)
- [Порядок выполнения запроса](#порядок-выполнения-запроса)
- [Типичные ошибки](#типичные-ошибки)
- [Что попробовать самостоятельно](#что-попробовать-самостоятельно)

---

## Что изучаем

В темах 01–02 вы работали с **отдельными строками**. Теперь — **сводки по множеству строк**:

| Конструкция | Назначение |
|---|---|
| `COUNT` | сколько строк / значений |
| `SUM` | сумма |
| `AVG` | среднее арифметическое |
| `MIN` / `MAX` | минимум / максимум |
| `GROUP BY` | разбить строки на группы и посчитать по каждой |
| `HAVING` | отфильтровать **группы** после агрегации |

Примеры — на таблице `orders` (~100 000 заказов).  
Запросы — в **вашем SQL-клиенте**:

```sql
SET search_path TO reactive_study;
```

---

## Таблица orders

```text
 id           | bigint        | PK
 user_id      | bigint        | FK → users
 product_name | text          |
 amount       | numeric(12,2) | сумма заказа
 status       | text          | pending, processing, shipped, delivered, cancelled
 product_id   | int           | FK → products (может быть NULL)
 created_at   | timestamptz   |
 updated_at   | timestamptz   |
```

---

## Зачем агрегации

**Бизнес-вопросы без агрегаций** требуют просмотра тысяч строк:

- сколько всего заказов?
- какая общая выручка?
- сколько заказов в каждом статусе?

**Агрегатная функция** сворачивает много строк в **одно число** (или одну строку на группу).

**Источник:** https://www.postgresql.org/docs/current/functions-aggregate.html

EN:

> Aggregate functions compute a single result from a set of input values.

RU:

> Агрегатные функции вычисляют один результат из набора входных значений.

---

## Агрегатные функции без GROUP BY

Если `GROUP BY` **нет**, вся таблица (после `WHERE`) считается **одной группой** — результат **одна строка**.

```sql
SELECT COUNT(*) AS total_orders
FROM orders;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `SELECT COUNT(*)` | посчитать все строки |
| `AS total_orders` | псевдоним столбца результата |
| `FROM orders` | из таблицы заказов |
| **Результат** | одна строка с числом ~100 000 |

---

## COUNT — подсчёт

**Источник:** https://www.postgresql.org/docs/current/functions-aggregate.html

EN:

> `count`(`*`) → `bigint` — Computes the number of input rows.  
> `count`(`"any"`) → `bigint` — Computes the number of input rows in which the input value is not null.

RU:

> `count`(`*`) → `bigint` — вычисляет количество входных строк.  
> `count`(`"any"`) → `bigint` — вычисляет количество входных строк, в которых входное значение **не равно NULL**.

### Пример 1 — все заказы

```sql
SELECT COUNT(*) AS cnt
FROM orders;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `COUNT(*)` | считает **каждую** строку результата (NULL в столбцах не мешает) |
| **Результат** | общее число заказов |

### Пример 2 — только с заполненным product_id

```sql
SELECT COUNT(product_id) AS with_product
FROM orders;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `COUNT(product_id)` | считает только строки, где значение `product_id` **не NULL** |
| отличие от `COUNT(*)` | строки с `product_id IS NULL` **не** участвуют в счёте |

---

## SUM — сумма

**Источник:** https://www.postgresql.org/docs/current/functions-aggregate.html

EN:

> `sum`(`numeric`) → `numeric` — Computes the sum of the non-null input values.

RU:

> `sum`(`numeric`) → `numeric` — вычисляет сумму входных значений, **не равных NULL**.

```sql
SELECT SUM(amount) AS total_revenue
FROM orders;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `SUM(amount)` | складывает `amount` по всем строкам, где amount не NULL |
| **Результат** | одна строка — общая сумма всех заказов |

С фильтром (тема 02):

```sql
SELECT SUM(amount) AS delivered_revenue
FROM orders
WHERE status = 'delivered';
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `WHERE status = 'delivered'` | сначала отбираются только доставленные строки |
| `SUM(amount)` | сумма считается **только** по этим строкам |

---

## AVG — среднее

**Источник:** https://www.postgresql.org/docs/current/functions-aggregate.html

EN:

> Computes the average (arithmetic mean) of all the non-null input values.

RU:

> Вычисляет среднее арифметическое всех входных значений, **не равных NULL**.

```sql
SELECT AVG(amount) AS avg_check
FROM orders;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `AVG(amount)` | сумма всех не-NULL значений `amount`, делённая на их количество |
| **Результат** | средний чек по всем заказам |

---

## MIN и MAX

**Источник:** https://www.postgresql.org/docs/current/functions-aggregate.html

EN:

> `min` — Computes the minimum of the non-null input values.  
> `max` — Computes the maximum of the non-null input values.

RU:

> `min` — вычисляет **минимум** среди входных значений, не равных NULL.  
> `max` — вычисляет **максимум** среди входных значений, не равных NULL.

```sql
SELECT
    MIN(amount) AS smallest_order,
    MAX(amount) AS largest_order
FROM orders;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `MIN(amount)` | наименьшая сумма заказа среди не-NULL значений |
| `MAX(amount)` | наибольшая сумма заказа среди не-NULL значений |
| запятая между ними | два агрегата в одном `SELECT` — оба по **одной** группе (вся таблица) |

---

## GROUP BY — группировка

**Утверждение:** `GROUP BY` **разбивает** строки на группы с одинаковым значением указанного столбца (или столбцов). Затем агрегатная функция **отдельно** обрабатывает каждую группу и даёт **одно число на группу**. В итоге из многих строк получается **мало строк** — по одной на каждую уникальную комбинацию в `GROUP BY`.

**Аналогия:** на почте сортируют письма по ярлыкам. Все письма с ярлыком «pending» — в одну коробку, «processing» — в другую. Потом **в каждой коробке** считают количество писем. На выходе — не 100 000 писем, а **5 строк**: pending → 19 944, processing → 20 079, …

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> `GROUP BY` will condense into a single row all selected rows that share the same values for the grouped expressions.

RU:

> `GROUP BY` сворачивает в **одну строку** все выбранные строки, у которых **совпадают значения** группируемых выражений.

EN:

> Aggregate functions, if any are used, are computed across all rows making up each group, producing a separate value for each group.

RU:

> Агрегатные функции, если они используются, вычисляются **по всем строкам, составляющим каждую группу**, и дают **отдельное значение для каждой группы**.

### Пример — сколько заказов в каждом статусе

```sql
SELECT
    status,
    COUNT(*) AS order_count
FROM orders
GROUP BY status
ORDER BY order_count DESC;
```

**Разбор частей запроса:**

| Часть | Что делает |
|---|---|
| `FROM orders` | берём все строки таблицы заказов |
| `GROUP BY status` | делим их на группы по значению `status` |
| `status` в SELECT | в результат попадает **значение группы** (pending, shipped, …) |
| `COUNT(*) AS order_count` | для **каждой** группы — сколько строк в ней |
| `ORDER BY order_count DESC` | **после** группировки сортируем готовый результат (это **не** часть GROUP BY) |

---

## Как GROUP BY работает по шагам

Разберём тот же запрос «изнутри» на **упрощённом** фрагменте таблицы (в реальной базе строк ~100 000, логика та же).

### Шаг 0 — исходные строки (после FROM)

| id | status |
|---|---|
| 101 | pending |
| 102 | pending |
| 103 | processing |
| 104 | delivered |
| 105 | pending |

PostgreSQL **не** «читает запрос слева направо как текст». Он выполняет этапы в порядке, описанном [ниже](#порядок-выполнения-запроса): сначала источник, потом группировка, потом агрегаты, потом сортировка.

### Шаг 1 — GROUP BY status: разложить по «коробкам»

PostgreSQL смотрит на столбец `status` и **раскладывает строки** по одинаковым значениям:

```text
Группа «pending»:     id 101, 102, 105        → 3 строки
Группа «processing»:  id 103                   → 1 строка
Группа «delivered»:   id 104                   → 1 строка
```

На этом этапе **сортировки ещё нет** — только деление на группы.

### Шаг 2 — агрегат COUNT(*) по каждой группе

Для **каждой** коробки считается агрегат:

```text
pending     → COUNT(*) = 3
processing  → COUNT(*) = 1
delivered   → COUNT(*) = 1
```

### Шаг 3 — SELECT: собрать итоговую «узкую» таблицу

Из каждой группы получается **ровно одна строка** результата:

| status | order_count |
|---|---|
| pending | 3 |
| processing | 1 |
| delivered | 1 |

- столбец `status` — **ключ группы** (то, по чему делили);
- столбец `order_count` — **результат агрегата** `COUNT(*)` для этой группы.

### Шаг 4 — ORDER BY order_count DESC

Сортировка применяется **уже к готовому** результату (3 строки), а не к 100 000 исходных:

| status | order_count |
|---|---|
| pending | 3 |
| delivered | 1 |
| processing | 1 |

### Что важно запомнить

1. **GROUP BY** — только **деление на группы**, не сортировка.
2. **Агрегат** (`COUNT`, `SUM`, …) — **одно значение на группу**.
3. **SELECT** в результате — ключ группы + агрегаты.
4. **ORDER BY** — **последний** шаг в нашем примере; меняет порядок **строк результата**, не порядок группировки.

### Ещё примеры GROUP BY

**Выручка по статусам:**

```sql
SELECT
    status,
    COUNT(*)    AS cnt,
    SUM(amount) AS revenue,
    AVG(amount) AS avg_amount
FROM orders
GROUP BY status
ORDER BY revenue DESC;
```

Для **каждой** группы `status` считаются сразу три агрегата: число заказов, сумма, среднее.

**Топ-5 клиентов по числу заказов:**

```sql
SELECT
    user_id,
    COUNT(*) AS orders_count
FROM orders
GROUP BY user_id
ORDER BY orders_count DESC
LIMIT 5;
```

Группа = все заказы одного `user_id`; `COUNT(*)` — сколько их; `LIMIT 5` — только пять лидеров **после** сортировки.

### Правило SELECT + GROUP BY

В `SELECT` могут быть:

- столбцы из `GROUP BY`;
- агрегатные функции (`COUNT`, `SUM`, …).

**Нельзя** писать «обычный» столбец, которого нет в `GROUP BY` — в группе несколько строк, PostgreSQL не знает, **какое** значение `amount` показать.

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> When `GROUP BY` is present, or any aggregate functions are present, it is not valid for the `SELECT` list expressions to refer to ungrouped columns except within aggregate functions.

RU:

> Когда присутствует `GROUP BY` или любая агрегатная функция, выражения в списке `SELECT` **не могут** ссылаться на негруппированные столбцы, **кроме как** внутри агрегатных функций.

```sql
-- ОШИБКА: amount не в GROUP BY и не внутри агрегата
SELECT status, amount FROM orders GROUP BY status;
```

---

## HAVING — фильтр групп

**Утверждение:** `WHERE` отфильтровывает **отдельные строки до** группировки. `HAVING` отфильтровывает **готовые группы после** того, как агрегаты уже посчитаны.

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> `HAVING` eliminates group rows that do not satisfy the condition. `HAVING` is different from `WHERE`: `WHERE` filters individual rows before the application of `GROUP BY`, while `HAVING` filters group rows created by `GROUP BY`.

RU:

> `HAVING` отбрасывает строки групп, не удовлетворяющие условию. `HAVING` отличается от `WHERE`: `WHERE` фильтрует отдельные строки **до** применения `GROUP BY`, тогда как `HAVING` фильтрует строки групп, **сформированные** `GROUP BY`.

### Пример — статусы, где больше 20 000 заказов

```sql
SELECT
    status,
    COUNT(*) AS cnt
FROM orders
GROUP BY status
HAVING COUNT(*) > 20000
ORDER BY cnt DESC;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `GROUP BY status` | группы по статусу |
| `HAVING COUNT(*) > 20000` | оставить только группы, где заказов **больше 20 000** |
| почему не `WHERE COUNT(*)` | на этапе `WHERE` группы и агрегаты **ещё не существуют** |

### HAVING vs WHERE — наглядно

```sql
-- WHERE: сначала отсечь дешёвые заказы, потом группировать
SELECT status, AVG(amount) AS avg_amt
FROM orders
WHERE amount >= 100
GROUP BY status;
```

```sql
-- HAVING: сгруппировать все, потом оставить группы со средним >= 500
SELECT status, AVG(amount) AS avg_amt
FROM orders
GROUP BY status
HAVING AVG(amount) >= 500;
```

**Разбор первого запроса:**

| Часть | Что делает |
|---|---|
| `WHERE amount >= 100` | в группы **не попадают** строки с amount < 100 |
| `GROUP BY status` | среднее считается только по оставшимся строкам |

**Разбор второго:**

| Часть | Что делает |
|---|---|
| `GROUP BY status` | среднее по **всем** заказам каждого статуса |
| `HAVING AVG(amount) >= 500` | в результат попадают только группы со средним ≥ 500 |

---

## WHERE и HAVING вместе

```sql
SELECT
    user_id,
    COUNT(*)    AS cnt,
    SUM(amount) AS total_spent
FROM orders
WHERE status <> 'cancelled'
GROUP BY user_id
HAVING SUM(amount) > 5000
ORDER BY total_spent DESC
LIMIT 10;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `WHERE status <> 'cancelled'` | **строки:** отменённые заказы не участвуют |
| `GROUP BY user_id` | группа = заказы одного клиента (без cancelled) |
| `HAVING SUM(amount) > 5000` | **группы:** только клиенты с суммой > 5000 |
| `ORDER BY` / `LIMIT` | сортировка и обрезка **готового** результата |

---

## Порядок выполнения запроса

```text
FROM  →  WHERE  →  GROUP BY  →  HAVING  →  SELECT  →  ORDER BY  →  LIMIT
```

| Шаг | Что происходит |
|---|---|
| 1. `FROM` | взять строки из таблицы |
| 2. `WHERE` | отфильтровать **отдельные строки** |
| 3. `GROUP BY` | разбить оставшиеся строки на **группы** |
| 4. `HAVING` | отфильтровать **группы** (по агрегатам) |
| 5. `SELECT` | сформировать столбцы: ключ группы + значения агрегатов |
| 6. `ORDER BY` | отсортировать **строки результата** |
| 7. `LIMIT` | обрезать число строк результата |

---

## Типичные ошибки

| Неправильно | В чём ошибка | Правильно |
|---|---|---|
| `SELECT status, amount … GROUP BY status` | `amount` не в GROUP BY | `SELECT status, SUM(amount) … GROUP BY status` |
| `WHERE COUNT(*) > 10` | агрегат в `WHERE` на этапе, где групп ещё нет | `HAVING COUNT(*) > 10` |
| `SELECT user_id, COUNT(*)` без GROUP BY | агрегат + обычный столбец без группировки | добавить `GROUP BY user_id` |
| `HAVING status = 'pending'` при `GROUP BY user_id` | `status` в группе не один — значение неоднозначно | фильтровать в `WHERE` до группировки |
| ожидать `SUM` = 0 на пустом наборе | на пустом наборе `SUM` → **NULL** | `COALESCE(SUM(amount), 0)` (позже) |

**Источник:** https://www.postgresql.org/docs/current/functions-aggregate.html

EN:

> Except for `count`, these functions return a null value when no rows are selected. In particular, `sum` of no rows returns null, not zero as one might expect.

RU:

> За исключением `count`, эти функции возвращают значение NULL, когда не выбрано ни одной строки. В частности, `sum` по нулю строк возвращает NULL, а **не ноль**, как можно было бы ожидать.

---

## Что попробовать самостоятельно

1. **Склад:** сколько всего заказов в системе? Одно число.
2. **Финансы:** какая общая сумма (`amount`) по **доставленным** заказам (`delivered`)?
3. **Операции:** для каждого `status` — сколько заказов и какая сумма? Отсортировать по убыванию суммы.
4. **CRM:** у каких пяти `user_id` больше всего заказов?
5. **Аналитика:** показать только те статусы, где **средний** чек заказа **больше 1500**.

---

*Когда будете готовы — напишите в чат: **«дай интервью по теме 3»**.*
