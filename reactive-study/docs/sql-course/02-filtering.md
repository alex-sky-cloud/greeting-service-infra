# Тема 02 — Фильтрация данных

> **Статус:** теория · **Следующий шаг:** изучить материал → написать в чат *«дай интервью по теме 2»*

---

## Оглавление

- [Что изучаем](#что-изучаем)
- [Напоминание: WHERE](#напоминание-where)
- [AND и OR — несколько условий](#and-и-or--несколько-условий)
- [NOT — отрицание](#not--отрицание)
- [BETWEEN — диапазон включительно](#between--диапазон-включительно)
- [IN — значение из списка](#in--значение-из-списка)
- [LIKE — поиск по шаблону](#like--поиск-по-шаблону)
- [IS NULL / IS NOT NULL](#is-null--is-not-null)
- [Комбинирование условий](#комбинирование-условий)
- [Типичные ошибки](#типичные-ошибки)
- [Что попробовать самостоятельно](#что-попробовать-самостоятельно)

---

## Что изучаем

В теме 01 вы уже использовали `WHERE` с `=` и `AND`. Теперь расширяем **инструменты фильтрации**:

| Конструкция | Назначение |
|---|---|
| `AND` / `OR` | несколько условий вместе |
| `NOT` | отрицание условия |
| `BETWEEN … AND …` | диапазон (границы включены) |
| `IN (…)` | значение входит в список |
| `LIKE` | поиск по шаблону в тексте |
| `IS NULL` / `IS NOT NULL` | проверка на пустое значение |

Примеры — на таблицах `users` и `orders`.  
Запросы выполняйте в **вашем SQL-клиенте**:

```sql
SET search_path TO reactive_study;
```

---

## Напоминание: WHERE

`WHERE` отбирает **строки**, для которых условие истинно.  
Условие — логическое выражение: результат `true`, `false` или `NULL` («неизвестно»).

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> If the `WHERE` clause is specified, all rows that do not satisfy the condition are eliminated from the output.

RU:

> Если указано `WHERE`, все строки, не удовлетворяющие условию, исключаются из результата.

---

## AND и OR — несколько условий

**Утверждение:** `AND` требует, чтобы **оба** условия были истинны. `OR` — чтобы **хотя бы одно** было истинно.

**Источник:** https://www.postgresql.org/docs/current/functions-logical.html

EN:

> SQL uses a three-valued logic system with true, false, and null, which represents “unknown”.

RU:

> SQL использует трёхзначную логику: true, false и null («неизвестно»).

### Пример 1 — AND (пересечение)

```sql
SELECT id, full_name
FROM users
WHERE id >= 5
  AND id <= 9;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `WHERE id >= 5` | id не меньше 5 |
| `AND` | **и одновременно** |
| `id <= 9` | id не больше 9 |
| **Результат** | строки с id = 5, 6, 7, 8, 9 |

### Пример 2 — OR (объединение)

```sql
SELECT id, email
FROM users
WHERE id = 1
   OR id = 2
   OR id = 3;
```

**Результат**: в выборку попадают все три пользователя, так как все указанные id присутствуют в таблице.

**Разбор:**

| Часть | Что делает |
|---|---|
| `id = 1` | первая допустимая строка |
| `OR` | **или** |
| `id = 2` | вторая |
| `OR` | **или** |
| `id = 3` | третья |
| **Результат** | любая строка, где id равен 1, 2 **или** 3 |

### Пример 3 — OR на таблице orders

```sql
SELECT id, status, amount
FROM orders
WHERE status = 'pending'
   OR status = 'cancelled'
ORDER BY id
LIMIT 10;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `FROM orders` | источник — таблица заказов |
| `status = 'pending'` | статус «ожидает» |
| `OR status = 'cancelled'` | **или** статус «отменён» |
| `ORDER BY id LIMIT 10` | первые 10 по id (после сортировки) |

---

## NOT — отрицание

**Утверждение:** `NOT` инвертирует условие: истина → ложь, ложь → истина.

### Пример

```sql
SELECT id, status
FROM orders
WHERE NOT status = 'delivered'
ORDER BY id
LIMIT 10;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `NOT` | отрицание следующего условия |
| `status = 'delivered'` | «статус доставлен» |
| **Вместе** | все заказы, статус которых **не** `delivered` |

Эквивалент через `<>`:

```sql
WHERE status <> 'delivered'
```

---

## BETWEEN — диапазон включительно

**Утверждение:** `BETWEEN x AND y` — сокращение для «от x до y **включительно**». Эквивалент: `>= x AND <= y`.

**Источник:** https://www.postgresql.org/docs/current/functions-comparison.html

EN:

> The `BETWEEN` predicate simplifies range tests: `a BETWEEN x AND y` is equivalent to `a >= x AND a <= y`. Notice that `BETWEEN` treats the endpoint values as included in the range.

RU:

> Предикат `BETWEEN` упрощает проверку диапазона: `a BETWEEN x AND y` эквивалентно `a >= x AND a <= y`. Граничные значения **включены** в диапазон.

### Пример 1 — диапазон id

```sql
SELECT id, full_name
FROM users
WHERE id BETWEEN 5 AND 9
ORDER BY id;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `id BETWEEN 5 AND 9` | id от 5 до 9 включительно |
| то же, что | `id >= 5 AND id <= 9` |
| **Результат** | id = 5, 6, 7, 8, 9 |

### Пример 2 — NOT BETWEEN

```sql
SELECT id, email
FROM users
WHERE id NOT BETWEEN 1 AND 3
ORDER BY id
LIMIT 5;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `NOT BETWEEN 1 AND 3` | id **не** в диапазоне 1–3 |
| **Результат** | id = 4, 5, 6, … |

---

## IN — значение из списка

**Утверждение:** `IN (список)` проверяет, **равно ли** значение столбца одному из перечисленных. Удобная замена нескольким `OR`.

### Пример 1 — несколько id

```sql
SELECT id, full_name
FROM users
WHERE id IN (1, 2, 3)
ORDER BY id;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `IN` | «входит в список» |
| `(1, 2, 3)` | список допустимых значений |
| **Эквивалент** | `id = 1 OR id = 2 OR id = 3` |

### Пример 2 — несколько статусов заказа

```sql
SELECT id, status, amount
FROM orders
WHERE status IN ('pending', 'processing', 'shipped')
ORDER BY id
LIMIT 10;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `status IN (...)` | статус — один из трёх перечисленных |
| `'pending'` | строковый литерал в одинарных кавычках |
| **Результат** | заказы в статусах pending, processing или shipped |

### Пример 3 — NOT IN

```sql
SELECT id, status
FROM orders
WHERE status NOT IN ('cancelled', 'delivered')
ORDER BY id
LIMIT 10;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `NOT IN (...)` | статус **не** входит в список |
| **Результат** | pending, processing, shipped |

---

## LIKE — поиск по шаблону

**Утверждение:** `LIKE` сравнивает строку с **шаблоном**. Два спецсимвола: `%` (любая последовательность символов) и `_` (ровно один любой символ).

**Источник:** https://www.postgresql.org/docs/current/functions-matching.html

EN:

> The `LIKE` expression returns true if the `string` matches the supplied `pattern`. An underscore (`_`) in `pattern` stands for (matches) any single character; a percent sign (`%`) matches any sequence of zero or more characters.

RU:

> `LIKE` возвращает true, если строка соответствует шаблону. `_` — один любой символ; `%` — ноль или более любых символов.

### Пример 1 — email на домене example.com

```sql
SELECT id, email, full_name
FROM users
WHERE email LIKE '%@example.com'
ORDER BY id;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `email LIKE` | сравнение email с шаблоном |
| `'%@example.com'` | `%` — любые символы до `@`, затем буквально `@example.com` |
| **Результат** | ann@example.com, bob@example.com, carol@example.com |

### Пример 2 — email начинается с user

```sql
SELECT id, email
FROM users
WHERE email LIKE 'user%'
ORDER BY id
LIMIT 5;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `'user%'` | начинается с `user`, дальше — что угодно |
| `%` в конце | ноль или больше символов после `user` |
| **Результат** | user4@..., user5@..., … |

### Пример 3 — один символ-заполнитель `_`

```sql
SELECT id, email
FROM users
WHERE email LIKE 'user_@load.reactive-study.test'
ORDER BY id
LIMIT 5;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `'user_'` | буквально `user`, затем **ровно один** любой символ |
| `_` | один символ (здесь — цифра 4, 5, 6…) |
| остальная часть | буквально `@load.reactive-study.test` |

### Пример 4 — NOT LIKE

```sql
SELECT id, email
FROM users
WHERE email NOT LIKE 'user%'
ORDER BY id
LIMIT 5;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `NOT LIKE` | строка **не** подходит под шаблон |
| **Результат** | пользователи, email которых **не** начинается с `user` |

### ILIKE — без учёта регистра (PostgreSQL)

```sql
SELECT id, full_name
FROM users
WHERE full_name ILIKE 'ann%';
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `ILIKE` | как `LIKE`, но без учёта регистра (расширение PostgreSQL) |
| `'ann%'` | найдёт «Ann Smith», «ann», «ANN…» |

**Источник:** https://www.postgresql.org/docs/current/functions-matching.html

EN:

> The key word `ILIKE` can be used instead of `LIKE` to make the match case-insensitive according to the active locale.

RU:

> `ILIKE` можно использовать вместо `LIKE` для сопоставления без учёта регистра.

---

## IS NULL / IS NOT NULL

**Утверждение:** `NULL` — «значение неизвестно / отсутствует». Сравнение `= NULL` **не работает** — нужно `IS NULL`.

**Источник:** https://www.postgresql.org/docs/current/functions-comparison.html

EN:

> Do not write `expression = NULL` because `NULL` is not “equal to” `NULL`. (The null value represents an unknown value, and it is not known whether two unknown values are equal.)

RU:

> Не пишите `expression = NULL`, потому что `NULL` не «равно» `NULL`. Null означает неизвестное значение.

EN:

> `datatype IS NULL` → boolean — Test whether value is null.

RU:

> `IS NULL` — проверка, является ли значение null.

### Пример 1 — IS NULL

```sql
SELECT id, product_id, status
FROM orders
WHERE product_id IS NULL
LIMIT 10;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `product_id IS NULL` | столбец `product_id` **не заполнен** |
| **Замечание** | в текущих данных таких строк может не быть — запрос всё равно корректен |

### Пример 2 — IS NOT NULL

```sql
SELECT id, product_id, status
FROM orders
WHERE product_id IS NOT NULL
ORDER BY id
LIMIT 5;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `IS NOT NULL` | столбец **заполнен** (есть значение) |
| **Результат** | заказы с указанным product_id |

### Пример 3 — ошибка с = NULL

```sql
-- НЕПРАВИЛЬНО — всегда даст пустой результат:
SELECT id FROM orders WHERE product_id = NULL;

-- ПРАВИЛЬНО:
SELECT id FROM orders WHERE product_id IS NULL;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `= NULL` | всегда даёт `NULL` (не true) → строки отфильтровываются |
| `IS NULL` | корректная проверка на отсутствие значения |

---

## Комбинирование условий

Скобки задают **приоритет**, когда смешиваете `AND` и `OR`:

```sql
SELECT id, status, amount
FROM orders
WHERE (status = 'pending' OR status = 'processing')
  AND amount > 1000
ORDER BY id
LIMIT 10;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `(status = 'pending' OR status = 'processing')` | сначала: статус pending **или** processing |
| `AND amount > 1000` | **и** сумма заказа больше 1000 |
| скобки | без них `AND` связывался бы иначе — результат был бы другим |

**Порядок приоритета:** `NOT` → `AND` → `OR`. При сомнении — ставьте скобки.

---

## Типичные ошибки

| Ошибка | Правильно |
|---|---|
| `WHERE email = ann@example.com` | `WHERE email = 'ann@example.com'` |
| `WHERE product_id = NULL` | `WHERE product_id IS NULL` |
| `WHERE id BETWEEN 9 AND 5` | `BETWEEN` требует левую границу ≤ правой → `BETWEEN 5 AND 9` |
| `WHERE id <= 4 AND <= 9` | `WHERE id <= 4 AND id <= 9` |
| `LIKE 'user'` (без `%`) | ищет **точно** `user`, не `user4@...` → `'user%'` |
| `OR` без скобок в сложном условии | `(A OR B) AND C` — явные скобки |

---

## Что попробовать самостоятельно

1. Пользователи с `id` **1, 2 или 3** — через `IN`, не через три `OR`.
2. Email, заканчивающиеся на `@load.reactive-study.test` — через `LIKE`.
3. Заказы со статусом **не** `cancelled` — через `NOT IN` или `<>` .
4. Заказы с `amount` **от 500 до 1000 включительно** — через `BETWEEN`.
5. Запрос с **AND** и **OR** вместе — не забудьте скобки.

---

*Когда будете готовы — напишите в чат: **«дай интервью по теме 2»**.*
