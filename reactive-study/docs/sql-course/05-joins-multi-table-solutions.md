# Тема 05 — принятые решения практики

Ваши принятые запросы из чата курса, в той редакции, которую зачли.
Это не эталонный ключ: если было несколько рабочих вариантов, здесь стоит принятый.
Формулировки заданий — в [`05-joins-multi-table-tasks.md`](05-joins-multi-table-tasks.md).

```sql

SET search_path TO reactive_study;
```

## Оглавление

- [Задание 1 — карточка заказа 2](#задание-1--карточка-заказа-2)
- [Задание 2 — пять заказов отдела «Электроника»](#задание-2--пять-заказов-отдела-электроника)
- [Задание 3 — пять отделов с наибольшим числом заказов](#задание-3--пять-отделов-с-наибольшим-числом-заказов)
- [Задание 4 — число заказов по каждому отделу](#задание-4--число-заказов-по-каждому-отделу)
- [Задание 5 — заказы 100001 и 100002](#задание-5--заказы-100001-и-100002)
- [Задание 6 — заказ 100003: название и артикул](#задание-6--заказ-100003:-название-и-артикул)
- [Задание 7 — история заказов Ann Smith](#задание-7--история-заказов-ann-smith)
- [Задание 8 — пять самых дорогих заказов](#задание-8--пять-самых-дорогих-заказов)
- [Задание 9 — заказы без карточки в каталоге](#задание-9--заказы-без-карточки-в-каталоге)
- [Задание 10 — три отдела с наибольшей выручкой](#задание-10--три-отдела-с-наибольшей-выручкой)
- [Задание 11 — выручка по каждому отделу](#задание-11--выручка-по-каждому-отделу)
- [Задание 12 — два показателя по каждому отделу](#задание-12--два-показателя-по-каждому-отделу)
- [Задание 13 — три заказа, отдел только «Электроника»](#задание-13--три-заказа,-отдел-только-электроника)
- [Задание 14 — три заказа, каталог только «Электроника»](#задание-14--три-заказа,-каталог-только-электроника)

## Задание 1 — карточка заказа 2

```sql

SELECT o.id, u.full_name, p.sku, pc.name, o.amount
FROM orders o
INNER JOIN users u ON o.user_id = u.id
INNER JOIN products p ON o.product_id = p.id
INNER JOIN product_categories pc ON p.category_id = pc.id
WHERE o.id = 2;
```


## Задание 2 — пять заказов отдела «Электроника»

```sql

SELECT o.id, p.sku, o.amount
FROM orders o
INNER JOIN products p ON o.product_id = p.id
INNER JOIN product_categories pc ON p.category_id = pc.id
WHERE pc.name = 'Электроника'
ORDER BY o.id
LIMIT 5;
```


## Задание 3 — пять отделов с наибольшим числом заказов

```sql

SELECT pc.name, count(o.id) AS count_orders
FROM orders o
INNER JOIN products p ON o.product_id = p.id
INNER JOIN product_categories pc ON pc.id = p.category_id
GROUP BY pc.name
ORDER BY count_orders DESC
LIMIT 5;
```


## Задание 4 — число заказов по каждому отделу

```sql

SELECT pc.name, count(*) AS count_orders
FROM orders o
LEFT JOIN products p ON o.product_id = p.id
LEFT JOIN product_categories pc ON p.category_id = pc.id
GROUP BY pc.name
ORDER BY count_orders DESC;
```


## Задание 5 — заказы 100001 и 100002

```sql

SELECT o.id, p.sku, pc.name
FROM orders o
LEFT JOIN (
    products p
    INNER JOIN product_categories pc ON p.category_id = pc.id
) ON o.product_id = p.id
WHERE o.id IN (100001, 100002);
```


## Задание 6 — заказ 100003: название и артикул

```sql

SELECT o.id, o.product_name, p.sku
FROM orders o
LEFT JOIN products p ON o.product_id = p.id
WHERE o.id = 100003;
```


## Задание 7 — история заказов Ann Smith

```sql

SELECT o.id, p.sku, pc.name, o.amount
FROM orders o
INNER JOIN users u ON u.id = o.user_id
INNER JOIN products p ON p.id = o.product_id
INNER JOIN product_categories pc ON p.category_id = pc.id
WHERE u.full_name = 'Ann Smith'
ORDER BY o.id DESC
LIMIT 10;
```


## Задание 8 — пять самых дорогих заказов

```sql

SELECT o.id, u.full_name, p.sku, pc.name, o.amount
FROM orders o
INNER JOIN users u ON o.user_id = u.id
INNER JOIN products p ON p.id = o.product_id
INNER JOIN product_categories pc ON pc.id = p.category_id
ORDER BY o.amount DESC, o.id
LIMIT 5;
```


## Задание 9 — заказы без карточки в каталоге

```sql

SELECT count(*) FROM orders o WHERE o.product_id IS NULL;
```


## Задание 10 — три отдела с наибольшей выручкой

```sql

SELECT pc.name, sum(o.amount) AS sum
FROM orders o
INNER JOIN products p ON o.product_id = p.id
INNER JOIN product_categories pc ON p.category_id = pc.id
GROUP BY pc.name
ORDER BY sum DESC
LIMIT 3;
```


## Задание 11 — выручка по каждому отделу

```sql

SELECT pc.name, sum(o.amount) AS sum
FROM orders o
LEFT JOIN products p ON o.product_id = p.id
LEFT JOIN product_categories pc ON p.category_id = pc.id
GROUP BY pc.name
ORDER BY sum DESC;
```


## Задание 12 — два показателя по каждому отделу

```sql

SELECT pc.name, count(*) AS count_orders_by, count(pc.name)
FROM orders o
LEFT JOIN products p ON o.product_id = p.id
LEFT JOIN product_categories pc ON pc.id = p.category_id
GROUP BY pc.name
ORDER BY count_orders_by DESC;
```


## Задание 13 — три заказа, отдел только «Электроника»

```sql

SELECT o.id, p.sku, pc.name
FROM orders o
LEFT JOIN products p ON o.product_id = p.id
LEFT JOIN product_categories pc ON pc.id = p.category_id AND pc.name = 'Электроника'
WHERE o.id IN (100001, 100002, 100003);
```


## Задание 14 — три заказа, каталог только «Электроника»

```sql

SELECT o.id, p.sku, pc.name
FROM orders o
LEFT JOIN (
    products p
    INNER JOIN product_categories pc ON p.category_id = pc.id AND pc.name = 'Электроника'
) ON o.product_id = p.id
WHERE o.id IN (100001, 100002, 100003);
```
