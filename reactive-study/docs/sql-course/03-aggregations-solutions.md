# Тема 03 — принятые решения практики

Ваши принятые запросы из чата курса, в той редакции, которую зачли.
Это не эталонный ключ: если было несколько рабочих вариантов, здесь стоит принятый.
Формулировки заданий — в [`03-aggregations-tasks.md`](03-aggregations-tasks.md).

```sql

SET search_path TO reactive_study;
```

## Оглавление

- [Задание 1 — дашборд руководства](#задание-1--дашборд-руководства)
- [Задание 2 — нагрузка по статусам](#задание-2--нагрузка-по-статусам)
- [Задание 3 — средний чек по статусам](#задание-3--средний-чек-по-статусам)
- [Задание 4 — выручка только доставленных](#задание-4--выручка-только-доставленных)
- [Задание 5 — заказы с привязкой к каталогу](#задание-5--заказы-с-привязкой-к-каталогу)
- [Задание 6 — «тяжёлые» статусы](#задание-6--тяжёлые-статусы)
- [Задание 7 — крупные заказы на проверке](#задание-7--крупные-заказы-на-проверке)
- [Задание 8 — самый ценный клиент](#задание-8--самый-ценный-клиент)
- [Задание 9 — диапазон сумм по статусам](#задание-9--диапазон-сумм-по-статусам)
- [Задание 10 — самые активные покупатели](#задание-10--самые-активные-покупатели)

## Задание 1 — дашборд руководства

```sql

SELECT count(*) AS cnt, sum(amount) AS rros FROM orders;
```


## Задание 2 — нагрузка по статусам

```sql

SELECT status, count(*) AS cnt FROM orders GROUP BY status ORDER BY cnt DESC, status;
```


## Задание 3 — средний чек по статусам

```sql

SELECT status, avg(amount) AS avg_amount FROM orders GROUP BY status ORDER BY avg_amount DESC;
```


## Задание 4 — выручка только доставленных

```sql

SELECT count(*) AS cnt, sum(amount) AS sum_amount FROM orders WHERE status = 'delivered';

-- или

SELECT status, count(*) AS cnt, sum(amount) AS sum_amount
FROM orders
WHERE status = 'delivered'
GROUP BY status;
```


## Задание 5 — заказы с привязкой к каталогу

```sql

SELECT count(product_id) AS with_catalog, count(*) AS total_orders FROM orders;
```


## Задание 6 — «тяжёлые» статусы

```sql

SELECT status, count(*) AS cnt FROM orders GROUP BY status HAVING count(*) > 18000 ORDER BY cnt DESC;
```


## Задание 7 — крупные заказы на проверке

```sql

SELECT status, count(*) AS cnt, avg(amount) AS avg_amount
FROM orders
WHERE amount >= 1000
GROUP BY status
HAVING avg(amount) >= 1500
ORDER BY avg_amount DESC;
```


## Задание 8 — самый ценный клиент

```sql

SELECT user_id, sum(amount) AS sum_amount
FROM orders
GROUP BY user_id
ORDER BY sum_amount DESC, user_id
LIMIT 1;
```


## Задание 9 — диапазон сумм по статусам

```sql

SELECT status, max(amount), min(amount) FROM orders GROUP BY status ORDER BY status;
```


## Задание 10 — самые активные покупатели

```sql

SELECT user_id, count(*) AS cnt FROM orders GROUP BY user_id ORDER BY cnt DESC, user_id LIMIT 5;
```
