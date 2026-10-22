# Тема 11 — индексы и EXPLAIN

Запрос может вернуть правильный ответ и при этом работать медленно: PostgreSQL перебирает всю таблицу, чтобы найти несколько строк. Индекс — отдельная структура, по которой нужные строки находятся без полного перебора. `EXPLAIN` показывает, каким способом PostgreSQL собирается выполнить запрос: перебором или через индекс.

В этой теме разбирается, как читать короткий план, когда индекс помогает, когда PostgreSQL его не берёт и почему, и как правильно написать условие, чтобы индекс сработал.

Подробное руководство по чтению больших планов — в [`planner-explain.md`](planner-explain.md) и [`06-uzly-plana-zaprosa.md`](06-uzly-plana-zaprosa.md). Здесь каждый пример самодостаточен: полный запрос и полный план.

Все планы ниже сняты на учебной базе `reactive_study`. Числа `cost` и `rows` в плане — оценки планировщика. После обновления статистики они могут немного отличаться, а способ чтения таблицы остаётся тем же.

## Оглавление

- [Что такое индекс](#что-такое-индекс)
- [Какие индексы уже есть в базе](#какие-индексы-уже-есть-в-базе)
- [Как устроен индекс внутри](#как-устроен-индекс-внутри)
- [EXPLAIN: как прочитать короткий план](#explain-как-прочитать-короткий-план)
- [Seq Scan: перебор всей таблицы](#seq-scan-перебор-всей-таблицы)
- [Bitmap Index Scan: много строк по индексу](#bitmap-index-scan-много-строк-по-индексу)
- [Index Only Scan: ответ прямо из индекса](#index-only-scan-ответ-прямо-из-индекса)
- [Когда индекс есть, но PostgreSQL его не берёт](#когда-индекс-есть-но-postgresql-его-не-берёт)
- [Функция над столбцом выключает индекс](#функция-над-столбцом-выключает-индекс)
- [EXPLAIN ANALYZE: план и факт](#explain-analyze-план-и-факт)
- [Индекс по выражению](#индекс-по-выражению)
- [Составной индекс: порядок столбцов важен](#составной-индекс-порядок-столбцов-важен)
- [Создать индекс и проверить план](#создать-индекс-и-проверить-план)
- [LIKE по началу строки](#like-по-началу-строки)
- [Индекс для ORDER BY и LIMIT](#индекс-для-order-by-и-limit)
- [Индекс в соединении таблиц](#индекс-в-соединении-таблиц)
- [Чем платят за индекс](#чем-платят-за-индекс)
- [Что обычно спрашивают на собеседованиях](#что-обычно-спрашивают-на-собеседованиях)
- [Карта выбора](#карта-выбора)
- [Что попробовать самостоятельно](#что-попробовать-самостоятельно)

---

## Что такое индекс

Без индекса поиск строки похож на поиск слова в книге без алфавитного указателя: нужно прочитать все страницы. Индекс — это и есть указатель: значения столбца, разложенные по порядку, и рядом с каждым значением — где лежит строка таблицы.

Индекс не меняет ответ запроса. Он меняет только способ, которым PostgreSQL этот ответ находит.

**Источник:** https://www.postgresql.org/docs/16/indexes-intro.html

**EN:**

> With no advance preparation, the system would have to scan the entire `test1` table, row by row, to find all matching entries. If there are many rows in `test1` and only a few rows (perhaps zero or one) that would be returned by such a query, this is clearly an inefficient method. But if the system has been instructed to maintain an index on the `id` column, it can use a more efficient method for locating matching rows. For instance, it might only have to walk a few levels deep into a search tree.
>
> A similar approach is used in most non-fiction books: terms and concepts that are frequently looked up by readers are collected in an alphabetic index at the end of the book.

**RU:**

> Без предварительной подготовки системе пришлось бы просматривать всю таблицу `test1` строка за строкой, чтобы найти все подходящие записи. Если строк в `test1` много, а запрос вернёт лишь несколько (возможно, ноль или одну), это явно неэффективный способ. Но если системе поручено поддерживать индекс по столбцу `id`, она может найти подходящие строки более эффективным способом. Например, ей может понадобиться спуститься всего на несколько уровней вглубь дерева поиска.
>
> Похожий подход используется в большинстве научно-популярных книг: термины и понятия, которые читатели часто ищут, собраны в алфавитный указатель в конце книги.

### Синтаксис создания и удаления

```sql

CREATE INDEX index_name ON table_name (column_name);
DROP INDEX index_name;
```

- `index_name` — имя индекса, его придумывают сами. Принято `idx_<таблица>_<столбцы>`: `idx_orders_created_at`.
- `table_name` — таблица, для которой строится индекс.
- `column_name` — столбец, по значениям которого строится индекс. Столбцов может быть несколько через запятую — это составной индекс.
- `DROP INDEX` удаляет индекс. Данные таблицы при этом не меняются.

По умолчанию PostgreSQL строит индекс типа **B-tree** — дерево, в котором значения лежат по порядку. Он подходит для `=`, `<`, `>`, `<=`, `>=`, `BETWEEN`, `IN` и для сортировки. Все индексы в этой теме — B-tree.

---

## Какие индексы уже есть в базе

**Задача.** Разработчик пришёл в проект и хочет узнать, какие индексы уже построены на таблице заказов.

```sql

SELECT indexname,
       indexdef
FROM pg_indexes
WHERE schemaname = 'reactive_study'
  AND tablename = 'orders'
ORDER BY indexname;
```

**Как читается запрос**

- `FROM pg_indexes` — `pg_indexes` — служебное представление PostgreSQL: одна строка — один индекс в базе.
- `WHERE schemaname = 'reactive_study' AND tablename = 'orders'` — только индексы таблицы `orders` учебной схемы.
- `indexname` — имя индекса.
- `indexdef` — команда, которой индекс создан. По ней видно, по каким столбцам он построен.

| indexname | indexdef |
|---|---|
| idx_orders_created_at | CREATE INDEX idx_orders_created_at ON reactive_study.orders USING btree (created_at) |
| idx_orders_product_id | CREATE INDEX idx_orders_product_id ON reactive_study.orders USING btree (product_id) |
| idx_orders_status_created_at | CREATE INDEX idx_orders_status_created_at ON reactive_study.orders USING btree (status, created_at) |
| idx_orders_user_id | CREATE INDEX idx_orders_user_id ON reactive_study.orders USING btree (user_id) |
| orders_pkey | CREATE UNIQUE INDEX orders_pkey ON reactive_study.orders USING btree (id) |

**Нюансы**

- `orders_pkey` никто не создавал командой `CREATE INDEX`: PostgreSQL построил его сам для первичного ключа `id`. Так же сам строится индекс для ограничения `UNIQUE`.
- `idx_orders_status_created_at` — составной индекс по двум столбцам. Как он работает, разобрано в разделе [про составной индекс](#составной-индекс-порядок-столбцов-важен).
- В `psql` тот же список показывает команда `\d orders` — в разделе `Indexes`.

---

## Как устроен индекс внутри

У каждой строки таблицы есть физический адрес — `ctid`: номер страницы файла таблицы и номер строки на этой странице. Индекс хранит пары «значение столбца → адрес строки», разложенные по значению.

**Задача.** Показать, какие пары хранит индекс `idx_orders_created_at` в самом начале: пять самых ранних заказов.

```sql

SELECT created_at,
       ctid,
       id
FROM orders
ORDER BY created_at
LIMIT 5;
```

**Как читается запрос**

- `ctid` — скрытый служебный столбец каждой таблицы. Его можно выбрать, но в `SELECT *` он не входит.
- `ORDER BY created_at LIMIT 5` — пять самых ранних заказов. Именно в таком порядке значения лежат в индексе по `created_at`.
- `(718,43)` значит: страница 718 файла таблицы, строка 43 на этой странице.

| created_at | ctid | id |
|---|---|---:|
| 2025-07-29 16:29:23.682063+00 | (718,43) | 54635 |
| 2025-07-29 16:46:50.143768+00 | (961,65) | 72884 |
| 2025-07-29 16:49:37.248015+00 | (388,48) | 29889 |
| 2025-07-29 16:57:20.545789+00 | (41,76) | 3397 |
| 2025-07-29 16:58:35.026529+00 | (294,29) | 22820 |

**Что из этого следует**

- В индексе время идёт по порядку, а адреса строк — вразнобой: 718, 961, 388, 41, 294. Соседние по времени заказы лежат на разных страницах таблицы.
- Найти в индексе нужный диапазон времени быстро: значения отсортированы, PostgreSQL спускается по дереву к началу диапазона и идёт вправо.
- Но за каждой найденной строкой нужно сходить в таблицу по её адресу. Если строк много, таких походов много, и перебрать всю таблицу подряд может оказаться дешевле. Поэтому PostgreSQL берёт индекс не всегда.

---

## EXPLAIN: как прочитать короткий план

### Синтаксис

```sql

EXPLAIN query;
EXPLAIN ANALYZE query;
```

- `EXPLAIN` перед запросом — запрос **не выполняется**. PostgreSQL только показывает план: каким способом он собирается читать таблицы.
- `EXPLAIN ANALYZE` — запрос **выполняется**, и рядом с планом показывается, что произошло на самом деле: сколько строк и сколько миллисекунд. Для `UPDATE` и `DELETE` это значит, что данные действительно изменятся.

### Что написано в строке плана

**Задача.** Разработчик проверяет, как PostgreSQL находит заказ по его номеру.

```sql

EXPLAIN
SELECT *
FROM orders
WHERE id = 10;
```

```text
 Index Scan using orders_pkey on orders  (cost=0.29..8.31 rows=1 width=68)
   Index Cond: (id = 10)
```

**Как читается план**

- `Index Scan` — способ чтения: найти строки через индекс, затем прочитать каждую строку из таблицы.
- `using orders_pkey` — какой индекс используется: индекс первичного ключа.
- `on orders` — какая таблица читается.
- `cost=0.29..8.31` — оценка стоимости в условных единицах. Первое число — сколько стоит получить первую строку, второе — все строки. Сравнивают стоимость разных планов между собой; в миллисекунды она не переводится.
- `rows=1` — сколько строк, по оценке планировщика, вернёт этот шаг.
- `width=68` — средний размер одной строки в байтах.
- `Index Cond: (id = 10)` — какое условие проверяется внутри индекса. Это условие из `WHERE`.

Основные способы чтения таблицы:

| Узел плана | Что делает | Когда выбирается |
|---|---|---|
| `Seq Scan` | читает всю таблицу подряд и проверяет каждую строку | индекса нет, или нужна большая часть таблицы |
| `Index Scan` | находит строки в индексе и по одной читает их из таблицы | нужно мало строк |
| `Bitmap Index Scan` + `Bitmap Heap Scan` | сначала собирает по индексу адреса всех нужных строк, потом читает страницы таблицы по порядку | нужно много строк, но далеко не все |
| `Index Only Scan` | берёт ответ прямо из индекса, в таблицу почти не ходит | все нужные запросу столбцы есть в индексе |

---

## Seq Scan: перебор всей таблицы

**Задача.** Аналитик ищет заказы дороже 1000. Индекса по сумме заказа нет.

```sql

EXPLAIN
SELECT *
FROM orders
WHERE amount > 1000;
```

```text
 Seq Scan on orders  (cost=0.00..2574.09 rows=50579 width=68)
   Filter: (amount > '1000'::numeric)
```

**Как читается план**

- `Seq Scan on orders` — читается вся таблица `orders`, все 100007 строк.
- `Filter: (amount > '1000'::numeric)` — каждая прочитанная строка проверяется условием. `Filter` — проверка **после** чтения строки, а не поиск через индекс.
- `rows=50579` — по оценке, условию подходит примерно половина заказов.

**Нюансы**

- `Seq Scan` — не ошибка сама по себе. Если запросу нужна половина таблицы, прочитать её подряд дешевле, чем 50 тысяч раз ходить по адресам из индекса.
- `Index Cond` и `Filter` — главное отличие в плане. `Index Cond` сужает поиск через индекс, `Filter` отбрасывает строки, которые уже прочитаны.

---

## Bitmap Index Scan: много строк по индексу

**Задача.** Дашборд показывает заказы за 1 июня 2026 года. На `created_at` есть индекс.

```sql

EXPLAIN
SELECT id,
       created_at
FROM orders
WHERE created_at >= '2026-06-01'
  AND created_at < '2026-06-02';
```

```text
 Bitmap Heap Scan on orders  (cost=7.11..685.33 rows=275 width=16)
   Recheck Cond: ((created_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (created_at < '2026-06-02 00:00:00+00'::timestamp with time zone))
   ->  Bitmap Index Scan on idx_orders_created_at  (cost=0.00..7.04 rows=275 width=0)
         Index Cond: ((created_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (created_at < '2026-06-02 00:00:00+00'::timestamp with time zone))
```

**Как читается план**

План читается снизу вверх: сначала выполняется самая вложенная строка.

- `Bitmap Index Scan on idx_orders_created_at` — по индексу находятся адреса всех заказов за 1 июня. По оценке — 275 строк. Строки таблицы на этом шаге ещё не читаются, поэтому `width=0`.
- `Index Cond: (…)` — диапазон времени ищется внутри индекса.
- `->` — стрелка показывает, что этот шаг передаёт результат шагу выше.
- `Bitmap Heap Scan on orders` — найденные адреса упорядочиваются по номеру страницы, и страницы таблицы читаются по порядку. Если на одной странице несколько нужных строк, страница читается один раз.
- `Recheck Cond: (…)` — повторная проверка условия на строках прочитанных страниц.

**Нюансы**

- Почему не `Index Scan`: 275 строк лежат на разных страницах. Прочитать каждую страницу один раз и по порядку дешевле, чем прыгать по адресам в порядке индекса.
- Строка `'2026-06-01'` в плане превратилась в `'2026-06-01 00:00:00+00'::timestamp with time zone`: PostgreSQL перевёл её в тип столбца.

---

## Index Only Scan: ответ прямо из индекса

**Задача.** Карточка клиента 352 показывает, сколько у него заказов.

```sql

EXPLAIN
SELECT count(*)
FROM orders
WHERE user_id = 352;
```

```text
 Aggregate  (cost=9.23..9.24 rows=1 width=8)
   ->  Index Only Scan using idx_orders_user_id on orders  (cost=0.29..9.12 rows=47 width=0)
         Index Cond: (user_id = 352)
```

**Как читается план**

- `Index Only Scan using idx_orders_user_id` — запросу нужен только факт «строка с `user_id = 352` есть». Значение `user_id` уже лежит в индексе, поэтому за строками в таблицу ходить не нужно.
- `rows=47` — оценка. На самом деле у клиента 352 — 33 заказа.
- `Aggregate` — над результатом считается `count(*)`.

**Нюансы**

- Если добавить в `SELECT` столбец, которого нет в индексе, например `amount`, PostgreSQL придётся читать строки таблицы, и `Index Only Scan` превратится в обычный `Index Scan` или `Bitmap Heap Scan`.
- PostgreSQL всё же иногда заглядывает в таблицу при `Index Only Scan`: для недавно изменённых страниц он проверяет, видна ли строка текущей транзакции.

---

## Когда индекс есть, но PostgreSQL его не берёт

**Задача.** Аналитик выгружает все заказы начиная с 1 августа 2025 года. Индекс по `created_at` есть.

```sql

EXPLAIN
SELECT *
FROM orders
WHERE created_at >= '2025-08-01';
```

```text
 Seq Scan on orders  (cost=0.00..2574.09 rows=99420 width=68)
   Filter: (created_at >= '2025-08-01 00:00:00+00'::timestamp with time zone)
```

**Как читается план**

- `rows=99420` — условию подходит 99420 строк из 100007, то есть почти вся таблица.
- `Seq Scan` — если нужна почти вся таблица, проход по индексу добавил бы лишнюю работу: сначала индекс, потом всё равно почти все страницы таблицы.

**Нюансы**

- Индекс полезен, когда условие отбирает **малую** часть строк. Это называют **селективностью**: чем меньше доля подходящих строк, тем селективнее условие.
- Выбор делает планировщик по статистике таблицы. Статистику обновляет команда `ANALYZE table_name` и фоновый процесс autovacuum.

---

## Функция над столбцом выключает индекс

**Задача.** Дашборд показывает заказы за 1 июня 2026 года. Разработчик записал условие через перевод в дату.

```sql

EXPLAIN
SELECT id,
       created_at
FROM orders
WHERE created_at::date = '2026-06-01';
```

```text
 Seq Scan on orders  (cost=0.00..2824.11 rows=500 width=16)
   Filter: ((created_at)::date = '2026-06-01'::date)
```

**Как читается план**

- Ответ тот же, что в разделе про [Bitmap Index Scan](#bitmap-index-scan-много-строк-по-индексу): заказы за 1 июня.
- Но в плане `Seq Scan`: индекс `idx_orders_created_at` не используется.
- Причина: индекс хранит значения `created_at`, а условие сравнивает не `created_at`, а результат выражения `created_at::date`. Значений `created_at::date` в индексе нет, искать в нём нечего. Поэтому PostgreSQL вычисляет выражение для каждой из 100007 строк.

**Как переписать.** Оставить столбец «голым», а период выразить границами — как в теме 10:

```sql

EXPLAIN
SELECT id,
       created_at
FROM orders
WHERE created_at >= '2026-06-01'
  AND created_at < '2026-06-02';
```

```text
 Bitmap Heap Scan on orders  (cost=7.11..685.33 rows=275 width=16)
   Recheck Cond: ((created_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (created_at < '2026-06-02 00:00:00+00'::timestamp with time zone))
   ->  Bitmap Index Scan on idx_orders_created_at  (cost=0.00..7.04 rows=275 width=0)
         Index Cond: ((created_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (created_at < '2026-06-02 00:00:00+00'::timestamp with time zone))
```

**Нюансы**

- Правило: индекс по столбцу работает, когда в условии стоит **сам столбец** с одной стороны и вычисляемое один раз значение с другой: `created_at >= '2026-06-01'`.
- То же касается `EXTRACT(year FROM created_at) = 2025`, `lower(email) = '…'`, `amount * 2 > 100`. Любая функция или арифметика над столбцом прячет его от обычного индекса.

---

## EXPLAIN ANALYZE: план и факт

`EXPLAIN` показывает оценку. `EXPLAIN ANALYZE` выполняет запрос и добавляет фактические числа.

**Задача.** Сравнить по факту два способа выбрать заказы за 1 июня 2026 года.

```sql

EXPLAIN ANALYZE
SELECT id,
       created_at
FROM orders
WHERE created_at >= '2026-06-01'
  AND created_at < '2026-06-02';
```

```text
 Bitmap Heap Scan on orders  (cost=7.11..685.33 rows=275 width=16) (actual time=0.109..1.043 rows=306 loops=1)
   Recheck Cond: ((created_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (created_at < '2026-06-02 00:00:00+00'::timestamp with time zone))
   Heap Blocks: exact=283
   ->  Bitmap Index Scan on idx_orders_created_at  (cost=0.00..7.04 rows=275 width=0) (actual time=0.073..0.073 rows=306 loops=1)
         Index Cond: ((created_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (created_at < '2026-06-02 00:00:00+00'::timestamp with time zone))
 Planning Time: 0.289 ms
 Execution Time: 1.121 ms
```

```sql

EXPLAIN ANALYZE
SELECT id,
       created_at
FROM orders
WHERE created_at::date = '2026-06-01';
```

```text
 Seq Scan on orders  (cost=0.00..2824.11 rows=500 width=16) (actual time=0.014..11.919 rows=306 loops=1)
   Filter: ((created_at)::date = '2026-06-01'::date)
   Rows Removed by Filter: 99701
 Planning Time: 0.168 ms
 Execution Time: 11.976 ms
```

**Как читается план**

- `actual time=0.109..1.043` — фактическое время шага в миллисекундах: до первой строки и до последней.
- `rows=306` в скобках `actual` — сколько строк вернулось на самом деле. Оценка была 275 — планировщик почти угадал.
- `loops=1` — сколько раз выполнялся шаг.
- `Heap Blocks: exact=283` — прочитано 283 страницы таблицы: 306 нужных строк лежат на 283 разных страницах.
- `Rows Removed by Filter: 99701` — во втором плане прочитано 100007 строк, из них 99701 отброшено условием. Это главный признак того, что перебор делался зря: `306 + 99701 = 100007`.
- `Execution Time` — общее время выполнения: `1.121 ms` против `11.976 ms`.

**Нюансы**

- Миллисекунды меняются от запуска к запуску: важен порядок величины и разница между вариантами, а не точное число.
- На 100 тысячах строк разница — 11 миллисекунд. На таблице в 100 миллионов строк тот же `Seq Scan` займёт секунды, а поиск по индексу останется быстрым.

---

## Индекс по выражению

Если запросы постоянно ищут по выражению, а переписать условие нельзя, индекс строят по самому выражению.

### Синтаксис

```sql

CREATE INDEX index_name ON table_name ((expression));
```

- Выражение пишется в **двойных** скобках: внешние — список столбцов индекса, внутренние — само выражение.
- Индекс используется, только если в условии запроса стоит **то же самое выражение**.

**Задача.** Служба поддержки ищет клиента по почте без учёта регистра букв: клиент мог написать `Ann@Example.com`. Без индекса по выражению — перебор:

```sql

EXPLAIN
SELECT id,
       email
FROM users
WHERE lower(email) = 'user1@example.com';
```

```text
 Seq Scan on users  (cost=0.00..136.07 rows=25 width=41)
   Filter: (lower(email) = 'user1@example.com'::text)
```

Тот же запрос после создания индекса по `lower(email)`. Индекс создаётся внутри транзакции и откатывается `ROLLBACK`, чтобы учебная база не изменилась:

```sql

BEGIN;

CREATE INDEX idx_tmp_users_lower_email ON users ((lower(email)));

EXPLAIN
SELECT id,
       email
FROM users
WHERE lower(email) = 'user1@example.com';

ROLLBACK;
```

```text
 Bitmap Heap Scan on users  (cost=4.48..51.89 rows=25 width=41)
   Recheck Cond: (lower(email) = 'user1@example.com'::text)
   ->  Bitmap Index Scan on idx_tmp_users_lower_email  (cost=0.00..4.47 rows=25 width=0)
         Index Cond: (lower(email) = 'user1@example.com'::text)
```

**Как читается запрос**

- `BEGIN` — начинается транзакция. Всё до `ROLLBACK` будет отменено.
- `CREATE INDEX idx_tmp_users_lower_email ON users ((lower(email)))` — индекс хранит не почту как есть, а её вариант строчными буквами.
- `EXPLAIN …` — теперь в плане `Index Cond: (lower(email) = …)`: выражение условия совпало с выражением индекса.
- `ROLLBACK` — транзакция отменяется, индекс исчезает.

### Не каждое выражение можно индексировать

**Задача.** Разработчик пытается построить индекс по дате заказа, чтобы ускорить условие `created_at::date = '…'`.

```sql

BEGIN;

CREATE INDEX idx_tmp_orders_day ON orders ((created_at::date));

ROLLBACK;
```

```text
ERROR:  functions in index expression must be marked IMMUTABLE
```

**Как читается ошибка**

- `IMMUTABLE` — пометка функции: при одних и тех же входных данных она всегда возвращает один и тот же результат.
- `created_at::date` для `timestamptz` зависит от часового пояса сессии: одно и то же мгновение в UTC — 1 июня, а в Москве — уже 2 июня. Результат может меняться, поэтому индекс по такому выражению был бы неверным, и PostgreSQL его запрещает.
- Решение — условие с границами периода из прошлого раздела. Оно работает с обычным индексом по `created_at`.

---

## Составной индекс: порядок столбцов важен

Составной индекс строится по нескольким столбцам сразу. Значения сортируются сначала по первому столбцу, внутри одинаковых — по второму. Как в телефонной книге: сначала по фамилии, внутри фамилии — по имени.

У таблицы попыток оплаты есть индекс `idx_payment_attempts_status_created` по `(status, created_at)`. Записи в нём лежат так: все `failed` по времени, затем все `success` по времени.

**Задача.** Платёжная команда ищет неудачные попытки за 1 июня 2026 года.

```sql

EXPLAIN
SELECT id
FROM payment_attempts
WHERE status = 'failed'
  AND created_at >= '2026-06-01'
  AND created_at < '2026-06-02';
```

```text
 Index Scan using idx_payment_attempts_status_created on payment_attempts  (cost=0.42..43.20 rows=16 width=8)
   Index Cond: ((status = 'failed'::text) AND (created_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (created_at < '2026-06-02 00:00:00+00'::timestamp with time zone))
```

**Как читается план**

- `Index Cond` содержит оба условия: PostgreSQL переходит в часть индекса с `failed`, а внутри неё — сразу к 1 июня.
- `rows=16` — нужно прочитать всего около 16 строк.

**Задача.** Та же команда ищет **все** попытки за 1 июня, без условия на статус.

```sql

EXPLAIN
SELECT id
FROM payment_attempts
WHERE created_at >= '2026-06-01'
  AND created_at < '2026-06-02';
```

```text
 Seq Scan on payment_attempts  (cost=0.00..2467.00 rows=287 width=8)
   Filter: ((created_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (created_at < '2026-06-02 00:00:00+00'::timestamp with time zone))
```

**Как читается план**

- Нужно всего около 287 строк, но в плане `Seq Scan`.
- В индексе `(status, created_at)` попытки за 1 июня лежат в двух разных местах: среди `failed` и среди `success`. Время упорядочено только внутри каждого статуса. Без условия на первый столбец индекс пришлось бы просматривать целиком.
- Индекса, где `created_at` стоит первым, у этой таблицы нет. Поэтому выбран перебор.

**Источник:** https://www.postgresql.org/docs/16/indexes-multicolumn.html

**EN:**

> A multicolumn B-tree index can be used with query conditions that involve any subset of the index's columns, but the index is most efficient when there are constraints on the leading (leftmost) columns. The exact rule is that equality constraints on leading columns, plus any inequality constraints on the first column that does not have an equality constraint, will be used to limit the portion of the index that is scanned.
>
> This index could in principle be used for queries that have constraints on `b` and/or `c` with no constraint on `a` — but the entire index would have to be scanned, so in most cases the planner would prefer a sequential table scan over using the index.

**RU:**

> Составной индекс B-tree можно использовать с условиями запроса, которые затрагивают любое подмножество столбцов индекса, но эффективнее всего он работает, когда есть условия на ведущие (крайние левые) столбцы. Точное правило таково: условия равенства на ведущие столбцы плюс условия неравенства на первый столбец, для которого нет условия равенства, используются для ограничения той части индекса, которую нужно просмотреть.
>
> Этот индекс в принципе можно использовать для запросов с условиями на `b` и/или `c` без условия на `a` — но тогда пришлось бы просмотреть весь индекс, поэтому в большинстве случаев планировщик предпочтёт последовательный просмотр таблицы использованию индекса.

**Нюансы**

- Первым в составной индекс ставят столбец, по которому чаще всего ищут на **равенство**: `status = 'failed'`. Столбец с диапазоном (`created_at >= …`) — следующим.
- Индекс `(status, created_at)` заменяет отдельный индекс по `status`: условие только на `status` использует его левую часть.

---

## Создать индекс и проверить план

**Задача.** Платёжная команда часто ищет все попытки за день. Разработчик проверяет, поможет ли отдельный индекс по `created_at`. Индекс создаётся в транзакции и откатывается.

```sql

BEGIN;

CREATE INDEX idx_tmp_pa_created_at ON payment_attempts (created_at);

EXPLAIN
SELECT id
FROM payment_attempts
WHERE created_at >= '2026-06-01'
  AND created_at < '2026-06-02';

ROLLBACK;
```

```text
 Bitmap Heap Scan on payment_attempts  (cost=7.23..625.37 rows=287 width=8)
   Recheck Cond: ((created_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (created_at < '2026-06-02 00:00:00+00'::timestamp with time zone))
   ->  Bitmap Index Scan on idx_tmp_pa_created_at  (cost=0.00..7.16 rows=287 width=0)
         Index Cond: ((created_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (created_at < '2026-06-02 00:00:00+00'::timestamp with time zone))
```

**Как читается запрос**

- `CREATE INDEX idx_tmp_pa_created_at ON payment_attempts (created_at)` — индекс, где `created_at` — первый и единственный столбец.
- `EXPLAIN …` — тот же запрос, что дал `Seq Scan` в прошлом разделе, теперь читает таблицу через новый индекс.
- Стоимость упала с `2467.00` до `625.37`.
- `ROLLBACK` — индекс отменяется. Чтобы оставить его навсегда, команду выполняют без `BEGIN` и `ROLLBACK`.

**Нюансы**

- Обычный `CREATE INDEX` на время построения блокирует запись в таблицу. На рабочей базе используют `CREATE INDEX CONCURRENTLY`: строится дольше, но не мешает вставкам. `CONCURRENTLY` нельзя выполнять внутри `BEGIN … ROLLBACK`.
- Индекс решает задачу, только если такие запросы действительно частые. Каждый индекс замедляет запись — об этом раздел [«Чем платят за индекс»](#чем-платят-за-индекс).

---

## LIKE по началу строки

**Задача.** Служба поддержки ищет клиентов, у которых почта начинается на `user12`.

```sql

EXPLAIN
SELECT id,
       email
FROM users
WHERE email LIKE 'user12%';
```

```text
 Seq Scan on users  (cost=0.00..123.56 rows=101 width=41)
   Filter: (email ~~ 'user12%'::text)
```

**Как читается план**

- `~~` — внутренняя запись оператора `LIKE`.
- Индекс по `email` есть, но выбран `Seq Scan`.
- Причина — правила сортировки текста базы. База использует `en_US.utf8`: строки сравниваются по правилам языка, а не по кодам символов. Для таких правил обычный индекс не умеет искать «всё, что начинается на `user12`».

Индекс с классом операторов `text_pattern_ops` сравнивает строки посимвольно и подходит для `LIKE 'начало%'`:

```sql

BEGIN;

CREATE INDEX idx_tmp_users_email_pattern ON users (email text_pattern_ops);

EXPLAIN
SELECT id,
       email
FROM users
WHERE email LIKE 'user12%';

ROLLBACK;
```

```text
 Bitmap Heap Scan on users  (cost=5.30..69.86 rows=101 width=41)
   Filter: (email ~~ 'user12%'::text)
   ->  Bitmap Index Scan on idx_tmp_users_email_pattern  (cost=0.00..5.27 rows=99 width=0)
         Index Cond: ((email ~>=~ 'user12'::text) AND (email ~<~ 'user13'::text))
```

**Как читается план**

- `email text_pattern_ops` — после имени столбца указан класс операторов: как индекс сравнивает значения.
- `Index Cond: ((email ~>=~ 'user12') AND (email ~<~ 'user13'))` — PostgreSQL превратил `LIKE 'user12%'` в диапазон: от `user12` включительно до `user13` не включительно. Все строки, начинающиеся на `user12`, лежат в индексе внутри этого диапазона.
- `Filter` — затем каждая найденная строка дополнительно проверяется исходным `LIKE`.

**Источник:** https://www.postgresql.org/docs/16/indexes-opclass.html

**EN:**

> The operator classes `text_pattern_ops`, `varchar_pattern_ops`, and `bpchar_pattern_ops` support B-tree indexes on the types `text`, `varchar`, and `char` respectively. The difference from the default operator classes is that the values are compared strictly character by character rather than according to the locale-specific collation rules. This makes these operator classes suitable for use by queries involving pattern matching expressions (`LIKE` or POSIX regular expressions) when the database does not use the standard “C” locale.

**RU:**

> Классы операторов `text_pattern_ops`, `varchar_pattern_ops` и `bpchar_pattern_ops` поддерживают индексы B-tree для типов `text`, `varchar` и `char` соответственно. Отличие от классов операторов по умолчанию в том, что значения сравниваются строго посимвольно, а не по правилам сортировки конкретного языка. Поэтому такие классы операторов подходят для запросов с сопоставлением по шаблону (`LIKE` или регулярные выражения POSIX), когда база не использует стандартную локаль «C».

**Нюансы**

- `LIKE '%user12'` — шаблон с `%` в начале — B-tree не ускорит ни с каким классом операторов: неизвестно, с чего начинается строка, искать диапазон негде.
- Индекс с `text_pattern_ops` не подходит для обычной сортировки и сравнений `<` и `>`. Если они тоже нужны, держат два индекса.

---

## Индекс для ORDER BY и LIMIT

Индекс B-tree хранит значения по порядку. Поэтому запрос «последние N» может просто прочитать первые N записей индекса — без сортировки всей таблицы.

**Задача.** Лента операционного менеджера показывает пять последних созданных заказов.

```sql

EXPLAIN
SELECT id,
       created_at
FROM orders
ORDER BY created_at DESC
LIMIT 5;
```

```text
 Limit  (cost=0.29..0.70 rows=5 width=16)
   ->  Index Scan Backward using idx_orders_created_at on orders  (cost=0.29..8056.40 rows=100007 width=16)
```

**Как читается план**

- `Index Scan Backward` — индекс по `created_at` читается с конца: от самого позднего времени к раннему. Это и есть `ORDER BY created_at DESC`.
- `Limit` — после пяти строк чтение останавливается. Стоимость всего плана — `0.70`, хотя индекс рассчитан на все 100007 строк.

**Задача.** Тот же менеджер просит пять самых дорогих заказов. Индекса по сумме нет.

```sql

EXPLAIN
SELECT id,
       amount
FROM orders
ORDER BY amount DESC
LIMIT 5;
```

```text
 Limit  (cost=3889.40..3889.97 rows=5 width=14)
   ->  Gather Merge  (cost=3889.40..10654.62 rows=58828 width=14)
         Workers Planned: 1
         ->  Sort  (cost=2889.39..3036.46 rows=58828 width=14)
               Sort Key: amount DESC
               ->  Parallel Seq Scan on orders  (cost=0.00..1912.28 rows=58828 width=14)
```

**Как читается план**

- `Parallel Seq Scan on orders` — таблица читается целиком, в два процесса: основной и один дополнительный (`Workers Planned: 1`).
- `Sort` с `Sort Key: amount DESC` — каждый процесс сортирует свои строки по сумме.
- `Gather Merge` — отсортированные части сливаются в один порядок.
- `Limit` — берутся первые пять.
- Стоимость — `3889.97` против `0.70` в плане с индексом.

**Нюансы**

- Составной индекс `(status, created_at)` тоже подходит для «последние 10 заказов со статусом `pending`»: внутри одного статуса время уже упорядочено.
- Индекс помогает `ORDER BY`, только если порядок сортировки совпадает с порядком индекса или полностью противоположен ему.

---

## Индекс в соединении таблиц

**Задача.** Карточка клиента 352 показывает историю статусов всех его заказов.

```sql

EXPLAIN
SELECT o.id,
       e.new_status,
       e.occurred_at
FROM orders o
JOIN order_status_events e ON e.order_id = o.id
WHERE o.user_id = 352;
```

```text
 Nested Loop  (cost=5.08..562.30 rows=117 width=25)
   ->  Bitmap Heap Scan on orders o  (cost=4.66..166.68 rows=47 width=8)
         Recheck Cond: (user_id = 352)
         ->  Bitmap Index Scan on idx_orders_user_id  (cost=0.00..4.65 rows=47 width=0)
               Index Cond: (user_id = 352)
   ->  Index Scan using idx_order_status_events_order_id on order_status_events e  (cost=0.42..8.39 rows=3 width=25)
         Index Cond: (order_id = o.id)
```

**Как читается план**

- `Nested Loop` — вложенный цикл: для каждой строки из верхнего входа выполняется поиск в нижнем.
- Верхний вход — `Bitmap Heap Scan on orders o`: по индексу `idx_orders_user_id` находятся заказы клиента 352. По оценке — 47, на самом деле — 33.
- Нижний вход — `Index Scan using idx_order_status_events_order_id`: для **каждого** найденного заказа по индексу ищутся его события. `Index Cond: (order_id = o.id)` — значение `o.id` подставляется из текущего заказа.
- Без индекса по `order_status_events.order_id` для каждого заказа пришлось бы перебирать все 249966 событий.

**Нюансы**

- Столбец, по которому таблицы соединяют, — `order_id`, `user_id`, `product_id` — почти всегда стоит индексировать. Первичный ключ проиндексирован автоматически, а ссылающийся на него столбец — **нет**.

---

## Чем платят за индекс

**Задача.** Администратор базы проверяет, сколько места занимают таблица заказов и её индексы.

```sql

SELECT c.relname,
       pg_size_pretty(pg_relation_size(c.oid)) AS size
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'reactive_study'
  AND c.relname IN ('orders', 'orders_pkey', 'idx_orders_created_at',
                    'idx_orders_status_created_at', 'idx_orders_user_id',
                    'idx_orders_product_id')
ORDER BY pg_relation_size(c.oid) DESC;
```

**Как читается запрос**

- `pg_class` — служебная таблица: одна строка — одна таблица, индекс или другой объект базы. `relname` — его имя.
- `pg_namespace` — схемы. `JOIN … ON n.oid = c.relnamespace` связывает объект с его схемой.
- `pg_relation_size(c.oid)` — размер объекта на диске в байтах.
- `pg_size_pretty(…)` — тот же размер в удобном виде: `kB`, `MB`.

| relname | size |
|---|---|
| orders | 10 MB |
| idx_orders_status_created_at | 4632 kB |
| idx_orders_created_at | 2520 kB |
| orders_pkey | 2208 kB |
| idx_orders_user_id | 1168 kB |
| idx_orders_product_id | 1032 kB |

Пять индексов вместе занимают около 11 МБ — больше, чем сама таблица.

**Источник:** https://www.postgresql.org/docs/16/indexes-intro.html

**EN:**

> After an index is created, the system has to keep it synchronized with the table. This adds overhead to data manipulation operations. Indexes can also prevent the creation of heap-only tuples. Therefore indexes that are seldom or never used in queries should be removed.

**RU:**

> После создания индекса система обязана поддерживать его в соответствии с таблицей. Это добавляет накладные расходы к операциям изменения данных. Индексы также могут мешать созданию heap-only-кортежей. Поэтому индексы, которые редко или никогда не используются в запросах, следует удалять.

**Нюансы**

- Каждый `INSERT` в `orders` записывает строку в таблицу и ещё по одной записи в каждый из пяти индексов.
- Индекс по столбцу, который часто меняется (например, `status`), обновляется при каждой смене значения.
- Индексы добавляют под реальные частые запросы, а не «на всякий случай».

---

## Что обычно спрашивают на собеседованиях

- Что такое индекс и почему он ускоряет поиск.
- Почему PostgreSQL выбрал `Seq Scan`, хотя индекс есть.
- Почему `WHERE created_at::date = …` или `WHERE lower(email) = …` не использует индекс и как это исправить.
- Как работает составной индекс и почему важен порядок столбцов.
- Чем `Index Scan` отличается от `Bitmap Heap Scan` и `Index Only Scan`.
- Чем `EXPLAIN` отличается от `EXPLAIN ANALYZE` и почему `EXPLAIN ANALYZE` опасен для `DELETE`.
- Почему `LIKE '%text'` не ускоряется индексом.
- Чем вреден лишний индекс.
- Нужен ли индекс на внешний ключ.

---

## Карта выбора

| Ситуация | Что сделать |
|---|---|
| Частый поиск по одному столбцу на равенство или диапазон | B-tree по этому столбцу |
| Частый поиск по двум столбцам: один на равенство, другой диапазоном | составной индекс: сначала столбец равенства, потом диапазона |
| В условии функция над столбцом | переписать условие, чтобы столбец стоял «голым» |
| Переписать нельзя | индекс по тому же выражению, если функция `IMMUTABLE` |
| Период по дате у `timestamptz` | `>= начало AND < начало следующего периода` |
| `LIKE 'начало%'` в базе не с локалью C | индекс с `text_pattern_ops` |
| `LIKE '%середина%'` | B-tree не поможет |
| «Последние N» по времени | индекс по столбцу сортировки |
| Столбец соединения таблиц | индекс по ссылающемуся столбцу |
| Условие отбирает большую часть таблицы | индекс не нужен, `Seq Scan` нормален |

---

## Что попробовать самостоятельно

Откройте SQL-клиент и выполните `SET search_path TO reactive_study`.

**1.**

**Ситуация:** карточка клиента открывает все его заказы. Разработчик хочет убедиться, что запрос использует индекс.

- Снимите план без выполнения запроса для поиска всех заказов клиента с номером 1200.
- Назовите способ чтения таблицы и индекс, который используется.

Ответ для сверки:

```text
 Bitmap Heap Scan on orders  (cost=4.45..77.32 rows=20 width=68)
   Recheck Cond: (user_id = 1200)
   ->  Bitmap Index Scan on idx_orders_user_id  (cost=0.00..4.44 rows=20 width=0)
         Index Cond: (user_id = 1200)
```

**2.**

**Ситуация:** служба поддержки открывает историю статусов заказа 10.

- В журнале `order_status_events` одна строка — одна смена статуса. Номер заказа — в столбце `order_id`.
- Снимите план для выбора нового статуса и времени смены по заказу 10.

Ответ для сверки:

```text
 Index Scan using idx_order_status_events_order_id on order_status_events  (cost=0.42..8.47 rows=3 width=17)
   Index Cond: (order_id = 10)
```

**3.**

**Ситуация:** продакт-менеджер ежедневно смотрит отмены за вчерашний день.

- В журнале `order_status_events` столбец `new_status` — новый статус, `occurred_at` — время смены.
- Нужны номера заказов, которые перешли в статус `cancelled` 1 июня 2026 года.
- Снимите план. Затем внутри транзакции с откатом создайте индекс, который подходит под этот запрос, и снимите план ещё раз.

Ответ для сверки — план без нового индекса:

```text
 Bitmap Heap Scan on order_status_events  (cost=22.98..1613.20 rows=52 width=8)
   Recheck Cond: ((occurred_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-06-02 00:00:00+00'::timestamp with time zone))
   Filter: (new_status = 'cancelled'::text)
   ->  Bitmap Index Scan on idx_order_status_events_occurred_at  (cost=0.00..22.97 rows=655 width=0)
         Index Cond: ((occurred_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-06-02 00:00:00+00'::timestamp with time zone))
```

План с составным индексом по статусу и времени:

```text
 Bitmap Heap Scan on order_status_events  (cost=5.08..193.50 rows=52 width=8)
   Recheck Cond: ((new_status = 'cancelled'::text) AND (occurred_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-06-02 00:00:00+00'::timestamp with time zone))
   ->  Bitmap Index Scan on idx_tmp_events_status_time  (cost=0.00..5.07 rows=52 width=0)
         Index Cond: ((new_status = 'cancelled'::text) AND (occurred_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-06-02 00:00:00+00'::timestamp with time zone))
```

- Без нового индекса по времени находятся 655 событий за день, из них условием `Filter` отбрасываются все, кроме отмен.
- С составным индексом сразу находятся только 52 отмены.
