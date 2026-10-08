# Дерево компании: `SEARCH DEPTH FIRST`

Задача та же, что в [`08-cte-tree-report.md`](08-cte-tree-report.md): обойти все отделы от «Компании» вниз и вывести строки **в порядке обхода дерева**, а не просто `ORDER BY level, id`.

Рекурсивная часть совпадает с ручным вариантом без массива `path`. Порядок строк задаёт встроенная конструкция PostgreSQL **`SEARCH`**, добавленная в SQL:2016 и описанная в документации PostgreSQL 16.

Три способа вывести **то же дерево** (13 отделов, обход в глубину):

| Способ | Что копим в рекурсии | Как сортировать | Где разбор |
|---|---|---|---|
| `SEARCH DEPTH FIRST` | `level`; столбец `ord` добавляет PostgreSQL | `ORDER BY ord` | этот файл |
| массив `path` | `array[id]`, `parent.path \|\| child.id` | `ORDER BY path` | [`08-cte-tree-report.md`](08-cte-tree-report.md) |
| текстовый `path` | имена через `\|\| ' / ' \|\|` | `ORDER BY path` | [`08-cte.md`](08-cte.md#путь-от-корня-до-отдела) |

Волны «накопленный результат / рабочая таблица» для `JOIN subtree` — те же, что в [`08-cte-tree-report.md`](08-cte-tree-report.md), часть 4 (там же видно, как считается массив `path`). Ниже те же шаги **полными таблицами**, без колонок `path` и `ord`.

Источник: https://www.postgresql.org/docs/16/queries-with.html (раздел про depth-first / breadth-first и `SEARCH`).

**Цитата:**

> There is built-in syntax to compute a depth- or breadth-first sort column. For example:
>
> `) SEARCH DEPTH FIRST BY id SET ordercol`
>
> This syntax is internally expanded to something similar to the above hand-written forms. The `SEARCH` clause specifies whether depth- or breadth first search is wanted, the list of columns to track for sorting, and a column name that will contain the result data that can be used for sorting. That column will implicitly be added to the output rows of the CTE.

**Перевод:**

> Есть встроенный синтаксис для вычисления столбца сортировки в порядке обхода в глубину или в ширину. Например:
>
> `) SEARCH DEPTH FIRST BY id SET ordercol`
>
> Этот синтаксис внутренне разворачивается во что-то похожее на описанные выше формы, написанные вручную. Предложение `SEARCH` задаёт, нужен обход в глубину или в ширину, список столбцов для учёта при сортировке и имя столбца, в котором будут данные для сортировки. Этот столбец неявно добавляется к строкам результата CTE.

---

## Полный запрос с комментариями

```sql

WITH RECURSIVE subtree AS (          -- рекурсивный CTE (старт + UNION ALL + JOIN subtree)

    -- СТАРТОВАЯ ЧАСТЬ: один раз
    SELECT id,
           parent_id,
           name,
           1 AS level
    FROM departments
    WHERE name = 'Компания'

    UNION ALL

    -- РЕКУРСИВНАЯ ЧАСТЬ
    SELECT child.id,
           child.parent_id,
           child.name,
           parent.level + 1
    FROM departments child
    JOIN subtree parent
      ON parent.id = child.parent_id

) SEARCH DEPTH FIRST BY id SET ord   -- не часть SELECT: модификатор CTE; столбец ord добавит PostgreSQL

SELECT id,
       parent_id,
       name,
       level
FROM subtree
ORDER BY ord;                         -- порядок «как в дереве», по служебному столбцу ord
```

---

## Отчёт «как на скриншоте»: `SEARCH` + отступы в колонке `name`

`SEARCH` и `ord` задают **только порядок строк**. Визуальную «лесенку» в колонке с названием делают **во внешнем** `SELECT`, уже после рекурсии.

- `level` к этому моменту посчитан внутри CTE (`1` у корня, дальше `parent.level + 1`).
- `repeat('    ', level - 1)` добавляет по четыре пробела на каждый уровень ниже корня.
- `|| name` приклеивает название отдела к отступу.
- `ORDER BY ord` не убирают: без него строки снова перемешаются, даже если в `name` есть пробелы.

Итоговый запрос с комментариями:

```sql

WITH RECURSIVE subtree AS (

    SELECT id,
           parent_id,
           name,
           1 AS level                    -- нужен для repeat во внешнем SELECT
    FROM departments
    WHERE name = 'Компания'

    UNION ALL

    SELECT child.id,
           child.parent_id,
           child.name,
           parent.level + 1
    FROM departments child
    JOIN subtree parent
      ON parent.id = child.parent_id

) SEARCH DEPTH FIRST BY id SET ord       -- порядок обхода; ord в SELECT ниже не обязателен

SELECT id,
       parent_id,
       repeat('    ', level - 1) || name AS name,   -- наглядное дерево в одной колонке
       level
FROM subtree
ORDER BY ord;                              -- сначала ord, потом уже красивый name
```

Результат на учебной базе (пробелы в `name` — реальные, из `repeat`):

| id | parent_id | name | level |
|---:|---:|---|---:|
| 1 | NULL | Компания | 1 |
| 2 | 1 | Коммерческий департамент | 2 |
| 5 | 2 | Отдел продаж | 3 |
| 6 | 2 | Отдел маркетинга | 3 |
| 13 | 6 | Группа аналитики | 4 |
| 3 | 1 | Логистический департамент | 2 |
| 7 | 3 | Склад | 3 |
| 8 | 3 | Доставка | 3 |
| 4 | 1 | Технический департамент | 2 |
| 9 | 4 | Отдел разработки | 3 |
| 11 | 9 | Группа backend | 4 |
| 12 | 9 | Группа frontend | 4 |
| 10 | 4 | Отдел тестирования | 3 |

В markdown-таблице отступы не видны; в клиенте (`psql`, DBeaver и т.п.) колонка `name` выглядит как дерево блоками — как на вашем скриншоте к [`08-cte-tree-report.md`](08-cte-tree-report.md).

Разделение ролей:

| Что | Где в запросе | Зачем |
|---|---|---|
| обход и порядок веток | `SEARCH DEPTH FIRST BY id SET ord` + `ORDER BY ord` | строки идут сверху вниз по дереву |
| глубина для отступа | `level` внутри CTE | число для `repeat` |
| картинка в тексте | `repeat('    ', level - 1) \|\| name` во внешнем SELECT | только отображение, на рекурсию не влияет |

Тот же приём с `repeat` можно использовать и в варианте с ручным `path`: там порядок — `ORDER BY path`, отступ — так же из `level` во внешнем `SELECT`.

---

## Где в тексте запроса что лежит

```text
WITH RECURSIVE subtree AS (
    … стартовая часть …
    UNION ALL
    … рекурсивная часть с JOIN subtree …
) SEARCH DEPTH FIRST BY id SET ord    ← сразу после закрывающей скобки CTE, до внешнего SELECT
SELECT … FROM subtree ORDER BY ord;
```

| Фрагмент | Роль |
|---|---|
| до `UNION ALL` внутри `subtree` | стартовая часть, один раз |
| после `UNION ALL`, с `JOIN subtree` | повторяющаяся часть |
| `SEARCH DEPTH FIRST BY id SET ord` | **не** рекурсия и **не** старт; порядок обхода и служебный столбец `ord` |
| `SELECT … FROM subtree` | обычный запрос после готового CTE |

`WITH RECURSIVE` по-прежнему относится ко всему списку `WITH`. Здесь в списке один CTE — `subtree`.

---

## Часть 1. Пошагово: накопленный результат и рабочая таблица

Обозначения те же, что в [`08-cte-tree-report.md`](08-cte-tree-report.md), часть 4, и в [`08-cte.md`](08-cte.md) («Все отделы внутри департамента»): **накопленный результат** — все строки с начала рекурсии; **рабочая таблица** — только строки последнего повтора (вход для следующего `JOIN subtree`).

Повторяющая часть:

```sql

SELECT child.id,
       child.parent_id,
       child.name,
       parent.level + 1
FROM departments child
JOIN subtree parent
  ON parent.id = child.parent_id
```

- `child` — псевдоним таблицы `departments`;
- `parent` — псевдоним **текущей** рабочей таблицы `subtree`.

### Старт

Стартовый `SELECT`:

```sql

SELECT id, parent_id, name, 1 AS level
FROM departments
WHERE name = 'Компания';
```

**Накопленный результат `subtree`:**

| id | parent_id | name | level |
|---:|---:|---|---:|
| 1 | NULL | Компания | 1 |

**Рабочая таблица:**

| id | parent_id | name | level |
|---:|---:|---|---:|
| 1 | NULL | Компания | 1 |

### Повтор 1: дети компании (`parent_id = 1`)

Эквивалент повторяющей части с подставленной рабочей строкой:

```sql

SELECT child.id,
       child.parent_id,
       child.name,
       parent.level + 1
FROM departments child
JOIN (
    SELECT 1 AS id, NULL::integer AS parent_id, 'Компания' AS name, 1 AS level
) parent
  ON parent.id = child.parent_id;
```

**Строки, которые вернул этот повтор:**

| id | parent_id | name | level |
|---:|---:|---|---:|
| 2 | 1 | Коммерческий департамент | 2 |
| 3 | 1 | Логистический департамент | 2 |
| 4 | 1 | Технический департамент | 2 |

**Накопленный результат `subtree`:**

| id | parent_id | name | level |
|---:|---:|---|---:|
| 1 | NULL | Компания | 1 |
| 2 | 1 | Коммерческий департамент | 2 |
| 3 | 1 | Логистический департамент | 2 |
| 4 | 1 | Технический департамент | 2 |

**Новая рабочая таблица:**

| id | parent_id | name | level |
|---:|---:|---|---:|
| 2 | 1 | Коммерческий департамент | 2 |
| 3 | 1 | Логистический департамент | 2 |
| 4 | 1 | Технический департамент | 2 |

### Повтор 2: дети департаментов `2`, `3`, `4`

**Строки, которые вернул повтор 2:**

| id | parent_id | name | level |
|---:|---:|---|---:|
| 5 | 2 | Отдел продаж | 3 |
| 6 | 2 | Отдел маркетинга | 3 |
| 7 | 3 | Склад | 3 |
| 8 | 3 | Доставка | 3 |
| 9 | 4 | Отдел разработки | 3 |
| 10 | 4 | Отдел тестирования | 3 |

**Накопленный результат `subtree`:**

| id | parent_id | name | level |
|---:|---:|---|---:|
| 1 | NULL | Компания | 1 |
| 2 | 1 | Коммерческий департамент | 2 |
| 3 | 1 | Логистический департамент | 2 |
| 4 | 1 | Технический департамент | 2 |
| 5 | 2 | Отдел продаж | 3 |
| 6 | 2 | Отдел маркетинга | 3 |
| 7 | 3 | Склад | 3 |
| 8 | 3 | Доставка | 3 |
| 9 | 4 | Отдел разработки | 3 |
| 10 | 4 | Отдел тестирования | 3 |

**Новая рабочая таблица:** шесть строк из таблицы «вернул повтор 2».

### Повтор 3: дети отделов `5`, `6`, `7`, `8`, `9`, `10`

Дети находятся только у отделов `6` и `9`.

**Строки, которые вернул повтор 3:**

| id | parent_id | name | level |
|---:|---:|---|---:|
| 13 | 6 | Группа аналитики | 4 |
| 11 | 9 | Группа backend | 4 |
| 12 | 9 | Группа frontend | 4 |

**Накопленный результат `subtree`:**

| id | parent_id | name | level |
|---:|---:|---|---:|
| 1 | NULL | Компания | 1 |
| 2 | 1 | Коммерческий департамент | 2 |
| 3 | 1 | Логистический департамент | 2 |
| 4 | 1 | Технический департамент | 2 |
| 5 | 2 | Отдел продаж | 3 |
| 6 | 2 | Отдел маркетинга | 3 |
| 7 | 3 | Склад | 3 |
| 8 | 3 | Доставка | 3 |
| 9 | 4 | Отдел разработки | 3 |
| 10 | 4 | Отдел тестирования | 3 |
| 13 | 6 | Группа аналитики | 4 |
| 11 | 9 | Группа backend | 4 |
| 12 | 9 | Группа frontend | 4 |

**Новая рабочая таблица:**

| id | parent_id | name | level |
|---:|---:|---|---:|
| 13 | 6 | Группа аналитики | 4 |
| 11 | 9 | Группа backend | 4 |
| 12 | 9 | Группа frontend | 4 |

### Повтор 4: остановка

Рабочая таблица: `11`, `12`, `13`. Детей в `departments` нет.

Повторяющаяся часть возвращает пустую таблицу.

**Накопленный результат не меняется** (те же 13 строк, что в таблице выше).

**Новая рабочая таблица пуста** — рекурсия закончена.

Столбец `ord` появляется **не в вашем** `SELECT`, а из-за `SEARCH DEPTH FIRST BY id SET ord` после `)` CTE. PostgreSQL заполняет `ord` при том же обходе, для которого показаны волны выше.

---

## Часть 2. Что добавляет `SEARCH DEPTH FIRST BY id SET ord`

Разбор по словам:

| Фрагмент | Смысл |
|---|---|
| `SEARCH` | включить встроенный расчёт порядка обхода |
| `DEPTH FIRST` | обход **в глубину**: сначала уйти в первую ветку до листьев, потом следующую ветку того же родителя |
| `BY id` | среди **соседей** (детей одного родителя) сравнивать и обходить по возрастанию `id` |
| `SET ord` | имя нового столбца в результате CTE; его значения можно использовать в `ORDER BY ord` |

Столбец `ord` **не нужно** писать в стартовом и рекурсивном `SELECT`. PostgreSQL добавляет его к каждой строке CTE сам.

На учебной базе (с отладочным выводом `ord`):

```sql

WITH RECURSIVE subtree AS (
    SELECT id, parent_id, name, 1 AS level
    FROM departments
    WHERE name = 'Компания'
    UNION ALL
    SELECT child.id, child.parent_id, child.name, parent.level + 1
    FROM departments child
    JOIN subtree parent ON parent.id = child.parent_id
) SEARCH DEPTH FIRST BY id SET ord
SELECT id, parent_id, name, level, ord
FROM subtree
ORDER BY ord;
```

| id | parent_id | name | level | ord |
|---:|---:|---|---:|---|
| 1 | NULL | Компания | 1 | {(1)} |
| 2 | 1 | Коммерческий департамент | 2 | {(1),(2)} |
| 5 | 2 | Отдел продаж | 3 | {(1),(2),(5)} |
| 6 | 2 | Отдел маркетинга | 3 | {(1),(2),(6)} |
| 13 | 6 | Группа аналитики | 4 | {(1),(2),(6),(13)} |
| 3 | 1 | Логистический департамент | 2 | {(1),(3)} |
| 7 | 3 | Склад | 3 | {(1),(3),(7)} |
| 8 | 3 | Доставка | 3 | {(1),(3),(8)} |
| 4 | 1 | Технический департамент | 2 | {(1),(4)} |
| 9 | 4 | Отдел разработки | 3 | {(1),(4),(9)} |
| 11 | 9 | Группа backend | 4 | {(1),(4),(9),(11)} |
| 12 | 9 | Группа frontend | 4 | {(1),(4),(9),(12)} |
| 10 | 4 | Отдел тестирования | 3 | {(1),(4),(10)} |

Формат `{(1),(2),(5)}` — служебное представление ключа порядка (цепочка значений столбца `id` от корня до текущей строки). Для сортировки достаточно `ORDER BY ord`; расшифровывать тип вручную не обязательно.

Порядок строк **совпадает** с вариантом из `08-cte-tree-report.md`, где путь собирали массивом `path` и сортировали `ORDER BY path`, потому что и там, и здесь обход в глубину, а среди соседей используется `id`.

---

## Часть 3. Почему «Отдел тестирования» ниже групп backend и frontend

У компании три ребёнка с `id` 2, 3, 4 — обход идёт сначала по ветке 2, затем 3, затем 4.

Внутри ветки 4:

- сначала ребёнок с меньшим `id`: отдел разработки `9`;
- у `9` — группы `11` и `12`;
- только после завершения ветки через `9` обрабатывается следующий ребёнок `4` — отдел тестирования `10`.

Поэтому в списке `10` идёт **после** `11` и `12`, хотя у всех троих `level = 3` или `4`. Сортировка по `level, id` такую картину **ломает**; `ORDER BY ord` или `ORDER BY path` — сохраняет.

---

## Часть 4. Сравнение с ручным `path`

| | `array[id]` + `ORDER BY path` | `SEARCH DEPTH FIRST … SET ord` |
|---|---|---|
| Где задаётся порядок | вы сами пишете `path` в старт и рекурсию | PostgreSQL считает `ord` |
| Столбец в `SELECT` CTE | `path` нужно объявить | `ord` добавляется автоматически |
| Обход | depth-first при `path \|\| child.id` | явно `DEPTH FIRST` |
| Отступы в `name` | `repeat` во внешнем SELECT | то же самое: `repeat` во внешнем SELECT |
| Портативность | идея понятна в любом SQL | синтаксис PostgreSQL (и SQL:2016) |

Документация прямо говорит, что `SEARCH` разворачивается во что-то похожее на ручной вариант с `ARRAY[…]` и `ORDER BY path` (см. цитату в начале документа).

---

## Часть 5. Обход в ширину (кратко)

Если нужен порядок **по уровням** (сначала все на глубине 2, потом все на 3 и т.д.), вместо `DEPTH FIRST` пишут `BREADTH FIRST`:

```sql

) SEARCH BREADTH FIRST BY id SET ord
```

**Цитата (PostgreSQL 16):**

> The recursive query evaluation algorithm produces its output in breadth-first search order. However, this is an order of evaluation detail and it is probably unsound to rely on it. The order of the rows within each level is certainly undefined, so some explicit ordering might be desired in any case.

**Перевод:**

> Алгоритм вычисления рекурсивного запроса выдаёт результат в порядке обхода в ширину. Однако это деталь реализации, и полагаться на неё, вероятно, ненадёжно. Порядок строк внутри одного уровня определённо не задан, поэтому явная сортировка может понадобиться в любом случае.

Для отчёта «дерево блоками» обычно нужен именно **`DEPTH FIRST`**, как в вашем запросе.

---

## Связанные материалы

- Ручной массив `path`, полные таблицы по шагам: [`08-cte-tree-report.md`](08-cte-tree-report.md)
- Текстовый путь `|| ' / ' ||`: [`08-cte.md` — Путь от корня до отдела](08-cte.md#путь-от-корня-до-отдела)
- Общая теория рекурсии: [`08-cte.md`](08-cte.md)
- Схемы: [`08-cte-schema.md`](08-cte-schema.md)
