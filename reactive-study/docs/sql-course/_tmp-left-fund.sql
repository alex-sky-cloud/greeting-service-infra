
SET search_path TO reactive_study, public;

WITH RECURSIVE tree AS (
    SELECT d.id, d.parent_id, d.name, 1 AS level
    FROM departments d
    WHERE d.parent_id IS NULL
    UNION ALL
    SELECT child.id,
           child.parent_id,
           child.name,
           parent.level + 1
    FROM departments child
    JOIN tree parent ON parent.id = child.parent_id
)
SELECT count(e.id) AS people_in_department,
       coalesce(sum(e.salary), 0) AS fund_in_department,
       e.department_id,
       t.id AS tree_id,
       t.name
FROM tree t
LEFT JOIN employees e ON e.department_id = t.id
GROUP BY e.department_id, t.name
ORDER BY department_id, name;

