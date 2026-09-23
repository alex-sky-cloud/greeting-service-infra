# Как SQL-движок выполняет запрос с window-функцией (пошагово)

## Оглавление

- [Исходный запрос и данные](#исходный-запрос-и-данные)
- [Шаг 1 — FROM / JOIN](#шаг-1--from--join)
- [Шаг 2 — WHERE](#шаг-2--where)
- [Шаг 3 — GROUP BY / HAVING](#шаг-3--group-by--having)
- [Шаг 4 — SELECT и window-функция](#шаг-4--select-и-window-функция)
- [Шаг 5 — ORDER BY / LIMIT](#шаг-5--order-by--limit)
- [Итоговая сводная таблица](#итоговая-сводная-таблица)

---

## Исходный запрос и данные

Разбираем этот запрос:

```sql
SELECT u.id, u.full_name, o.order_id, o.amount,
       SUM(o.amount) OVER (PARTITION BY u.id) AS total_amount
FROM users u
JOIN orders o ON o.user_id = u.id
WHERE o.amount > 2000;
```

Исходные данные для наглядности:

**users**

| id | full_name |
|----|-----------|
| 1  | Иванов    |
| 2  | Петров    |

**orders**

| order_id | user_id | amount |
|----------|---------|--------|
| 101      | 1       | 2500   |
| 102      | 1       | 3000   |
| 103      | 1       | 1500   |
| 104      | 2       | 2200   |

Важно: SQL пишется в порядке `SELECT → FROM → WHERE`, а выполняется в обратном логическом порядке — сначала `FROM`, и только в конце `SELECT`.

**Источник:** https://querycase.com/blog/sql-order-of-execution

> "You write SQL from SELECT down, but SQL runs FROM first and SELECT almost last."

RU:

> «Вы пишете SQL начиная с SELECT, но SQL выполняется начиная с FROM, а SELECT выполняется почти последним.»

---

## Шаг 1 — FROM / JOIN

Движок строит «сырой» набор строк: берёт `users`, соединяет с `orders` по условию `o.user_id = u.id`. Если у пользователя несколько заказов, его строка размножается — по одной строке на каждый заказ. Это и есть эффект **fan-out**.

**Результат после шага 1:**

| u.id | u.full_name | o.order_id | o.amount |
|------|-------------|------------|----------|
| 1    | Иванов      | 101        | 2500     |
| 1    | Иванов      | 102        | 3000     |
| 1    | Иванов      | 103        | 1500     |
| 2    | Петров      | 104        | 2200     |

Обратите внимание: строка 103 с суммой 1500 пока присутствует — фильтр `WHERE` ещё не применялся.

**Источник:** https://www.varsitytutors.com/practice/subjects/sql/lessons/join-duplication-issues

> "When a join key is not unique on at least one side of the join, rows from the other table are replicated for every matching key occurrence."

RU:

> «Если ключ соединения не уникален хотя бы с одной стороны JOIN, строки из другой таблицы дублируются на каждое совпадение ключа.»

---

## Шаг 2 — WHERE

Из набора убираются строки, не удовлетворяющие условию `o.amount > 2000`. Фильтрация построчная: движок ещё ничего не знает про группы, он просто проверяет каждую строку отдельно.

**Результат после шага 2** (строка с order_id=103 удалена, так как 1500 ≤ 2000):

| u.id | u.full_name | o.order_id | o.amount |
|------|-------------|------------|----------|
| 1    | Иванов      | 101        | 2500     |
| 1    | Иванов      | 102        | 3000     |
| 2    | Петров      | 104        | 2200     |

**Источник:** https://www.stratascratch.com/blog/sql-execution-order-explained

> "WHERE filters individual rows, and it runs before any grouping or aggregation."

RU:

> «WHERE фильтрует отдельные строки и выполняется до любой группировки или агрегации.»

---

## Шаг 3 — GROUP BY / HAVING

В нашем запросе нет ни `GROUP BY`, ни `HAVING`, поэтому движок этот логический шаг просто пропускает. Набор строк остаётся точно таким же, как после шага 2.

**Результат после шага 3** (без изменений):

| u.id | u.full_name | o.order_id | o.amount |
|------|-------------|------------|----------|
| 1    | Иванов      | 101        | 2500     |
| 1    | Иванов      | 102        | 3000     |
| 2    | Петров      | 104        | 2200     |

**Источник:** https://www.microsoftpressstore.com/articles/article.aspx?p=2992602&seqNum=5

> "Logical Query Processing: 1. FROM · 2. WHERE · 3. GROUP BY · 4. HAVING · 5. SELECT."

RU:

> «Логическая обработка запроса: 1. FROM · 2. WHERE · 3. GROUP BY · 4. HAVING · 5. SELECT.»

---

## Шаг 4 — SELECT и window-функция

Теперь вычисляются выражения из `SELECT`, включая `SUM(o.amount) OVER (PARTITION BY u.id)`.

Что делает `PARTITION BY u.id` наглядно: движок мысленно разбивает оставшиеся строки на группы по `u.id` (партиции), считает `SUM(amount)` **внутри каждой партиции отдельно**, но, в отличие от `GROUP BY`, не схлопывает строки — он просто дописывает посчитанную сумму в каждую строку этой партиции.

Разбивка по партициям на этом шаге:

**Партиция u.id = 1** (строки 101, 102): сумма = 2500 + 3000 = 5500
**Партиция u.id = 2** (строка 104): сумма = 2200

**Результат после шага 4:**

| u.id | u.full_name | o.order_id | o.amount | total_amount |
|------|-------------|------------|----------|---------------|
| 1    | Иванов      | 101        | 2500     | 5500          |
| 1    | Иванов      | 102        | 3000     | 5500          |
| 2    | Петров      | 104        | 2200     | 2200          |

Видно главное отличие от `GROUP BY`: строк по-прежнему три (не схлопнулись в две), но у обеих строк Иванова в `total_amount` стоит одинаковое число 5500 — сумма именно по его отфильтрованным заказам.

**Источник:** https://stackoverflow.com/questions/54357532/what-is-the-execution-order-of-the-partition-by-clause-compared-to-other-sql-cla

> "Window functions are executed/calculated at the same stage as SELECT, stage 5 in your table. In other words, window functions are applied to all rows that are 'visible' in the SELECT."

RU:

> «Window-функции вычисляются на том же этапе, что и SELECT, то есть на этапе 5. Иными словами, window-функции применяются ко всем строкам, которые „видны“ в SELECT.»

Также поэтому window-функции разрешено использовать только внутри `SELECT` и `ORDER BY` — на момент их вычисления набор строк уже полностью определён предыдущими шагами.

**Источник:** https://blog.jooq.org/a-beginners-guide-to-the-true-order-of-sql-operations/

> "Window functions being logically calculated only now also explains why you can put them only in the SELECT or ORDER BY clauses."

RU:

> «То, что window-функции логически вычисляются только сейчас, также объясняет, почему их можно использовать только в предложениях SELECT или ORDER BY.»

---

## Шаг 5 — ORDER BY / LIMIT

В исходном запросе этих предложений нет, поэтому движок на этом останавливается — итоговый результат тот же, что получился на шаге 4. Если бы вы дописали `ORDER BY u.id LIMIT 5`, то сортировка и обрезка результата были бы двумя последними шагами, уже после того как window-функция всё посчитала.

**Источник:** https://www.varsitytutors.com/practice/subjects/sql/lessons/window-functions-and-group-by

> "SQL processes clauses in a fixed logical order: FROM → WHERE → GROUP BY → HAVING → SELECT (including window functions) → ORDER BY → LIMIT."

RU:

> «SQL обрабатывает предложения в фиксированном логическом порядке: FROM → WHERE → GROUP BY → HAVING → SELECT (включая window-функции) → ORDER BY → LIMIT.»

---

## Итоговая сводная таблица

| Шаг | Предложение     | Что происходит                                                                 | Кол-во строк после шага |
|-----|-----------------|---------------------------------------------------------------------------------|--------------------------|
| 1   | FROM / JOIN     | Соединяем users и orders, происходит fan-out (размножение строк по заказам)     | 4                        |
| 2   | WHERE           | Убираем заказы с amount ≤ 2000                                                  | 3                        |
| 3   | GROUP BY/HAVING | Отсутствуют в запросе, шаг пропускается                                         | 3                        |
| 4   | SELECT (window) | Считаем SUM(amount) OVER (PARTITION BY u.id), строки не схлопываются            | 3                        |
| 5   | ORDER BY/LIMIT  | Отсутствуют в запросе, результат финальный                                      | 3                        |

Итоговый результат запроса — таблица из шага 4: три строки, каждая с суммой заказов пользователя, посчитанной только по заказам больше 2000.
