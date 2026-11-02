# Тема 02 — принятые решения практики

Ваши принятые запросы из чата курса, в той редакции, которую зачли.
Это не эталонный ключ: если было несколько рабочих вариантов, здесь стоит принятый.
Формулировки заданий — в [`02-filtering-tasks.md`](02-filtering-tasks.md).

```sql

SET search_path TO reactive_study;
```

## Оглавление

- [Задание 1 — жалоба от трёх клиентов](#задание-1--жалоба-от-трёх-клиентов)
- [Задание 2 — рассылка на «старый» домен](#задание-2--рассылка-на-старый-домен)
- [Задание 3 — заказы «ещё в работе»](#задание-3--заказы-ещё-в-работе)
- [Задание 4 — первые тестовые пользователи](#задание-4--первые-тестовые-пользователи)
- [Задание 5 — крупные заказы на проверке](#задание-5--крупные-заказы-на-проверке)
- [Задание 6 — заказы с привязкой к каталогу](#задание-6--заказы-с-привязкой-к-каталогу)

## Задание 1 — жалоба от трёх клиентов

```sql

SELECT id, full_name FROM users WHERE id IN (1, 2, 3) ORDER BY id;
```


## Задание 2 — рассылка на «старый» домен

```sql

SELECT id, email FROM users WHERE email LIKE '%example.com';
```


## Задание 3 — заказы «ещё в работе»

```sql

SELECT id, status FROM orders WHERE status NOT IN ('delivered', 'cancelled') LIMIT 10;
```


## Задание 4 — первые тестовые пользователи

```sql

SELECT id, full_name FROM users WHERE id BETWEEN 5 AND 9 ORDER BY id DESC;
```


## Задание 5 — крупные заказы на проверке

```sql

SELECT id, status, amount
FROM orders
WHERE status IN ('pending', 'processing') AND amount > 1000
ORDER BY id
LIMIT 10;

-- или

SELECT id, status, amount
FROM orders
WHERE (status = 'pending' OR status = 'processing') AND amount > 1000
ORDER BY id
LIMIT 10;
```


## Задание 6 — заказы с привязкой к каталогу

```sql

SELECT id, product_id, status FROM orders WHERE product_id IS NOT NULL LIMIT 5;
```
