# Тема 07 — принятые решения практики

Ваши принятые запросы из чата курса, в той редакции, которую зачли.
Это не эталонный ключ: если было несколько рабочих вариантов, здесь стоит принятый.
Формулировки заданий — в [`07-conditional-aggregates-tasks.md`](07-conditional-aggregates-tasks.md).

```sql

SET search_path TO reactive_study;
```

## Оглавление

- [Задание 1 — строка дашборда по заказам](#задание-1--строка-дашборда-по-заказам)
- [Задание 2 — все статусы заказов в одной строке](#задание-2--все-статусы-заказов-в-одной-строке)
- [Задание 3 — доля доставленных заказов](#задание-3--доля-доставленных-заказов)
- [Задание 4 — доставленное и потерянное](#задание-4--доставленное-и-потерянное)
- [Задание 5 — средняя сумма доставленного заказа](#задание-5--средняя-сумма-доставленного-заказа)
- [Задание 6 — крупные заказы внутри каждого статуса](#задание-6--крупные-заказы-внутри-каждого-статуса)
- [Задание 7 — доля успешных списаний](#задание-7--доля-успешных-списаний)
- [Задание 8 — первая неудача и третья удача](#задание-8--первая-неудача-и-третья-удача)
- [Задание 9 — первая и повторные попытки по статусу](#задание-9--первая-и-повторные-попытки-по-статусу)
- [Задание 10 — деньги по статусу списания](#задание-10--деньги-по-статусу-списания)

## Задание 1 — строка дашборда по заказам

```sql

SELECT count(*) AS count_orders_all,
       count(CASE WHEN o.status = 'delivered' THEN 1 END) AS count_delivered,
       count(o.id) FILTER (WHERE o.status = 'cancelled') AS count_cancelled,
       sum(CASE WHEN o.status = 'delivered' THEN o.amount END) AS sum_delivered
FROM orders o;
```


## Задание 2 — все статусы заказов в одной строке

```sql

SELECT count(*) FILTER (WHERE o.status = 'pending') AS order_pending,
       count(CASE WHEN o.status = 'processing' THEN 1 END) AS order_processing,
       count(o.id) FILTER (WHERE o.status = 'shipped') AS order_shipped,
       count(o.id) FILTER (WHERE o.status = 'delivered') AS order_delivered,
       count(CASE WHEN o.status = 'cancelled' THEN 1 END) AS order_cancelled
FROM orders o;
```


## Задание 3 — доля доставленных заказов

```sql

SELECT count(*) AS all_orders,
       count(CASE WHEN o.status = 'delivered' THEN 1 END) AS order_delivered,
       round(count(o.id) FILTER (WHERE o.status = 'delivered') * 100.0 / count(*), 2)
FROM orders o;
```


## Задание 4 — доставленное и потерянное

```sql

SELECT count(*) FILTER (WHERE o.status = 'delivered') AS count_delivered,
       count(CASE WHEN o.status = 'cancelled' THEN 1 END) AS count_cancelled,
       sum(o.amount) FILTER (WHERE o.status = 'delivered') AS sum_delivered,
       sum(CASE WHEN o.status = 'cancelled' THEN o.amount END) AS sum_cancelled
FROM orders o;
```


## Задание 5 — средняя сумма доставленного заказа

```sql

SELECT round(avg(o.amount) FILTER (WHERE o.status = 'delivered'), 2) AS avg_delivered,
       count(o.id) FILTER (WHERE o.status = 'delivered') AS count_delivered
FROM orders o;
```


## Задание 6 — крупные заказы внутри каждого статуса

```sql

SELECT o.status,
       count(*) AS count_all_in_status,
       count(o.id) FILTER (WHERE o.amount > 1500) AS count_orders_more_1500,
       sum(CASE WHEN o.amount > 1500 THEN o.amount END) AS sum_orders_more_1500
FROM orders o
GROUP BY o.status
ORDER BY o.status;
```


## Задание 7 — доля успешных списаний

```sql

SELECT count(*) AS attempts_all,
       count(CASE WHEN pa.status = 'success' THEN 1 END) AS attempts_success,
       count(pa.id) FILTER (WHERE pa.status = 'failed') AS failed,
       round((count(pa.id) FILTER (WHERE pa.status = 'success') * 100.0) / count(pa.id), 2)
FROM payment_attempts pa;
```


## Задание 8 — первая неудача и третья удача

```sql

SELECT count(CASE WHEN pa.status = 'failed' AND pa.attempt_no = 1 THEN 1 END),
       count(pa.id) FILTER (WHERE pa.attempt_no = 3 AND pa.status = 'success')
FROM payment_attempts pa;
```


## Задание 9 — первая и повторные попытки по статусу

```sql

SELECT pa.status,
       count(pa.id) AS all_attempts_by_status,
       count(CASE WHEN pa.attempt_no = 1 THEN 1 END) AS first_attempts_by_status,
       count(pa.id) FILTER (WHERE pa.attempt_no IN (2, 3)) AS second_third_by_status
FROM payment_attempts pa
GROUP BY pa.status
ORDER BY pa.status;
```


## Задание 10 — деньги по статусу списания

```sql

SELECT pa.status,
       sum(pa.amount) AS sum_amount_by_status,
       sum(pa.amount) FILTER (WHERE pa.attempt_no IN (2, 3)) AS sum_by_attempts_second_or_first_by_status
FROM payment_attempts pa
GROUP BY pa.status;
```
