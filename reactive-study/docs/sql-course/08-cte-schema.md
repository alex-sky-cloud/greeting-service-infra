# Схемы таблиц учебной базы

Деревья нарисованы по строкам базы `reactive_study`. Стрелка вниз — от старшей строки к младшей.

Цвет блока в дереве — это уровень вложенности, один и тот же на всех деревьях. Подпись внутри блока того же оттенка, только темнее, чтобы строка не сливалась с заливкой:

- голубой — корень, ссылка на себя пустая
- зелёный — второй уровень
- персиковый — третий уровень
- сиреневый — четвёртый уровень

## departments

`parent_id` ссылается на `id` этой же таблицы. У корня «Компания» `parent_id` пустой.

```plantuml
@startwbs
title departments — дерево отделов
skinparam shadowing false
skinparam titleFontSize 22
<style>
wbsDiagram {
  node {
    FontSize 20
    Padding 16
    Margin 12
    RoundCorner 8
    LineColor #5A7A94
  }
  rootNode {
    BackgroundColor #7EB6D9
    FontColor #0E3D5C
  }
  depth(1) {
    BackgroundColor #7FBF9A
    FontColor #0E4A32
  }
  depth(2) {
    BackgroundColor #F0C48A
    FontColor #6A3E10
  }
  depth(3) {
    BackgroundColor #D4B4E0
    FontColor #4A2A62
  }
}
</style>

* Компания\n id 1\n parent_id пусто
** Коммерческий департамент\n id 2\n parent_id 1
*** Отдел продаж\n id 5\n parent_id 2
*** Отдел маркетинга\n id 6\n parent_id 2
**** Группа аналитики\n id 13\n parent_id 6
** Логистический департамент\n id 3\n parent_id 1
*** Склад\n id 7\n parent_id 3
*** Доставка\n id 8\n parent_id 3
** Технический департамент\n id 4\n parent_id 1
*** Отдел разработки\n id 9\n parent_id 4
**** Группа backend\n id 11\n parent_id 9
**** Группа frontend\n id 12\n parent_id 9
*** Отдел тестирования\n id 10\n parent_id 4

@endwbs
```

## employees

`manager_id` ссылается на `id` этой же таблицы. У корня «Ирина Соколова» `manager_id` пустой.

Отдельно от этого дерева: `employees.department_id` ссылается на `departments.id`. Это связь между двумя таблицами, не ссылка сотрудника на самого себя.

```plantuml
@startwbs
title employees — дерево начальников
skinparam shadowing false
skinparam titleFontSize 22
<style>
wbsDiagram {
  node {
    FontSize 20
    Padding 16
    Margin 12
    RoundCorner 8
    LineColor #5A7A94
  }
  rootNode {
    BackgroundColor #7EB6D9
    FontColor #0E3D5C
  }
  depth(1) {
    BackgroundColor #7FBF9A
    FontColor #0E4A32
  }
  depth(2) {
    BackgroundColor #F0C48A
    FontColor #6A3E10
  }
  depth(3) {
    BackgroundColor #D4B4E0
    FontColor #4A2A62
  }
}
</style>

* Ирина Соколова\n генеральный директор\n id 1\n manager_id пусто
** Павел Морозов\n директор по продажам\n id 2\n manager_id 1
*** Сергей Гусев\n руководитель отдела продаж\n id 5\n manager_id 2
**** Мария Зайцева\n менеджер по продажам\n id 6\n manager_id 5
**** Денис Волков\n менеджер по продажам\n id 7\n manager_id 5
** Ольга Крылова\n директор по логистике\n id 3\n manager_id 1
*** Елена Орлова\n руководитель склада\n id 8\n manager_id 3
**** Игорь Беляев\n кладовщик\n id 9\n manager_id 8
**** Наталья Ершова\n кладовщик\n id 10\n manager_id 8
**** Светлана Ким\n курьер\n id 15\n manager_id 8
** Антон Лебедев\n технический директор\n id 4\n manager_id 1
*** Виктор Сомов\n руководитель разработки\n id 11\n manager_id 4
**** Алексей Тихонов\n разработчик\n id 12\n manager_id 11
**** Юлия Карпова\n разработчик\n id 13\n manager_id 11
**** Роман Фадеев\n тестировщик\n id 14\n manager_id 11

@endwbs
```

## catalog_sections

`parent_id` ссылается на `id` этой же таблицы. Корней три, у каждого `parent_id` пустой. Поэтому ниже три дерева одной таблицы.

### Корень «Техника»

```plantuml
@startwbs
title catalog_sections — корень «Техника»
skinparam shadowing false
skinparam titleFontSize 22
<style>
wbsDiagram {
  node {
    FontSize 20
    Padding 16
    Margin 12
    RoundCorner 8
    LineColor #5A7A94
  }
  rootNode {
    BackgroundColor #7EB6D9
    FontColor #0E3D5C
  }
  depth(1) {
    BackgroundColor #7FBF9A
    FontColor #0E4A32
  }
  depth(2) {
    BackgroundColor #F0C48A
    FontColor #6A3E10
  }
  depth(3) {
    BackgroundColor #D4B4E0
    FontColor #4A2A62
  }
}
</style>

* Техника\n id 1\n parent_id пусто
** Электроника\n id 11\n parent_id 1
*** Смартфоны\n id 21\n parent_id 11
**** Android-смартфоны\n id 42\n parent_id 21
**** iPhone\n id 43\n parent_id 21
*** Ноутбуки\n id 22\n parent_id 11
** Авто\n id 18\n parent_id 1
*** Шины и диски\n id 35\n parent_id 18
*** Автозапчасти\n id 36\n parent_id 18

@endwbs
```

### Корень «Дом»

```plantuml
@startwbs
title catalog_sections — корень «Дом»
skinparam shadowing false
skinparam titleFontSize 22
<style>
wbsDiagram {
  node {
    FontSize 20
    Padding 16
    Margin 12
    RoundCorner 8
    LineColor #5A7A94
  }
  rootNode {
    BackgroundColor #7EB6D9
    FontColor #0E3D5C
  }
  depth(1) {
    BackgroundColor #7FBF9A
    FontColor #0E4A32
  }
  depth(2) {
    BackgroundColor #F0C48A
    FontColor #6A3E10
  }
  depth(3) {
    BackgroundColor #D4B4E0
    FontColor #4A2A62
  }
}
</style>

* Дом\n id 2\n parent_id пусто
** Дом и быт\n id 12\n parent_id 2
*** Посуда\n id 23\n parent_id 12
*** Мебель\n id 24\n parent_id 12
** Продукты\n id 16\n parent_id 2
*** Напитки\n id 31\n parent_id 16
*** Снеки\n id 32\n parent_id 16
** Офис\n id 20\n parent_id 2
*** Канцелярия\n id 39\n parent_id 20
*** Бумага\n id 40\n parent_id 20

@endwbs
```

### Корень «Жизнь и отдых»

```plantuml
@startwbs
title catalog_sections — корень «Жизнь и отдых»
skinparam shadowing false
skinparam titleFontSize 22
<style>
wbsDiagram {
  node {
    FontSize 20
    Padding 16
    Margin 12
    RoundCorner 8
    LineColor #5A7A94
  }
  rootNode {
    BackgroundColor #7EB6D9
    FontColor #0E3D5C
  }
  depth(1) {
    BackgroundColor #7FBF9A
    FontColor #0E4A32
  }
  depth(2) {
    BackgroundColor #F0C48A
    FontColor #6A3E10
  }
  depth(3) {
    BackgroundColor #D4B4E0
    FontColor #4A2A62
  }
}
</style>

* Жизнь и отдых\n id 3\n parent_id пусто
** Спорт\n id 13\n parent_id 3
*** Тренажёры\n id 25\n parent_id 13
*** Велосипеды\n id 26\n parent_id 13
** Книги\n id 14\n parent_id 3
*** Художественная литература\n id 27\n parent_id 14
*** Учебники\n id 28\n parent_id 14
** Одежда\n id 15\n parent_id 3
*** Верхняя одежда\n id 29\n parent_id 15
*** Обувь\n id 30\n parent_id 15
** Игрушки\n id 17\n parent_id 3
*** Конструкторы\n id 33\n parent_id 17
*** Настольные игры\n id 34\n parent_id 17
** Здоровье\n id 19\n parent_id 3
*** Витамины\n id 37\n parent_id 19
*** Медтехника\n id 38\n parent_id 19
** Подарки\n id 41\n parent_id 3

@endwbs
```

## Таблицы без ссылки на себя

У этих таблиц нет столбца, который ссылается на ту же таблицу. Стрелка подписана именем внешнего ключа и идёт к таблице, на которую он ссылается.

Цвет здесь отличает таблицу от таблицы, а не уровень дерева. Подпись внутри блока того же оттенка, только темнее.

`catalog_sections` на этой схеме только как цель `products.section_id`. Её собственное дерево — в разделе выше.

```plantuml
@startuml
title Таблицы без ссылки на себя
hide circle
hide stereotype
skinparam shadowing false
skinparam linetype ortho
skinparam nodesep 110
skinparam ranksep 100
skinparam classFontSize 22
skinparam classAttributeFontSize 18
skinparam classHeaderFontSize 22
skinparam ArrowFontSize 16
skinparam titleFontSize 22
skinparam class {
  BorderColor #5A7A94
}
<style>
classDiagram {
  class {
    FontSize 22
    AttributeFontSize 18
    Padding 16
    Margin 12
    RoundCorner 8
    LineColor #5A7A94
  }
  .users {
    BackgroundColor #7EB6D9
    FontColor #0E3D5C
    HeaderFontColor #0E3D5C
    AttributeFontColor #0E3D5C
  }
  .categories {
    BackgroundColor #C5B3E6
    FontColor #3D2468
    HeaderFontColor #3D2468
    AttributeFontColor #3D2468
  }
  .products {
    BackgroundColor #F0C48A
    FontColor #6A3E10
    HeaderFontColor #6A3E10
    AttributeFontColor #6A3E10
  }
  .orders {
    BackgroundColor #E8927A
    FontColor #6A2A18
    HeaderFontColor #6A2A18
    AttributeFontColor #6A2A18
  }
  .events {
    BackgroundColor #E8A8C8
    FontColor #6A2848
    HeaderFontColor #6A2848
    AttributeFontColor #6A2848
  }
  .payments {
    BackgroundColor #7FBF9A
    FontColor #0E4A32
    HeaderFontColor #0E4A32
    AttributeFontColor #0E4A32
  }
  .sections {
    BackgroundColor #6AADC4
    FontColor #0E3D4A
    HeaderFontColor #0E3D4A
    AttributeFontColor #0E3D4A
  }
}
</style>

class "users" as users <<users>> {
  PK id
  --
  email
  full_name
}

class "product_categories" as categories <<categories>> {
  PK id
  --
  code
  name
}

class "products" as products <<products>> {
  PK id
  --
  FK category_id
  FK section_id
  sku
  name
  price
}

class "orders" as orders <<orders>> {
  PK id
  --
  FK user_id
  FK product_id
  amount
  status
}

class "order_status_events" as events <<events>> {
  PK id
  --
  FK order_id
  status
}

class "payment_attempts" as payments <<payments>> {
  PK id
  --
  FK order_id
  attempt_no
  amount
  status
}

class "catalog_sections" as sections <<sections>> {
  PK id
  --
  дерево разделов
  см. раздел выше
}

users -[hidden]right- categories
categories -[hidden]right- sections

products -up-> categories : category_id
products -up-> sections : section_id
orders -up-> users : user_id
orders -up-> products : product_id
events -up-> orders : order_id
payments -up-> orders : order_id

@enduml
```
