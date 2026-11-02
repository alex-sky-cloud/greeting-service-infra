# Тема 04 — принятые решения практики

Ваши принятые запросы из чата курса, в той редакции, которую зачли.
Это не эталонный ключ: если было несколько рабочих вариантов, здесь стоит принятый.
Формулировки заданий — в [`04-joins-inner-left-tasks.md`](04-joins-inner-left-tasks.md).

```sql

SET search_path TO reactive_study;
```

## Оглавление

- [Задание 1 — обращение по заказу](#задание-1--обращение-по-заказу)
- [Задание 2 — карточка постоянного клиента](#задание-2--карточка-постоянного-клиента)
- [Задание 3 — спящие клиенты](#задание-3--спящие-клиенты)
- [Задание 4 — SMS по доставленным](#задание-4--sms-по-доставленным)
- [Задание 5 — заказы «в работе» с контактами](#задание-5--заказы-в-работе-с-контактами)
- [Задание 6 — учебные клиенты без покупок](#задание-6--учебные-клиенты-без-покупок)
- [Задание 7 — выручка по имени клиента](#задание-7--выручка-по-имени-клиента)
- [Задание 8 — крупные заказы: кто покупатель](#задание-8--крупные-заказы:-кто-покупатель)
- [Задание 9 — топ клиентов по числу заказов](#задание-9--топ-клиентов-по-числу-заказов)
- [Задание 10 — «тяжёлые» покупатели](#задание-10--тяжёлые-покупатели)
- [Задание 11 — ранние пользователи example.com](#задание-11--ранние-пользователи-example.com)
- [Задание 12 — сравнение двух клиентов](#задание-12--сравнение-двух-клиентов)

## Задание 1 — обращение по заказу

```sql

SELECT o.id, u.full_name, u.email, o.status
FROM users u
INNER JOIN orders o ON u.id = o.user_id
WHERE o.id = 100001;
```


## Задание 2 — карточка постоянного клиента

```sql

SELECT u.full_name, o.id, o.amount, o.status
FROM users u
INNER JOIN orders o ON u.id = o.user_id
WHERE u.id = 1
ORDER BY o.id DESC
LIMIT 10;
```


## Задание 3 — спящие клиенты

```sql

SELECT count(*) AS cnt_clients_without_orders
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE o.id IS NULL;
```


## Задание 4 — SMS по доставленным

```sql

SELECT o.id, u.email, o.amount
FROM users u
INNER JOIN orders o ON u.id = o.user_id
WHERE status = 'delivered'
ORDER BY o.id DESC
LIMIT 5;
```


## Задание 5 — заказы «в работе» с контактами

```sql

SELECT o.id, u.full_name, o.status, o.amount
FROM users u
INNER JOIN orders o ON u.id = o.user_id
WHERE status NOT IN ('delivered', 'cancelled')
ORDER BY o.id
LIMIT 10;
```


## Задание 6 — учебные клиенты без покупок

```sql

SELECT u.email, u.full_name, o.id
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE u.email LIKE 'join-demo-no-orders-%@example.com';
```


## Задание 7 — выручка по имени клиента

```sql

SELECT u.full_name, sum(o.amount) AS amount_of_client
FROM users u
INNER JOIN orders o ON u.id = o.user_id
WHERE u.id = 2
GROUP BY u.full_name, o.user_id;
```


## Задание 8 — крупные заказы: кто покупатель

```sql

SELECT o.id, u.email, o.amount
FROM users u
INNER JOIN orders o ON u.id = o.user_id
WHERE o.amount >= 1500
ORDER BY o.amount DESC
LIMIT 10;
```


## Задание 9 — топ клиентов по числу заказов

```sql

SELECT u.full_name, count(o.id) AS cnt_orders
FROM users u
INNER JOIN orders o ON u.id = o.user_id
GROUP BY u.full_name, o.user_id
ORDER BY cnt_orders DESC, u.full_name
LIMIT 5;
```


## Задание 10 — «тяжёлые» покупатели

```sql

SELECT u.id, u.full_name, count(o.id) AS cnt_orders
FROM users u
INNER JOIN orders o ON u.id = o.user_id
GROUP BY u.id, u.full_name, o.user_id
HAVING count(o.id) > 25
ORDER BY cnt_orders DESC;
```


## Задание 11 — ранние пользователи example.com

```sql

SELECT u.id, u.full_name, count(o.id) AS cnt_orders, sum(o.amount) AS amount_orders
FROM users u
INNER JOIN orders o ON u.id = o.user_id
WHERE u.id IN (1, 2, 3)
GROUP BY u.id, u.full_name, o.user_id
ORDER BY u.id;
```


## Задание 12 — сравнение двух клиентов

```sql

SELECT u.email, u.full_name, o.id, o.amount
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE u.email IN (
    'join-demo-single-order@example.com',
    'join-demo-no-orders-1@example.com'
)
ORDER BY u.email, o.id;
```
