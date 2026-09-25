# Тема 08 — CTE: `WITH … AS`

> **Статус:** теория · **Следующий шаг:** изучить материал → написать в чат *«дай интервью по теме 8»*

Тема опирается на темы 03, 06 и 07: `count`, `sum`, `GROUP BY`, подзапрос, `FILTER` / `CASE`. Оконные функции здесь не нужны.

В каждом разделе сначала таблица на вымышленных заказах. Затем тот же смысл на учебной базе `reactive_study`.

---

## Оглавление

- [Как читать статусы](#как-читать-статусы)
- [Слово CTE](#слово-cte)
- [Зачем имя, если есть подзапрос](#зачем-имя-если-есть-подзапрос)
- [Два имени подряд](#два-имени-подряд)
- [Имя можно прочитать дважды](#имя-можно-прочитать-дважды)
- [Имя живёт только в этом запросе](#имя-живёт-только-в-этом-запросе)
- [WITH RECURSIVE: следующий номер](#with-recursive-следующий-номер)
- [Типичные ошибки](#типичные-ошибки)
- [Что попробовать самостоятельно](#что-попробовать-самостоятельно)

---

## Как читать статусы

**Заказы**

| В тексте по-русски | В базе | Что происходит с заказом |
|---|---|---|
| ждёт обработки | `pending` | заказ принят, его ещё не начали собирать |
| собирают | `processing` | заказ собирают на складе |
| отправлено | `shipped` | заказ уже уехал к покупателю, но ещё не вручён |
| доставлено | `delivered` | заказ уже у покупателя |
| отменено | `cancelled` | заказ отменили |

«Доставлено» — это только `delivered`. «Отправлено» — только `shipped`.

**Попытки оплаты**

| В тексте по-русски | В базе | Что произошло |
|---|---|---|
| деньги списались | `success` | попытка оплаты прошла |
| списание не удалось | `failed` | попытка оплаты не прошла |

---

## Слово CTE

`WITH имя AS (запрос)` даёт промежуточному запросу **имя**. Дальше основной `SELECT` читает это имя так же, как таблицу. Имя живёт только внутри этого запроса.

**Источник:** https://www.postgresql.org/docs/current/queries-with.html

EN:

> `WITH` provides a way to write auxiliary statements for use in a larger query. These statements, which are often referred to as Common Table Expressions or CTEs, can be thought of as defining temporary tables that exist just for one query.

RU:

> `WITH` даёт способ написать вспомогательные запросы для использования в более крупном запросе. Такие запросы часто называют Common Table Expressions, или CTE. Их можно представлять как временные таблицы, которые существуют только на время одного запроса.

### Как это выглядит в магазине одежды

Четыре заказа магазина «Север»:

| id | status | amount |
|---|---|---|
| 1 | delivered | 1000 |
| 2 | delivered | 500 |
| 3 | cancelled | 700 |
| 4 | shipped | 200 |

Сначала именуют только доставленные строки. Потом считают их.

```sql

WITH delivered_orders AS (
    SELECT id, amount
    FROM orders
    WHERE status = 'delivered'
)
SELECT count(*)              AS delivered,
       round(sum(amount), 2) AS delivered_revenue
FROM delivered_orders;
```

Имя `delivered_orders` внутри содержит две строки:

| id | amount |
|---|---|
| 1 | 1000 |
| 2 | 500 |

Основной `SELECT` читает только их:

| delivered | delivered_revenue |
|---|---|
| 2 | 1500.00 |

### Как это выглядит в учебной базе

```sql

WITH delivered_orders AS (
    SELECT id, amount
    FROM orders
    WHERE status = 'delivered'
)
SELECT count(*)              AS delivered,
       round(sum(amount), 2) AS delivered_revenue
FROM delivered_orders;
```

| delivered | delivered_revenue |
|---|---|
| 20076 | 20121989.26 |

---

## Зачем имя, если есть подзапрос

Тот же отчёт можно написать подзапросом в `FROM`. Числа совпадут. `WITH` нужен не ради другого ответа, а ради шагов: сначала назвать кусок, потом читать его сверху вниз.

**Источник:** https://www.postgresql.org/docs/current/queries-with.html

EN:

> The basic value of `SELECT` in `WITH` is to break down complicated queries into simpler parts. […] This example could have been written without `WITH`, but we'd have needed two levels of nested sub-`SELECT`s. It's a bit easier to follow this way.

RU:

> Основная ценность `SELECT` внутри `WITH` — разбить сложный запрос на более простые части. […] Этот пример можно было написать и без `WITH`, но тогда понадобились бы два уровня вложенных подзапросов `SELECT`. Так читать чуть проще.

### Как это выглядит в магазине одежды

Тот же «Север», тот же ответ `2` и `1500.00`, но без имени:

```sql

SELECT count(*)              AS delivered,
       round(sum(amount), 2) AS delivered_revenue
FROM (
    SELECT id, amount
    FROM orders
    WHERE status = 'delivered'
) delivered_orders;
```

| delivered | delivered_revenue |
|---|---|
| 2 | 1500.00 |

### Как это выглядит в учебной базе

```sql

SELECT count(*)              AS delivered,
       round(sum(amount), 2) AS delivered_revenue
FROM (
    SELECT id, amount
    FROM orders
    WHERE status = 'delivered'
) delivered_orders;
```

| delivered | delivered_revenue |
|---|---|
| 20076 | 20121989.26 |

Числа те же, что в разделе про слово CTE.

---

## Два имени подряд

Имена перечисляют через запятую. Второе имя может читать первое. Основной `SELECT` читает уже второе.

### Как это выглядит в магазине одежды

Сначала считают, сколько заказов в каждом статусе. Потом оставляют только статусы, где заказов больше одного.

Первое имя `by_status`:

| status | total |
|---|---|
| cancelled | 1 |
| delivered | 2 |
| shipped | 1 |

Второе имя `large_statuses` оставляет строку, где `total > 1`:

| status | total |
|---|---|
| delivered | 2 |

```sql

WITH by_status AS (
    SELECT status,
           count(*) AS total
    FROM orders
    GROUP BY status
),
large_statuses AS (
    SELECT status, total
    FROM by_status
    WHERE total > 1
)
SELECT status, total
FROM large_statuses
ORDER BY status;
```

| status | total |
|---|---|
| delivered | 2 |

### Как это выглядит в учебной базе

Порог здесь — больше **20000** заказов в статусе.

```sql

WITH by_status AS (
    SELECT status,
           count(*) AS total
    FROM orders
    GROUP BY status
),
large_statuses AS (
    SELECT status, total
    FROM by_status
    WHERE total > 20000
)
SELECT status, total
FROM large_statuses
ORDER BY status;
```

| status | total |
|---|---|
| cancelled | 20071 |
| delivered | 20076 |
| processing | 20081 |

Статусы `pending` (19946) и `shipped` (19833) во второе имя не прошли: там заказов не больше 20000.

---

## Имя можно прочитать дважды

Одно и то же имя читают и чтобы взять среднее, и чтобы сравнить с ним каждую строку. Подзапрос с тем же текстом пришлось бы писать два раза.

### Как это выглядит в магазине одежды

Средняя сумма четырёх заказов «Севера»: `(1000 + 500 + 700 + 200) / 4 = 600`.

Имя `order_avg` содержит одну строку:

| avg_amount |
|---|
| 600 |

Заказы дороже 600 — это заказ 1 (1000) и заказ 3 (700).

```sql

WITH order_avg AS (
    SELECT avg(amount) AS avg_amount
    FROM orders
)
SELECT count(*) AS above_avg
FROM orders o
JOIN order_avg a ON o.amount > a.avg_amount;
```

| above_avg |
|---|
| 2 |

### Как это выглядит в учебной базе

Средняя сумма заказа в базе — `1007.38`.

```sql

WITH order_avg AS (
    SELECT avg(amount) AS avg_amount
    FROM orders
)
SELECT count(*) AS above_avg
FROM orders o
JOIN order_avg a ON o.amount > a.avg_amount;
```

| above_avg |
|---|
| 49971 |

---

## Имя живёт только в этом запросе

После точки с запятой имени `delivered_orders` в базе нет. Это не `CREATE TABLE`. Следующий запрос `SELECT * FROM delivered_orders` база не найдёт.

На «Севере» и в учебной базе это одно и то же правило: имя видно только тому `SELECT`, который стоит сразу после `WITH`.

---

## WITH RECURSIVE: следующий номер

Обычный `WITH` не может читать сам себя. `WITH RECURSIVE` может: первый шаг даёт стартовую строку, каждый следующий шаг читает уже полученные строки и дописывает новые, пока условие шага даёт `true`.

**Источник:** https://www.postgresql.org/docs/current/queries-with.html

EN:

> The optional `RECURSIVE` modifier changes `WITH` from a mere syntactic convenience into a feature that accomplishes things not otherwise possible in standard SQL. Using `RECURSIVE`, a `WITH` query can refer to its own output.

RU:

> Необязательное слово `RECURSIVE` превращает `WITH` из удобной записи в средство, которым в обычном SQL иначе не сделать задуманное. С `RECURSIVE` запрос в `WITH` может ссылаться на свой собственный результат.

### Как это выглядит на числах

Нужны номера от 1 до 5.

```sql

WITH RECURSIVE numbers AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1
    FROM numbers
    WHERE n < 5
)
SELECT n
FROM numbers
ORDER BY n;
```

| n |
|---|
| 1 |
| 2 |
| 3 |
| 4 |
| 5 |

- Первый шаг даёт строку `1`.
- Пока `n < 5` даёт `true`, следующий шаг берёт текущее `n` и пишет `n + 1`.
- Когда `n` становится `5`, условие `n < 5` даёт `false`. Новых строк нет, разбор останавливается.

Этот запрос не читает таблицы магазина. В учебной базе он даёт ту же таблицу `1 … 5`.

В магазине такой приём берут, когда есть дерево: отдел внутри отдела, деталь внутри детали. В `orders` и `payment_attempts` такого дерева нет, поэтому на учебных таблицах рекурсию дальше не разбираем.

---

## Типичные ошибки

На магазине «Север» ошибка видна сразу. Нужная сводка по доставленным — `2` и `1500.00`.

| Ошибка | Что окажется в результате |
|---|---|
| забыть основной `SELECT` после `WITH` | база не поймёт запрос: имя объявили, а читать его некому |
| написать `SELECT * FROM delivered_orders` вторым запросом | имени уже нет. Оно жило только в предыдущем запросе |
| во втором имени сослаться на себя без `RECURSIVE` | обычный `WITH` сам себя читать не умеет |
| во втором имени поставить порог так, что не останется строк | на «Севере» при `total > 2` второе имя пустое, основной `SELECT` вернёт 0 строк |

---

## Что попробовать самостоятельно

Откройте свой SQL-клиент и выполните `SET search_path TO reactive_study`. Дальше три отдельных запроса. В каждом запросе промежуточный шаг назовите через `WITH`.

**1.** Напишите запрос к таблице заказов `orders`.

- Сначала соберите в именованный шаг только заказы со статусом `processing`.
  - `processing` значит, что заказ сейчас собирают на складе.
- Затем из этого шага получите одну строку из двух полей.
  - Первое поле — сколько заказов со статусом `processing`.
  - Второе поле — сумма столбца `amount` только у заказов со статусом `processing`.

Ниже числа для сверки первого запроса.

| processing | processing_sum |
|---|---|
| 20081 | 20326718.59 |

**2.** Напишите запрос к таблице заказов `orders`.

- Первый именованный шаг считает, сколько заказов имеет каждый статус.
- Второй именованный шаг читает первый и оставляет только статусы, в которых заказов больше **20000**.
- Основной `SELECT` читает второй шаг.
- Поля результата:
  - статус заказа;
  - сколько заказов имеют этот статус.

Ниже числа для сверки второго запроса. Порядок строк не важен.

| status | total |
|---|---|
| cancelled | 20071 |
| delivered | 20076 |
| processing | 20081 |

**3.** Напишите запрос к таблице `payment_attempts`.

- Каждая строка этой таблицы — одна попытка списать деньги по заказу, поэтому число попыток — это число строк.
- Столбец `id` — уникальный номер этой строки.
- В столбце `status` записано, чем закончилась попытка.
  - `success` значит, что деньги списались.
  - `failed` значит, что списание не удалось.
- Сначала соберите в именованный шаг только попытки со статусом `success`.
- Затем из этого шага получите одну строку из двух полей.
  - Первое поле — сколько попыток оплаты имеют статус `success`.
  - Второе поле — сумма столбца `amount` у попыток оплаты со статусом `success`.

Ниже числа для сверки третьего запроса.

| success_cnt | success_sum |
|---|---|
| 95884 | 96612590.20 |
