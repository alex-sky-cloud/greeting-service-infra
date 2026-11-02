# Тема 09 — принятые решения практики

Ваши принятые запросы из чата курса, в той редакции, которую зачли.
Это не эталонный ключ: если было несколько рабочих вариантов, здесь стоит принятый.
Формулировки заданий — в [`09-window-functions-tasks.md`](09-window-functions-tasks.md).

```sql

SET search_path TO reactive_study;
```

## Оглавление

- [Задание 1 — зарплата и средняя по компании](#задание-1--зарплата-и-средняя-по-компании)
- [Задание 2 — сравнение со своим отделом](#задание-2--сравнение-со-своим-отделом)
- [Задание 3 — место сотрудника по зарплате](#задание-3--место-сотрудника-по-зарплате)
- [Задание 4 — рейтинг руководителей](#задание-4--рейтинг-руководителей)
- [Задание 5 — самый высокооплачиваемый сотрудник отдела](#задание-5--самый-высокооплачиваемый-сотрудник-отдела)
- [Задание 6 — два сотрудника из каждого отдела](#задание-6--два-сотрудника-из-каждого-отдела)
- [Задание 7 — три дорогих заказа каждого статуса](#задание-7--три-дорогих-заказа-каждого-статуса)
- [Задание 8 — последний заказ каждого клиента](#задание-8--последний-заказ-каждого-клиента)
- [Задание 9 — накопленная сумма заказов клиента](#задание-9--накопленная-сумма-заказов-клиента)
- [Задание 10 — накопительная сумма заказов по дням](#задание-10--накопительная-сумма-заказов-по-дням)
- [Задание 11 — средняя дневная сумма заказов за семь дат](#задание-11--средняя-дневная-сумма-заказов-за-семь-дат)
- [Задание 12 — предыдущая попытка оплаты](#задание-12--предыдущая-попытка-оплаты)
- [Задание 13 — время между попытками](#задание-13--время-между-попытками)
- [Задание 14 — процент статуса в заказах](#задание-14--процент-статуса-в-заказах)
- [Задание 15 — перерыв между заказами клиента](#задание-15--перерыв-между-заказами-клиента)

## Задание 1 — зарплата и средняя по компании

```sql

SELECT e.full_name,
       e.salary,
       round(avg(e.salary) OVER (), 2) AS avg_salary,
       round(e.salary - avg(e.salary) OVER (), 2) AS diff
FROM employees e;
```


## Задание 2 — сравнение со своим отделом

```sql

SELECT d.name,
       e.full_name,
       e.salary,
       round(avg(e.salary) OVER (PARTITION BY e.department_id), 2) AS avg_salary_by_dep,
       round(e.salary - avg(e.salary) OVER (PARTITION BY e.department_id), 2) AS diff
FROM employees e
JOIN departments d ON e.department_id = d.id;
```


## Задание 3 — место сотрудника по зарплате

```sql

SELECT row_number() OVER (ORDER BY e.salary DESC, e.id) AS row_no,
       e.id,
       e.full_name,
       e.salary
FROM employees e
ORDER BY row_no;
```


## Задание 4 — рейтинг руководителей

```sql

WITH target AS (
    SELECT boss.id,
           boss.full_name,
           count(child.manager_id) AS count_empl
    FROM employees boss
    JOIN employees child ON child.manager_id = boss.id
    GROUP BY boss.full_name, boss.id
)
SELECT t.full_name,
       t.count_empl,
       rank() OVER (ORDER BY t.count_empl DESC) AS number
FROM target t
ORDER BY t.count_empl DESC;
```


## Задание 5 — самый высокооплачиваемый сотрудник отдела

```sql

WITH target AS (
    SELECT d.name,
           e.id,
           e.full_name,
           e.salary,
           row_number() OVER (
               PARTITION BY e.department_id
               ORDER BY e.salary DESC, e.id
           ) AS row_no
    FROM employees e
    JOIN departments d ON d.id = e.department_id
)
SELECT t.name, t.id, t.full_name, t.salary
FROM target t
WHERE t.row_no = 1;
```


## Задание 6 — два сотрудника из каждого отдела

```sql

WITH target AS (
    SELECT d.name,
           e.full_name,
           e.salary,
           row_number() OVER (
               PARTITION BY e.department_id
               ORDER BY e.salary DESC, e.id
           ) AS row_no
    FROM employees e
    JOIN departments d ON d.id = e.department_id
)
SELECT t.name, t.full_name, t.salary, t.row_no
FROM target t
WHERE t.row_no <= 2;
```


## Задание 7 — три дорогих заказа каждого статуса

```sql

WITH target AS (
    SELECT o.status,
           o.id,
           o.amount,
           row_number() OVER (
               PARTITION BY o.status
               ORDER BY o.amount DESC, o.id
           ) AS row_no
    FROM orders o
)
SELECT t.status, t.id, t.amount, t.row_no
FROM target t
WHERE t.row_no <= 3;
```


## Задание 8 — последний заказ каждого клиента

```sql

WITH target AS (
    SELECT o.user_id,
           o.id,
           o.status,
           o.amount,
           o.created_at,
           row_number() OVER (
               PARTITION BY o.user_id
               ORDER BY o.created_at DESC, o.id DESC
           ) AS row_no
    FROM orders o
)
SELECT t.user_id, t.id, t.status, t.amount, t.created_at
FROM target t
WHERE t.row_no = 1;
```


## Задание 9 — накопленная сумма заказов клиента

```sql

SELECT o.user_id,
       o.id,
       o.created_at,
       o.amount,
       sum(o.amount) OVER (
           PARTITION BY o.user_id
           ORDER BY o.created_at, o.id
           ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
       ) AS sum_orders
FROM orders o
ORDER BY o.user_id, sum_orders;
```


## Задание 10 — накопительная сумма заказов по дням

```sql

WITH target AS (
    SELECT o.created_at::date AS order_date,
           count(*) AS count_orders_current_day,
           sum(o.amount) AS sum_current_day
    FROM orders o
    GROUP BY o.created_at::date
)
SELECT t.order_date,
       t.count_orders_current_day,
       t.sum_current_day,
       sum(t.sum_current_day) OVER (
           ORDER BY t.order_date
           ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
       )
FROM target t
ORDER BY t.order_date;
```


## Задание 11 — средняя дневная сумма заказов за семь дат

```sql

WITH target AS (
    SELECT o.created_at::date AS order_date,
           round(sum(o.amount), 2) AS sum_current_date
    FROM orders o
    GROUP BY o.created_at::date
)
SELECT t.order_date,
       t.sum_current_date,
       round(
           avg(t.sum_current_date) OVER (
               ORDER BY t.order_date
               ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
           ),
           2
       ) AS avg_cummulative
FROM target t
ORDER BY t.order_date;
```


## Задание 12 — предыдущая попытка оплаты

```sql

WITH target AS (
    SELECT order_id
    FROM payment_attempts
    GROUP BY order_id
    HAVING count(*) > 1
)
SELECT pa.order_id,
       pa.attempt_no,
       pa.status,
       lag(pa.status) OVER (
           PARTITION BY pa.order_id
           ORDER BY pa.attempt_no
       )
FROM payment_attempts pa
JOIN target t ON pa.order_id = t.order_id;
```


## Задание 13 — время между попытками

```sql

WITH target AS (
    SELECT pa.order_id,
           pa.attempt_no,
           pa.created_at AS current_attempt_time,
           lag(pa.created_at) OVER (
               PARTITION BY pa.order_id
               ORDER BY attempt_no
           ) AS previos_time_attempt
    FROM payment_attempts pa
)
SELECT t.order_id,
       t.attempt_no,
       t.current_attempt_time,
       t.previos_time_attempt,
       t.current_attempt_time - t.previos_time_attempt AS diff
FROM target t
WHERE t.previos_time_attempt IS NOT NULL;
```


## Задание 14 — процент статуса в заказах

```sql

WITH target AS (
    SELECT status, count(id) AS count_by_status
    FROM orders
    GROUP BY status
)
SELECT t.status,
       t.count_by_status,
       sum(t.count_by_status) OVER () AS count_all,
       round((t.count_by_status / sum(t.count_by_status) OVER ()) * 100.00, 2) AS persent
FROM target t;
```


## Задание 15 — перерыв между заказами клиента

```sql

SELECT o.user_id,
       o.id,
       o.created_at,
       lag(o.created_at) OVER (
           PARTITION BY o.user_id
           ORDER BY o.created_at, o.id
       ) AS previous_order,
       o.created_at - lag(o.created_at) OVER (
           PARTITION BY o.user_id
           ORDER BY o.created_at, o.id
       ) AS diff
FROM orders o
ORDER BY user_id, o.created_at, o.id;
```
