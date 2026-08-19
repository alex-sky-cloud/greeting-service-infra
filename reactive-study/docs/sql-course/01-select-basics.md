# Тема 01 — SELECT: основы

> **Статус:** теория · **Следующий шаг:** изучить материал → написать в чат *«дай интервью по теме 1»*

---

## Оглавление

- [Что изучаем](#что-изучаем)
- [Таблица users](#таблица-users)
- [Установка схемы search_path](#установка-схемы-search_path)
- [SELECT — выборка столбцов](#select--выборка-столбцов)
- [FROM — откуда берём строки](#from--откуда-берём-строки)
- [WHERE — фильтрация строк](#where--фильтрация-строк)
- [ORDER BY — сортировка](#order-by--сортировка)
- [LIMIT — ограничение числа строк](#limit--ограничение-числа-строк)
- [Полный запрос — разбор по частям](#полный-запрос--разбор-по-частям)
- [Типичные ошибки](#типичные-ошибки)
- [Что попробовать самостоятельно](#что-попробовать-самостоятельно)

---

## Что изучаем

В этой теме — минимальный набор для чтения данных из таблицы:

| Конструкция | Назначение |
|---|---|
| `SELECT` | какие столбцы вернуть |
| `FROM` | из какой таблицы |
| `WHERE` | какие строки оставить |
| `ORDER BY` | в каком порядке |
| `LIMIT` | сколько строк вернуть |

Все примеры — на таблице `reactive_study.users` (5 000 строк).  
Запросы выполняйте в **вашем SQL-клиенте** (консоль уже открыта — просто вставляйте SQL).

---

## Таблица users

```text
 id         | bigint                   | PK, generated always as identity
 email      | text                     | NOT NULL, UNIQUE
 full_name  | text                     | NOT NULL
 created_at | timestamptz              | NOT NULL, default now()
```

---

## Установка схемы search_path

Перед запросами удобно один раз установить схему по умолчанию:

```sql
SET search_path TO reactive_study;
```

### Разбор команды по частям

| Часть | Тип | Что делает |
|---|---|---|
| `SET` | команда | изменяет параметр сессии PostgreSQL на время текущего подключения |
| `search_path` | имя параметра | задаёт **порядок поиска схем**, когда вы пишете имя таблицы без префикса |
| `TO` | ключевое слово | связывает параметр с новым значением (синтаксис `SET … TO …`) |
| `reactive_study` | значение | имя схемы, где лежат учебные таблицы (`users`, `orders` и др.) |
| `;` | разделитель | конец команды |

**Что меняется после выполнения:**  
можно писать `users` вместо полного имени `reactive_study.users` — PostgreSQL сначала ищет таблицу в схеме `reactive_study`.

**Пример без `search_path`:**

```sql
SELECT id, email
FROM reactive_study.users
LIMIT 3;
```

**Тот же запрос после `SET search_path`:**

```sql
SELECT id, email
FROM users
LIMIT 3;
```

---

## SELECT — выборка столбцов

**Утверждение:** `SELECT` задаёт, **какие столбцы** (или выражения) попадут в результат.

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> `SELECT` retrieves rows from zero or more tables.

RU:

> `SELECT` извлекает строки из одной или нескольких таблиц.

### Пример 1 — все столбцы

```sql
SELECT *
FROM users
LIMIT 5;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `SELECT` | начало списка возвращаемых столбцов |
| `*` | специальный символ: «все столбцы таблицы из `FROM`» |
| `FROM users` | источник строк — таблица `users` (см. раздел [FROM](#from--откуда-берём-строки)) |
| `LIMIT 5` | вернуть не более 5 строк (см. раздел [LIMIT](#limit--ограничение-числа-строк)) |

### Пример 2 — конкретные столбцы

```sql
SELECT id, email, full_name
FROM users
LIMIT 5;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `SELECT` | начало списка столбцов |
| `id` | первый столбец результата — поле `id` из таблицы |
| `,` | разделитель между столбцами в списке |
| `email` | второй столбец |
| `full_name` | третий столбец |
| `FROM users` | строки берём из `users` |
| `LIMIT 5` | обрезаем результат до 5 строк |

### Пример 3 — псевдоним столбца

```sql
SELECT
    id,
    email,
    full_name AS name
FROM users
LIMIT 5;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `full_name` | исходный столбец таблицы |
| `AS` | ключевое слово: «показать этот столбец под другим именем» |
| `name` | псевдоним (alias) — так столбец будет называться в заголовке результата |
| остальное | как в примере 2 |

`AS` можно опустить (`full_name name`), но с `AS` запрос читается однозначнее.

---

## FROM — откуда берём строки

**Утверждение:** `FROM` указывает **источник данных** — таблицу. Без `FROM` можно выбрать только константы: `SELECT 1 + 1;`.

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> The `FROM` clause specifies one or more source tables for the `SELECT`.

RU:

> Предложение `FROM` задаёт одну или несколько исходных таблиц для `SELECT`.

### Пример — таблица в текущей схеме

```sql
SELECT *
FROM users
LIMIT 3;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `FROM` | ключевое слово: «данные брать из…» |
| `users` | имя таблицы; при установленном `search_path` это `reactive_study.users` |

### Пример — полное имя со схемой

```sql
SELECT *
FROM reactive_study.users
LIMIT 3;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `reactive_study` | имя схемы (namespace) — «папка» таблиц в базе |
| `.` | разделитель: схема **точка** таблица |
| `users` | имя таблицы внутри схемы |

Полное имя нужно, если `search_path` **не** содержит `reactive_study`.

---

## WHERE — фильтрация строк

**Утверждение:** `WHERE` **отбрасывает строки**, не удовлетворяющие условию.

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> If the `WHERE` clause is specified, all rows that do not satisfy the condition are eliminated from the output.

RU:

> Если указано предложение `WHERE`, все строки, не удовлетворяющие условию, исключаются из результата.

### Пример 1 — фильтр по id

```sql
SELECT id, email, full_name
FROM users
WHERE id = 1;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `SELECT id, email, full_name` | какие столбцы вернуть |
| `FROM users` | из какой таблицы |
| `WHERE` | начало условия фильтрации строк |
| `id` | столбец, по которому сравниваем |
| `=` | оператор «равно» |
| `1` | значение: оставить только строки, где `id` равен 1 |

### Пример 2 — фильтр по тексту (email)

```sql
SELECT id, email, full_name
FROM users
WHERE email = 'ann@example.com';
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `WHERE email` | сравниваем столбец `email` |
| `=` | оператор равенства |
| `'ann@example.com'` | **строковый литерал** — текст в одинарных кавычках; без кавычек PostgreSQL воспримет это как имя столбца и выдаст ошибку |

### Пример 3 — несколько условий (AND)

```sql
SELECT id, email
FROM users
WHERE id >= 10
  AND id <= 20
ORDER BY id;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `WHERE id >= 10` | `id` больше или равен 10 |
| `>=` | «больше или равно» |
| `AND` | логическое «и»: оба условия должны быть истинны |
| `id <= 20` | `id` меньше или равен 20 |
| `ORDER BY id` | отсортировать результат по `id` (подробнее — ниже) |

**Важно:** в `WHERE` нельзя ссылаться на псевдоним из `SELECT` (`AS name`) — только на реальные столбцы или выражения.

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> An output column's name can be used to refer to the column's value in `ORDER BY` and `GROUP BY` clauses, but not in the `WHERE` or `HAVING` clauses; there you must write out the expression instead.

RU:

> Имя столбца результата можно использовать в `ORDER BY` и `GROUP BY`, но **не** в `WHERE` или `HAVING` — там нужно писать само выражение.

---

## ORDER BY — сортировка

**Утверждение:** `ORDER BY` **упорядочивает** строки. Без него порядок **не гарантирован**.

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> If the `ORDER BY` clause is specified, the returned rows are sorted in the specified order. If `ORDER BY` is not given, the rows are returned in whatever order the system finds fastest to produce.

RU:

> Если указано `ORDER BY`, строки возвращаются в заданном порядке. Если `ORDER BY` нет, строки возвращаются в том порядке, который системе быстрее всего сформировать.

### Пример 1 — по возрастанию (ASC по умолчанию)

```sql
SELECT id, email
FROM users
ORDER BY id
LIMIT 5;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `ORDER BY` | «отсортировать результат» |
| `id` | столбец, по которому сортируем |
| *(не указано)* | направление по умолчанию — `ASC` (от меньшего к большему) |
| `LIMIT 5` | после сортировки взять первые 5 строк |

### Пример 2 — по убыванию (DESC)

```sql
SELECT id, email, created_at
FROM users
ORDER BY created_at DESC
LIMIT 5;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `ORDER BY created_at` | сортировка по дате регистрации |
| `DESC` | descending — от большего к меньшему (самые новые сверху) |
| `LIMIT 5` | 5 строк после сортировки |

### Пример 3 — несколько ключей сортировки

```sql
SELECT id, full_name, email
FROM users
ORDER BY full_name ASC, id ASC
LIMIT 10;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `ORDER BY full_name ASC` | сначала сортировка по имени (A→Z) |
| `, id ASC` | при одинаковом `full_name` — по `id` (A→Z) |
| `ASC` | явно указано «по возрастанию» (можно опустить — это значение по умолчанию) |

---

## LIMIT — ограничение числа строк

**Утверждение:** `LIMIT n` возвращает **не более n строк** из результата.

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> If the `LIMIT` (or `FETCH FIRST`) or `OFFSET` clause is specified, the `SELECT` statement only returns a subset of the result rows.

RU:

> Если указано `LIMIT` (или `FETCH FIRST`) или `OFFSET`, `SELECT` возвращает только подмножество строк результата.

### Пример 1 — первые 3 пользователя

```sql
SELECT id, email, full_name
FROM users
ORDER BY id
LIMIT 3;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `ORDER BY id` | сначала упорядочиваем по `id` |
| `LIMIT` | ограничиваем число строк в ответе |
| `3` | максимум 3 строки |

Без `ORDER BY` слово «первые» не имеет смысла — PostgreSQL возьмёт **любые** 3 строки.


**EN**:

> If ORDER BY is not given, the rows are returned in whatever order the system finds fastest to produce.

**RU**:

> Если ORDER BY не указан, строки возвращаются в том порядке, который системе быстрее всего сформировать.

**Почему** «иногда наблюдалось что база данных отдает все по порядку даже без ORDER BY»:

- таблица маленькая;
- данные лежат на диске «удобно»;
- после VACUUM/перестройки индекса порядок может измениться.

### Пример 2 — OFFSET (пропуск строк)

```sql
SELECT id, email
FROM users
ORDER BY id
OFFSET 10
LIMIT 5;
```

**Разбор:**

| Часть | Что делает |
|---|---|
| `ORDER BY id` | сортировка по `id` |
| `OFFSET 10` | пропустить первые 10 строк отсортированного результата |
| `LIMIT 5` | вернуть следующие 5 строк (с 11-й по 15-ю) |

---

## Полный запрос — разбор по частям

Пример, где участвуют все конструкции темы 01:

```sql
SELECT id, email
FROM users
WHERE id > 100
ORDER BY id
LIMIT 10;
```

**Порядок выполнения** (логика PostgreSQL):

```text
FROM  →  WHERE  →  SELECT  →  ORDER BY  →  LIMIT
```

**Разбор каждой части:**

| # | Часть | Что делает |
|---|---|---|
| 1 | `FROM users` | взять все строки таблицы `users` |
| 2 | `WHERE id > 100` | оставить только строки, где `id` строго больше 100 |
| 3 | `SELECT id, email` | из оставшихся строк взять только столбцы `id` и `email` |
| 4 | `ORDER BY id` | отсортировать по `id` по возрастанию |
| 5 | `LIMIT 10` | вернуть не более 10 строк |

**Источник:** https://www.postgresql.org/docs/current/sql-select.html

EN:

> `SELECT [ … ] [ FROM … ] [ WHERE condition ] [ ORDER BY … ] [ LIMIT { count | ALL } ]`

RU:

> Синтаксис: `SELECT` → `FROM` → `WHERE` → `ORDER BY` → `LIMIT` (части кроме `SELECT`/`FROM` опциональны).

---

## Типичные ошибки

| Ошибка | Правильно |
|---|---|
| `SELECT email, full_name WHERE id = 1` — нет `FROM` | `SELECT … FROM users WHERE id = 1` |
| Ожидать стабильный порядок без `ORDER BY` | Добавлять `ORDER BY`, если порядок важен |
| `WHERE name = 'Ann'` — нет столбца `name` | `WHERE full_name = 'Ann Smith'` |
| `WHERE email = ann@example.com` — без кавычек | `WHERE email = 'ann@example.com'` |
| `SELECT full_name AS name … WHERE name = 'Ann'` | `WHERE full_name = 'Ann Smith'` |

---

## Что попробовать самостоятельно

Выполните в своём SQL-клиенте (не обязательно скидывать — для себя):

1. Все `email` и `full_name` пользователей с `id` от 1 до 10, по возрастанию `id`.
2. Только `id` и `email` пользователя с `id = 42` (если такого нет — пустой результат, это нормально).
3. 5 пользователей с **наибольшим** `id` (подсказка: `ORDER BY id DESC LIMIT 5`).
4. Один и тот же запрос без `ORDER BY` — выполнить два раза и сравнить порядок строк.

---

*Когда будете готовы — напишите в чат: **«дай интервью по теме 1»**.*
