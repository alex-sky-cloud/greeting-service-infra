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

**Задача:** в таблице `orders` ~100 000 строк. Нужно узнать, **сколько заказов в каждом статусе** — не перебирая все строки вручную, а получив **короткий отчёт**: 
  - одна строка на каждый статус.

Сначала — **запрос**, на котором всё разберём:

```sql
SELECT
    status,
    COUNT(*) AS order_count
FROM orders
GROUP BY status
ORDER BY order_count DESC;
```

---

### Что появится в результате

Пример (реальные числа из базы, порядок — по убыванию `order_count`):

| status | order_count |
|---|---|
| delivered | 20074 |
| processing | 20079 |
| pending | 19944 |
| … | … |

В таблице `orders` — **~100 000 строк**. В ответе — **~5 строк** (по числу разных `status`).  
Каждая строка ответа — это **сводка по одной корзине** (группе), а не один исходный заказ.

---

### Аналогия: корзины

1. PostgreSQL смотрит на столбец из `GROUP BY` — здесь `status`.
2. Строки с **одинаковым** `status` складывает в **одну корзину**.
3. Строки с **другим** `status` — в **другую** корзину.

```text
Корзина «pending»:     все заказы, где status = pending
Корзина «processing»:  все заказы, где status = processing
Корзина «shipped»:     …
```

В каждой корзине — **часть** строк исходной таблицы. Их объединяет одно: **одно и то же значение** `status`.

---

### По шагам — на 5 строках (тот же запрос)

#### Шаг 1 — FROM: исходная таблица

| id | status |
|---|---|
| 101 | pending |
| 102 | pending |
| 103 | processing |
| 104 | delivered |
| 105 | pending |

#### Шаг 2 — GROUP BY status: корзины

```text
Корзина «pending»:     id 101, 102, 105     → 3 строки внутри
Корзина «processing»:  id 103              → 1 строка
Корзина «delivered»:   id 104              → 1 строка
```

Только **раскладка по корзинам**. Сортировки пока **нет**.

#### Шаг 3 — COUNT(*) : посчитать в каждой корзине

`COUNT(*)` **заходит в корзину** и считает строки **только там**:

```text
Корзина «pending»:     COUNT(*) = 3
Корзина «processing»:  COUNT(*) = 1
Корзина «delivered»:   COUNT(*) = 1
```

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> Aggregate functions, if any are used, are computed across all rows making up each group, producing a separate value for each group.

RU:

> Агрегатные функции, если они используются, вычисляются **по всем строкам, составляющим каждую группу**, и дают **отдельное значение для каждой группы**.

**Одна корзина → одно число** от `COUNT(*)`. Три корзины → три числа.

#### Шаг 4 — SELECT: одна строка отчёта на корзину

| status (метка корзины) | order_count (результат COUNT) |
|---|---|
| pending | 3 |
| processing | 1 |
| delivered | 1 |

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> `GROUP BY` will condense into a single row all selected rows that share the same values for the grouped expressions.

RU:

> `GROUP BY` сворачивает в **одну строку** все выбранные строки, у которых **совпадают значения** группируемых выражений.

«Сворачивает» значит: **3 строки в корзине pending** → **1 строка** в ответе с `order_count = 3`.

#### Шаг 5 — ORDER BY order_count DESC

Сортировка **готового отчёта** (3 строки), не исходных 100 000:

| status | order_count |
|---|---|
| pending | 3 |
| delivered | 1 |
| processing | 1 |

---

### Разбор частей запроса (итого)

| Часть | Что делает |
|---|---|
| `FROM orders` | взять строки таблицы |
| `GROUP BY status` | разложить по корзинам с одинаковым `status` |
| `status` в SELECT | показать **метку** корзины |
| `COUNT(*) AS order_count` | посчитать строки **внутри каждой** корзины |
| `ORDER BY order_count DESC` | отсортировать строки **отчёта** (не часть GROUP BY) |

PostgreSQL **не** читает запрос «слева направо как текст». Порядок этапов — [ниже](#порядок-выполнения-запроса).

### Что важно запомнить

1. **GROUP BY** — **корзины** (группы), без сортировки.
2. **Агрегат** считается **в каждой корзине отдельно**.
3. **Ответ** — одна строка на корзину: `status` + результат агрегата.
4. **ORDER BY** — в конце, по **готовому** результату.

### Ещё примеры GROUP BY

---

#### Пример 1 — выручка по статусам (финансы и операции)

**Ситуация:** директор спрашивает: *«На каком этапе жизни заказа у нас „застряло“ больше всего денег?»* — не отдельные чеки, а **суммарная** выручка по каждому `status`.

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

**Пример результата** (данные из вашей базы):

| status | cnt | revenue | avg_amount |
|---|---:|---:|---:|
| processing | 20079 | 20 326 296.37 | 1012.32 |
| pending | 19944 | 20 204 593.26 | 1013.07 |
| cancelled | 20071 | 20 190 485.83 | 1005.95 |
| delivered | 20074 | 20 121 747.26 | 1002.38 |
| shipped | 19832 | 19 900 529.92 | 1003.46 |

**Разбор каждого столбца результата:**

| Столбец | Откуда | Что означает для **одной корзины** (одного `status`) |
|---|---|---|
| `status` | `GROUP BY status` | **Метка корзины** — общий статус всех заказов в группе |
| `cnt` | `COUNT(*)` | **Сколько строк** (заказов) лежит в этой корзине |
| `revenue` | `SUM(amount)` | **Общая сумма** поля `amount` по всем заказам этой корзины |
| `avg_amount` | `AVG(amount)` | **Средний чек** — среднее значение `amount` в этой корзине |

**Разбор `ORDER BY revenue DESC`:**

- `DESC` относится **только** к столбцу **`revenue`** (сразу слева от `DESC`);
- сортировка идёт по столбцу **`revenue`** уже **после** группировки;
- **первая строка** — статус с **наибольшей суммарной** выручкой;
- правила `ASC`/`DESC` и запятой в `ORDER BY` — см. [тема 01, раздел ORDER BY: DESC и несколько столбцов](01-select-basics.md#order-by-desc-и-несколько-столбцов).

**Зачем это в жизни:**

- много денег в **`pending`** / **`processing`** — много заказов **ещё не завершено**; риск задержек и потери клиентов;
- если бы **`cancelled`** был наверху — сигнал: теряем большие объёмы на отменах;
- сравнивая `revenue` и `cnt`, видно: много заказов или **дорогие** заказы тянут статус вверх.

---

#### Пример 2 — топ-5 клиентов по числу заказов (CRM)

**CRM** (Customer Relationship Management) — система **для управления взаимоотношениями** с клиентами: 
  - хранит данные о клиентах, сделках, заказах, звонках и коммуникациях.

В примере «топ-5 клиентов по числу заказов (**CRM**)» имеется в виду выбрать из **CRM** пять клиентов, **оформивших больше всего заказов**.

**Ситуация:** отдел лояльности ищет **самых активных** покупателей.

```sql
SELECT
    user_id,
    COUNT(*) AS orders_count
FROM orders
GROUP BY user_id
ORDER BY orders_count DESC
LIMIT 5;
```

**Пример результата:**

| user_id | orders_count |
|---:|---:|
| 2346 | 40 |
| 2639 | 37 |
| 459 | 36 |
| 498 | 36 |
| 1427 | 35 |

**Разбор:**

| Столбец | Откуда | Смысл |
|---|---|---|
| `user_id` | `GROUP BY user_id` | один клиент = одна корзина |
| `orders_count` | `COUNT(*)` | сколько заказов **у этого** клиента |
| `ORDER BY orders_count DESC` | — | сначала клиенты с **бóльшим** числом заказов |
| `LIMIT 5` | — | только **пять** строк отчёта |

**Зачем в жизни:** персональные скидки, VIP-программа, звонок ключевым клиентам.

### Правило: что можно писать в SELECT при GROUP BY

При `GROUP BY status` в **каждой корзине** лежит **не одна**, а **много** строк заказов. Например, в корзине `pending` — тысячи заказов с разными `id`, `amount`, `user_id`.

**Вопрос:** что показать в **одной строке отчёта** для корзины `pending`?

| id (в корзине pending) | amount (в корзине pending) |
|---|---|
| 2 | 1394.86 |
| 6 | 850.00 |
| 8 | 1848.02 |
| … | … |

PostgreSQL **не может** вывести **один** `id` или **один** `amount` — их в корзине **много**, и они **разные**.

---

#### Что **разрешено** в SELECT

| Тип в SELECT | Пример | Почему можно |
|---|---|---|
| Столбец из `GROUP BY` | `status` | Во всей корзине `status` **одинаковый** — значение одно |
| Агрегат по столбцам корзины | `COUNT(*)`, `SUM(amount)`, `MIN(id)` | Агрегат **сводит** много значений в **одно число** |

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> When `GROUP BY` is present, or any aggregate functions are present, it is not valid for the `SELECT` list expressions to refer to ungrouped columns except within aggregate functions.

RU:

> Когда присутствует `GROUP BY` или любая агрегатная функция, выражения в списке `SELECT` **не могут** ссылаться на негруппированные столбцы, **кроме как** внутри агрегатных функций.

**Перевод на практику:**

- **«Негруппированный столбец»** — столбец, которого **нет** в `GROUP BY` (например `id`, `amount`, `user_id` при `GROUP BY status`).
- **«Внутри агрегатной функции»** — столбец **внутри** `COUNT(...)`, `SUM(...)`, `MIN(...)` и т.п. Тогда PostgreSQL **не** выбирает «какую-то одну строку», а **считает** по всей корзине.

```sql
-- МОЖНО: status — в GROUP BY; amount — внутри агрегата SUM
SELECT
    status,
    SUM(amount) AS total
FROM orders
GROUP BY status;
```

```sql
-- НЕЛЬЗЯ: amount просто в SELECT — не в GROUP BY и не в агрегате
SELECT status, amount
FROM orders
GROUP BY status;
```

```sql
-- НЕЛЬЗЯ: id не в GROUP BY — в корзине pending много разных id
SELECT status, id
FROM orders
GROUP BY status;
```

```sql
-- МОЖНО: id «упакован» в агрегат — вернёт один id (минимальный в группе)
SELECT
    status,
    MIN(id) AS first_order_id
FROM orders
GROUP BY status;
```

**Кратко:** при `GROUP BY status` в `SELECT` — либо **`status`**, либо **агрегат** (`SUM(amount)`, `COUNT(*)`, `MIN(id)`, …). Поля вроде `id`, `amount`, `user_id` **без** агрегата — **ошибка**.

---

### GROUP BY по нескольким столбцам

**Ситуация:** поддержке нужно видеть не «сколько всего pending», а **сколько pending у каждого клиента** — детальнее, чем один столбец в `GROUP BY`.

```sql
SELECT
    status,
    user_id,
    COUNT(*) AS cnt
FROM orders
GROUP BY status, user_id
ORDER BY cnt DESC
LIMIT 10;
```

**Пример результата** (фрагмент):

| status | user_id | cnt |
|---|---:|---:|
| pending | 3166 | 16 |
| delivered | 4674 | 14 |
| shipped | 595 | 14 |

**Разбор:**

| Столбец | Смысл |
|---|---|
| `status`, `user_id` | **Два ключа корзины** — группа только если совпадают **оба** |
| `cnt` | число заказов у **этого** клиента в **этом** статусе |
| `ORDER BY cnt DESC` | пары (status, user_id) с наибольшим числом заказов — сверху |

**Зачем в жизни:** у клиента **3166** шестнадцать заказов в **`pending`** — повод проверить, не «завис» ли у него личный кабинет или оплата.

#### Как делятся корзины (упрощённо)

| id | status | user_id |
|---|---|---|
| 1 | pending | 10 |
| 2 | pending | 10 |
| 3 | pending | 20 |
| 4 | shipped | 10 |

```text
GROUP BY status          →  2 корзины: (pending), (shipped)
GROUP BY status, user_id →  3 корзины: (pending,10), (pending,20), (shipped,10)
```

При **двух** столбцах корзин **больше**, отчёт **детальнее** — строк в результате обычно **больше**, чем при группировке только по `status`.

#### Подводные камни

| Ситуация | Что происходит |
|---|---|
| Добавили столбец в `GROUP BY` | групп **больше**, сводка **мельче** (ближе к исходным строкам) |
| Забыли столбец в `GROUP BY`, но указали в `SELECT` | **ошибка** — для нового столбца в корзине может быть несколько разных значений |
| `GROUP BY status`, а в SELECT `status, user_id` | **ошибка** — в корзине одного `status` много разных `user_id` | добавить `user_id` в `GROUP BY` или агрегат |
| `GROUP BY status, user_id`, а в SELECT только `status` | **не ошибка** — запрос выполнится | много строк с одинаковым `status`, `user_id` в ответе **нет** — отчёт бессмысленный; лучше вывести оба поля |
| Очень много столбцов в `GROUP BY` | почти «по одной строке на заказ» — агрегация теряет смысл |

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> An expression used inside a grouping_element can be an input column name … grouping_element [, …]

RU:

> В `GROUP BY` можно перечислить **несколько** grouping_element через запятую — группа определяется **совпадением всех** перечисленных столбцов.

PostgreSQL **не «угадывает»** лишние столбцы: всё, что в `SELECT` **не** обёрнуто в агрегат, должно **полностью** входить в список `GROUP BY`.

---

## HAVING — фильтр групп

**Ситуация:** из отчёта по статусам нужны **не все** группы, а только «крупные» — где заказов **больше 20 000**. Отсечь мелкие группы **до** сортировки нельзя через `WHERE` — там ещё нет результата агрегатной функции, **например**, мы используем `COUNT(*)`.

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> `HAVING` eliminates group rows that do not satisfy the condition. `HAVING` is different from `WHERE`: `WHERE` filters individual rows before the application of `GROUP BY`, while `HAVING` filters group rows created by `GROUP BY`.

RU:

> `HAVING` отбрасывает строки групп, не удовлетворяющие условию.
> 
>- `HAVING` отличается от `WHERE`: 
>   - `WHERE` фильтрует отдельные строки **до** применения `GROUP BY`, тогда как 
>   - `HAVING` фильтрует строки групп, **сформированные** `GROUP BY`.

### Пример — только «крупные» статусы по числу заказов

```sql
SELECT
    status,
    COUNT(*) AS cnt
FROM orders
GROUP BY status
HAVING COUNT(*) > 20000
ORDER BY cnt DESC;
```

**Пример результата:**

| status | cnt |
|---|---:|
| processing | 20079 |
| delivered | 20074 |
| cancelled | 20071 |

*(статусы с cnt ≤ 20 000, например `shipped` / `pending`, **не попали** в ответ)*

---

#### Порядок выполнения — почему в HAVING пишут `COUNT(*)`, а не `cnt`

PostgreSQL **не** читает запрос «сверху вниз как текст». Логический порядок:

```text
FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT
         ↑                      ↑         ↑
    отдельные строки      фильтр      здесь появляется
                         корзин       псевдоним cnt
```

| Шаг | Что уже есть | Чего **ещё нет** |
|---|---|---|
| 1–3. `FROM` … `GROUP BY` | корзины по `status`, в каждой посчитан «сырой» `COUNT(*)` | псевдонима **`cnt`** |
| 4. **`HAVING`** | агрегаты по корзинам (`COUNT(*)`, `SUM(...)`, …) | **`cnt`** — имя из `SELECT` **ещё не создано** |
| 5. **`SELECT`** | формируется результат: `status`, **`cnt`** | — |
| 6. **`ORDER BY cnt`** | псевдоним **`cnt` уже есть** | можно сортировать по имени |

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> An output column's name can be used to refer to the column's value in `ORDER BY` and `GROUP BY` clauses, but not in the `WHERE` or `HAVING` clauses; there you must write out the expression instead.

RU:

> Имя столбца результата можно использовать в `ORDER BY` и `GROUP BY`, но **не** в `WHERE` или `HAVING` — там нужно писать **само выражение** (например `COUNT(*)`, а не псевдоним `cnt`).

---

#### Подводный камень: `HAVING cnt > 20000` — ошибка

Хочется написать короче — по имени из `SELECT`:

```sql
-- ОШИБКА
SELECT status, COUNT(*) AS cnt
FROM orders
GROUP BY status
HAVING cnt > 20000;
```

PostgreSQL ответит:

```text
ERROR: column "cnt" does not exist
```

**Почему:** на шаге `HAVING` псевдоним **`cnt` ещё не существует** — он появится только на шаге `SELECT`, **после** `HAVING`.

**Правильно в HAVING — полное выражение агрегата:**

```sql
HAVING COUNT(*) > 20000
-- или, если условие сложное:
HAVING SUM(amount) > 1000000
```

**В `ORDER BY` псевдоним уже можно** — он идёт **после** `SELECT`:

```sql
ORDER BY cnt DESC   -- OK
```

---

#### Разбор запроса по шагам

| Шаг | Что происходит |
|---|---|
| `GROUP BY status` | корзины по статусам, в каждой свой `COUNT(*)` |
| `HAVING COUNT(*) > 20000` | выбросить корзины, где заказов **≤ 20 000** (`shipped`, `pending` отсеются) |
| `SELECT status, COUNT(*) AS cnt` | одна строка отчёта на оставшуюся корзину; число → в столбец **`cnt`** |
| `ORDER BY cnt DESC` | сортировка **готового** отчёта по убыванию `cnt` |

**Зачем в жизни:** смотреть нагрузку только на **массовые** этапы обработки, не отвлекаясь на редкие статусы с малым числом заказов.

---

### HAVING vs WHERE — два разных уровня фильтра

#### Запрос A — WHERE (фильтр **строк** до корзин)

**Ситуация:** «Средний чек по статусам, но **не учитывать** мелкие заказы дешевле 100».

```sql
SELECT
    status,
    AVG(amount) AS avg_amt
FROM orders
WHERE amount >= 100
GROUP BY status;
```

**Пример результата** (фрагмент):

| status | avg_amt |
|---|---:|
| pending | 1060.39 |
| processing | 1057.54 |

| Элемент | Смысл |
|---|---|
| `WHERE amount >= 100` | в корзины **не попадают** строки с amount < 100 |
| `AVG(amount)` | среднее считается **только** по оставшимся заказам в каждой корзине |

**Зачем:** убрать «шум» от мелких позиций перед расчётом среднего.

#### Запрос B — HAVING (фильтр **корзин** после агрегата)

**Ситуация:** «Показать статусы, где **средний** чек по группе не ниже 500» (среднее уже посчитано).

```sql
SELECT
    status,
    AVG(amount) AS avg_amt
FROM orders
GROUP BY status
HAVING AVG(amount) >= 500;
```

**Пример результата:** все пять статусов (в базе у каждого avg ≈ 1000–1013 — условие `>= 500` всем подходит).

| Элемент | Смысл |
|---|---|
| `GROUP BY` + `AVG` | сначала средний чек **по каждой** корзине |
| `HAVING AVG(amount) >= 500` | в ответе только корзины, где это среднее **≥ 500** |

**Зачем:** отсечь статусы с **низким** средним чеком (например, если бы pending тянулся мелочью).

**Запомнить:** `WHERE` — **какие заказы** участвуют; `HAVING` — **какие группы** показать в отчёте.

---

## WHERE и HAVING вместе

**Ситуация:** маркетинг хочет **топ клиентов по сумме покупок**, но:

- не считать **отменённые** заказы (`WHERE`);
- показывать только тех, кто потратил **> 5000** (`HAVING`).

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

**Пример результата** (топ-3):

| user_id | cnt | total_spent |
|---:|---:|---:|
| 2280 | 29 | 37 526.16 |
| 1427 | 30 | 36 009.25 |
| 4637 | 28 | 35 056.38 |

**Разбор:**

| Столбец / шаг | Смысл |
|---|---|
| `WHERE status <> 'cancelled'` | в корзины клиентов **не кладём** отменённые заказы |
| `GROUP BY user_id` | одна корзина = все **учтённые** заказы одного клиента |
| `cnt` | сколько таких заказов у клиента |
| `total_spent` | **SUM(amount)** по корзине — сколько клиент потратил |
| `HAVING SUM(amount) > 5000` | клиенты с суммой **≤ 5000** **выбрасываются** из отчёта |
| `ORDER BY total_spent DESC` | сверху — **крупнейшие** по сумме |
| `LIMIT 10` | только десять лидеров |

**Зачем в жизни:** список для VIP-рассылки или персонального менеджера — платят много и без учёта отмен.

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
| `HAVING cnt > 10` при `COUNT(*) AS cnt` | псевдоним `cnt` в HAVING **ещё не существует** | `HAVING COUNT(*) > 10`; в `ORDER BY` — `cnt` можно |
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
