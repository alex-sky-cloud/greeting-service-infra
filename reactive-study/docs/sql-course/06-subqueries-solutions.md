# Тема 06 — принятые решения практики

Ваши принятые запросы из чата курса, в той редакции, которую зачли.
Это не эталонный ключ: если было несколько рабочих вариантов, здесь стоит принятый.
Формулировки заданий — в [`06-subqueries-tasks.md`](06-subqueries-tasks.md).

```sql

SET search_path TO reactive_study;
```

## Оглавление

- [Задание 1 — заказы дороже среднего](#задание-1--заказы-дороже-среднего)
- [Задание 2 — самый дорогой заказ в истории](#задание-2--самый-дорогой-заказ-в-истории)
- [Задание 3 — счётчик заказов в списке клиентов](#задание-3--счётчик-заказов-в-списке-клиентов)
- [Задание 4 — клиенты с заказом дороже 2000](#задание-4--клиенты-с-заказом-дороже-2000)
- [Задание 5 — клиенты без заказов](#задание-5--клиенты-без-заказов)
- [Задание 6 — товары, которые ни разу не покупали](#задание-6--товары,-которые-ни-разу-не-покупали)
- [Задание 7 — заказы с неопределимым товаром](#задание-7--заказы-с-неопределимым-товаром)
- [Задание 8 — клиенты с крупным необработанным заказом](#задание-8--клиенты-с-крупным-необработанным-заказом)
- [Задание 9 — товары дороже всего отдела «Офис»](#задание-9--товары-дороже-всего-отдела-офис)
- [Задание 10 — пять самых активных клиентов](#задание-10--пять-самых-активных-клиентов)
- [Задание 11 — заказы выше личного среднего клиента](#задание-11--заказы-выше-личного-среднего-клиента)
- [Задание 12 — отделы с непроданными товарами](#задание-12--отделы-с-непроданными-товарами)
- [Задание 13 — клиенты без доставленных заказов](#задание-13--клиенты-без-доставленных-заказов)
- [Задание 14 — клиенты с необработанным заказом из «Электроники»](#задание-14--клиенты-с-необработанным-заказом-из-электроники)
- [Задание 15 — товары дешевле всего отдела «Офис»](#задание-15--товары-дешевле-всего-отдела-офис)
- [Задание 16 — клиенты, которые ни разу не брали «Спорт»](#задание-16--клиенты,-которые-ни-разу-не-брали-спорт)
- [Задание 17 — заказы дороже среднего по отделу «Одежда»](#задание-17--заказы-дороже-среднего-по-отделу-одежда)
- [Задание 18 — клиенты с отменённым заказом из «Продуктов»](#задание-18--клиенты-с-отменённым-заказом-из-продуктов)

## Задание 1 — заказы дороже среднего

```sql

SELECT o.id, o.amount
FROM orders o
WHERE o.amount > (SELECT avg(o2.amount) FROM orders o2)
ORDER BY o.amount DESC
LIMIT 10;
```


## Задание 2 — самый дорогой заказ в истории

```sql

SELECT o.id, o.amount FROM orders o WHERE o.amount = (SELECT max(o2.amount) FROM orders o2);
```


## Задание 3 — счётчик заказов в списке клиентов

```sql

SELECT u.id, u.full_name,
       (SELECT count(*) FROM orders o WHERE o.user_id = u.id) AS amount_orders
FROM users u
ORDER BY amount_orders
LIMIT 10;

-- или

SELECT u.id, u.full_name, count(o.id) AS count
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
GROUP BY u.id, u.full_name
ORDER BY count
LIMIT 10;
```


## Задание 4 — клиенты с заказом дороже 2000

```sql

SELECT u.id, u.full_name
FROM users u
WHERE EXISTS (
    SELECT 1 FROM orders o WHERE o.amount > 2000 AND o.user_id = u.id
)
ORDER BY u.id
LIMIT 10;
```


## Задание 5 — клиенты без заказов

```sql

SELECT u.id, u.full_name, u.email
FROM users u
WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.user_id = u.id)
ORDER BY u.id;

-- или

SELECT u.id, u.full_name, u.email, count(o.amount) AS cnt
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
GROUP BY u.id, u.full_name, u.email
HAVING count(o.amount) = 0
ORDER BY u.id;
```


## Задание 6 — товары, которые ни разу не покупали

```sql

SELECT p.sku, p.name, p.price
FROM products p
WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.product_id = p.id)
ORDER BY price;
```


## Задание 7 — заказы с неопределимым товаром

```sql

SELECT o.id, o.product_name, o.amount
FROM orders o
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.id = o.product_id)
ORDER BY o.id;
```


## Задание 8 — клиенты с крупным необработанным заказом

```sql

SELECT u.id, u.full_name
FROM users u
WHERE EXISTS (
    SELECT 1 FROM orders o
    WHERE o.status = 'pending' AND o.user_id = u.id AND o.amount > 1500
)
ORDER BY u.id
LIMIT 10;

-- или

SELECT u.id, u.full_name
FROM users u
INNER JOIN orders o ON u.id = o.user_id
WHERE o.amount > 1500 AND o.status = 'pending'
GROUP BY u.id, u.full_name
ORDER BY u.id
LIMIT 10;
```


## Задание 9 — товары дороже всего отдела «Офис»

```sql

SELECT p.sku, p.name, p.price
FROM products p
WHERE p.price > (
    SELECT max(p2.price)
    FROM products p2
    INNER JOIN product_categories pc ON p2.category_id = pc.id
    WHERE pc.name = 'Офис'
)
ORDER BY p.price DESC
LIMIT 10;
```


## Задание 10 — пять самых активных клиентов

```sql

SELECT u.id, u.full_name, count(o.id) AS count_orders
FROM users u
INNER JOIN orders o ON o.user_id = u.id
GROUP BY u.id, u.full_name
ORDER BY count_orders DESC, u.id
LIMIT 5;

-- или

SELECT u.id,
       u.full_name,
       (SELECT count(o.id) FROM orders o WHERE o.user_id = u.id) AS count_orders
FROM users u
ORDER BY count_orders DESC, u.id
LIMIT 5;
```


## Задание 11 — заказы выше личного среднего клиента

```sql

SELECT o.id, o.user_id, o.amount
FROM orders o
WHERE o.amount > (
    SELECT avg(o2.amount) FROM orders o2 WHERE o2.user_id = o.user_id
)
ORDER BY o.id
LIMIT 10;
```


## Задание 12 — отделы с непроданными товарами

```sql

SELECT pc.name
FROM product_categories pc
INNER JOIN products p ON pc.id = p.category_id
WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.product_id = p.id)
GROUP BY pc.name
ORDER BY pc.name;
```


## Задание 13 — клиенты без доставленных заказов

```sql

SELECT u.id, u.full_name, u.email
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM orders o WHERE o.status = 'delivered' AND o.user_id = u.id
)
ORDER BY u.id
LIMIT 10;
```


## Задание 14 — клиенты с необработанным заказом из «Электроники»

```sql

SELECT u.id, u.full_name
FROM users u
WHERE EXISTS (
    SELECT 1 FROM orders o
    WHERE o.user_id = u.id
      AND o.status = 'pending'
      AND EXISTS (
          SELECT 1 FROM products p
          WHERE p.id = o.product_id
            AND EXISTS (
                SELECT 1 FROM product_categories pc
                WHERE p.category_id = pc.id AND pc.name = 'Электроника'
            )
      )
)
ORDER BY u.id
LIMIT 10;
```


## Задание 15 — товары дешевле всего отдела «Офис»

```sql

SELECT p.sku, p.name, p.price
FROM products p
WHERE p.price < (
    SELECT min(p2.price)
    FROM products p2
    WHERE EXISTS (
        SELECT 1 FROM product_categories pc
        WHERE pc.id = p2.category_id AND pc.name = 'Офис'
    )
);

-- или

SELECT p.sku, p.name, p.price
FROM products p
WHERE p.price < ALL (
    SELECT p2.price
    FROM products p2
    WHERE EXISTS (
        SELECT 1 FROM product_categories pc
        WHERE pc.id = p2.category_id AND pc.name = 'Офис'
    )
);
```


## Задание 16 — клиенты, которые ни разу не брали «Спорт»

```sql

SELECT u.id, u.full_name
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM orders o
    WHERE u.id = o.user_id
      AND EXISTS (
          SELECT 1 FROM products p
          WHERE o.product_id = p.id
            AND EXISTS (
                SELECT 1 FROM product_categories pc
                WHERE pc.id = p.category_id AND pc.name = 'Спорт'
            )
      )
)
ORDER BY u.id DESC
LIMIT 10;
```


## Задание 17 — заказы дороже среднего по отделу «Одежда»

```sql

SELECT o.id, o.amount
FROM orders o
WHERE o.amount > (
    SELECT avg(o2.amount)
    FROM orders o2
    WHERE EXISTS (
        SELECT 1 FROM products p
        WHERE p.id = o2.product_id
          AND EXISTS (
              SELECT 1 FROM product_categories pc
              WHERE pc.id = p.category_id AND pc.name = 'Одежда'
          )
    )
)
ORDER BY o.amount DESC
LIMIT 10;
```


## Задание 18 — клиенты с отменённым заказом из «Продуктов»

```sql

SELECT u.id, u.full_name
FROM users u
WHERE EXISTS (
    SELECT 1 FROM orders o
    WHERE o.user_id = u.id
      AND o.status = 'cancelled'
      AND EXISTS (
          SELECT 1 FROM products p
          WHERE p.id = o.product_id
            AND EXISTS (
                SELECT 1 FROM product_categories pc
                WHERE pc.id = p.category_id AND pc.name = 'Продукты'
            )
      )
)
ORDER BY u.id
LIMIT 10;
```
