# Тема 08 — принятые решения практики

Ваши принятые запросы из чата курса, в той редакции, которую зачли.
Это не эталонный ключ: если было несколько рабочих вариантов, здесь стоит принятый.
Формулировки заданий — в [`08-cte-tasks.md`](08-cte-tasks.md).

```sql

SET search_path TO reactive_study;
```

## Оглавление

- [Задание 1 — люди и деньги по каждому отделу](#задание-1--люди-и-деньги-по-каждому-отделу)
- [Задание 2 — отделы с фондом выше среднего](#задание-2--отделы-с-фондом-выше-среднего)
- [Задание 3 — сотрудники с зарплатой выше средней](#задание-3--сотрудники-с-зарплатой-выше-средней)
- [Задание 4 — что входит в логистический департамент](#задание-4--что-входит-в-логистический-департамент)
- [Задание 5 — начальники курьера](#задание-5--начальники-курьера)
- [Задание 6 — путь до каждого отдела](#задание-6--путь-до-каждого-отдела)
- [Задание 7 — фонд коммерческого департамента целиком](#задание-7--фонд-коммерческого-департамента-целиком)
- [Задание 8 — что входит в раздел «Дом»](#задание-8--что-входит-в-раздел-дом)
- [Задание 9 — выручка раздела «Жизнь и отдых» целиком](#задание-9--выручка-раздела-жизнь-и-отдых-целиком)
- [Задание 10 — насколько глубокая структура компании](#задание-10--насколько-глубокая-структура-компании)
- [Задание 11 — сотрудник и его начальник](#задание-11--сотрудник-и-его-начальник)
- [Задание 12 — отдел и отдел над ним](#задание-12--отдел-и-отдел-над-ним)
- [Задание 13 — сколько прямых подчинённых](#задание-13--сколько-прямых-подчинённых)
- [Задание 14 — сотрудники без подчинённых](#задание-14--сотрудники-без-подчинённых)
- [Задание 15 — зарплата рядом с зарплатой начальника](#задание-15--зарплата-рядом-с-зарплатой-начальника)
- [Задание 16 — отделы без подчинённых отделов](#задание-16--отделы-без-подчинённых-отделов)

## Задание 1 — люди и деньги по каждому отделу

```sql

WITH blank AS (
    SELECT d.name, e.id, e.salary
    FROM employees e
    JOIN departments d ON e.department_id = d.id
)
SELECT b.name,
       count(b.id) AS count_empl,
       sum(b.salary) AS found_departments
FROM blank b
GROUP BY b.name;

-- или

SELECT d.name,
       count(e.id) AS count_empl,
       sum(e.salary) AS found_departments
FROM employees e
JOIN departments d ON e.department_id = d.id
GROUP BY d.name;
```

## Задание 2 — отделы с фондом выше среднего

```sql

WITH found AS (
    SELECT d.name,
           sum(e.salary) AS found_salary
    FROM departments d
    JOIN employees e ON d.id = e.department_id
    GROUP BY d.name
)
SELECT *
FROM found f
WHERE f.found_salary > (SELECT avg(f2.found_salary) FROM found f2)
ORDER BY f.name;

-- или

WITH found AS (
    SELECT d.name,
           sum(e.salary) AS sum_by_dep
    FROM departments d
    INNER JOIN employees e ON e.department_id = d.id
    GROUP BY d.name
),
avg_found AS (
    SELECT avg(f.sum_by_dep) AS avg_by_dep
    FROM found f
)
SELECT f.name,
       f.sum_by_dep
FROM found f
INNER JOIN avg_found af ON true
WHERE f.sum_by_dep > af.avg_by_dep
ORDER BY f.name;
```

## Задание 3 — сотрудники с зарплатой выше средней

```sql

WITH avg_salary AS (
    SELECT avg(salary) AS avg_salary
    FROM employees e
)
SELECT e.full_name,
       e.salary
FROM employees e
JOIN avg_salary a ON true
WHERE e.salary > a.avg_salary;

-- или

SELECT e.full_name,
       e.salary
FROM employees e
WHERE e.salary > (SELECT avg(e2.salary) FROM employees e2);
```

## Задание 4 — что входит в логистический департамент

```sql

WITH RECURSIVE subtree AS (
    SELECT d.id,
           d.name,
           1 AS level
    FROM departments d
    WHERE d.id = 3

    UNION ALL

    SELECT child.id,
           child.name,
           boss.level + 1
    FROM departments child
    JOIN subtree boss ON boss.id = child.parent_id
)
SELECT s.id,
       s.name,
       s.level
FROM subtree s
ORDER BY s.id;
```

## Задание 5 — начальники курьера

```sql

WITH RECURSIVE subtree AS (
    SELECT 1 AS step,
           e.full_name,
           e.manager_id,
           e.position
    FROM employees e
    WHERE e.full_name = 'Светлана Ким'

    UNION ALL

    SELECT child.step + 1,
           boss.full_name,
           boss.manager_id,
           boss.position
    FROM employees boss
    JOIN subtree child ON boss.id = child.manager_id
)
SELECT s.step,
       s.full_name,
       s.position
FROM subtree s
ORDER BY s.step;
```

## Задание 6 — путь до каждого отдела

```sql

WITH RECURSIVE subtree AS (
    SELECT d.id,
           1 AS level,
           d.parent_id,
           d.name,
           d.name AS path_dep
    FROM departments d
    WHERE d.parent_id IS NULL

    UNION ALL

    SELECT child.id,
           boss.level + 1,
           child.parent_id,
           child.name,
           boss.path_dep || '\' || child.name
    FROM departments child
    JOIN subtree boss ON boss.id = child.parent_id
)
SELECT s.id,
       s.level,
       s.path_dep
FROM subtree s;
```

## Задание 7 — фонд коммерческого департамента целиком

```sql

WITH RECURSIVE subtree AS (
    SELECT d.id,
           d.parent_id
    FROM departments d
    WHERE d.name = 'Коммерческий департамент'

    UNION ALL

    SELECT child.id,
           child.parent_id
    FROM departments child
    JOIN subtree boss ON boss.id = child.parent_id
)
SELECT count(e.id),
       sum(e.salary)
FROM subtree s
JOIN employees e ON s.id = e.department_id;
```

## Задание 8 — что входит в раздел «Дом»

```sql

WITH RECURSIVE subtree AS (
    SELECT cs.id,
           cs.name,
           1 AS level
    FROM catalog_sections cs
    WHERE cs.name = 'Дом'

    UNION ALL

    SELECT child.id,
           child.name,
           boss.level + 1
    FROM catalog_sections child
    JOIN subtree boss ON child.parent_id = boss.id
)
SELECT *
FROM subtree;
```

## Задание 9 — выручка раздела «Жизнь и отдых» целиком

```sql

WITH RECURSIVE subtree AS (
    SELECT cs.id,
           cs.parent_id
    FROM catalog_sections cs
    WHERE cs.name = 'Жизнь и отдых'

    UNION ALL

    SELECT child.id,
           child.parent_id
    FROM catalog_sections child
    JOIN subtree boss ON boss.id = child.parent_id
)
SELECT count(o.id) AS count_orders_in_life_and_rescue,
       round(sum(o.amount), 2) AS sum_orders
FROM subtree s
JOIN products p ON p.section_id = s.id
JOIN orders o ON o.product_id = p.id;
```

## Задание 10 — насколько глубокая структура компании

```sql

WITH RECURSIVE subtree AS (
    SELECT 1 AS level,
           d.id,
           d.parent_id
    FROM departments d
    WHERE d.parent_id IS NULL

    UNION ALL

    SELECT boss.level + 1,
           child.id,
           child.parent_id
    FROM departments child
    JOIN subtree boss ON boss.id = child.parent_id
)
SELECT s.level,
       count(s.id) AS count_departments
FROM subtree s
GROUP BY s.level
ORDER BY s.level;
```

## Задание 11 — сотрудник и его начальник

```sql

SELECT child.id,
       child.full_name,
       child.position AS position_employee,
       coalesce(boss.full_name, '') AS name_boss,
       coalesce(boss.position, '') AS boss_position
FROM employees child
LEFT JOIN employees boss ON child.manager_id = boss.id
ORDER BY child.id;
```

## Задание 12 — отдел и отдел над ним

```sql

SELECT child.id,
       child.name AS doter_department,
       coalesce(boss.name, '') AS boss_department
FROM departments child
LEFT JOIN departments boss ON boss.id = child.parent_id
ORDER BY child.id;
```

## Задание 13 — сколько прямых подчинённых

```sql

SELECT boss.full_name,
       count(child.id) AS count_employees
FROM employees boss
JOIN employees child ON boss.id = child.manager_id
GROUP BY boss.full_name;
```

## Задание 14 — сотрудники без подчинённых

```sql

SELECT e.id,
       e.full_name
FROM employees e
WHERE NOT EXISTS (
    SELECT 1 FROM employees e2 WHERE e2.manager_id = e.id
);
```

## Задание 15 — зарплата рядом с зарплатой начальника

```sql

SELECT child.full_name,
       child.position AS position_employee,
       child.salary AS salary_employee,
       coalesce(boss.full_name, '') AS boss_full_name,
       coalesce(boss.position, '') AS position_boss,
       coalesce(boss.salary, 0) AS salary_boss,
       coalesce(child.salary - boss.salary, 0.0) AS diff_salary
FROM employees child
LEFT JOIN employees boss ON boss.id = child.manager_id;
```

## Задание 16 — отделы без подчинённых отделов

```sql

SELECT d.id,
       d.name
FROM departments d
WHERE NOT EXISTS (
    SELECT 1 FROM departments d2 WHERE d2.parent_id = d.id
);
```
