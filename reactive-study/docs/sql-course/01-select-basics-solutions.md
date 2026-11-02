# Тема 01 — принятые решения практики

Ваши принятые запросы из чата курса, в той редакции, которую зачли.
Это не эталонный ключ: если было несколько рабочих вариантов, здесь стоит принятый.
Формулировки заданий — в [`01-select-basics-tasks.md`](01-select-basics-tasks.md).

```sql

SET search_path TO reactive_study;
```

## Оглавление

- [Задание 1 — конкретные столбцы](#задание-1--конкретные-столбцы)
- [Задание 2 — фильтр по id](#задание-2--фильтр-по-id)
- [Задание 3 — фильтр по email](#задание-3--фильтр-по-email)
- [Задание 4 — диапазон и сортировка](#задание-4--диапазон-и-сортировка)
- [Задание 5 — топ по дате регистрации](#задание-5--топ-по-дате-регистрации)
- [Задание 6 — полный запрос](#задание-6--полный-запрос)

## Задание 1 — конкретные столбцы

```sql

SELECT id, email FROM users LIMIT 5;
```


## Задание 2 — фильтр по id

```sql

SELECT id, full_name FROM users WHERE id = 1;
```


## Задание 3 — фильтр по email

```sql

SELECT id, full_name FROM users WHERE email = 'bob@example.com';
```


## Задание 4 — диапазон и сортировка

```sql

SELECT id, full_name FROM users WHERE id BETWEEN 5 AND 9 ORDER BY id DESC;
```


## Задание 5 — топ по дате регистрации

```sql

SELECT id, email, created_at FROM users ORDER BY created_at DESC LIMIT 3;
```


## Задание 6 — полный запрос

```sql

SELECT id, email FROM users WHERE id > 100 ORDER BY id LIMIT 10;
```
