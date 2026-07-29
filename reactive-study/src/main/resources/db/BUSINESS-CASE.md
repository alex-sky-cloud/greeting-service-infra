# Reactive Shop — бизнес-кейс для лабораторных работ

Учебный домен: **интернет-магазин с потоковой обработкой заказов**.  
Объём данных после миграций `V4`…`V11`: **~100 000+ строк** в «тяжёлых» таблицах.

---

## Сущности

| Таблица | Назначение | ~Записей |
|---------|------------|----------|
| `users` | Покупатели | 5 000 |
| `product_categories` | Категории каталога | 10 |
| `products` | Товары | 200 |
| `orders` | Заказы | **100 000** |
| `order_status_events` | История смены статуса заказа | **100 000** |
| `payment_attempts` | Попытки списания (очередь платежей) | **100 000** |

Демо-данные из `V3` (3 пользователя, 4 заказа) заменяются bulk-сидами в `V10`/`V11`.

---

## Сценарии и операторы Reactor

| Сценарий | Операторы | Таблицы / запрос |
|----------|-----------|------------------|
| Потоковая выгрузка всех заказов без OOM | `Flux`, `limitRate`, backpressure | `orders` (100k) |
| Профиль пользователя + список заказов | `flatMap`, `map` | `users` → `orders` |
| Последовательная обработка платежей по очереди | `concatMap`, `then` | `payment_attempts` |
| Параллельная загрузка user + stats + каталог | `Mono.zip` | `users`, `orders`, `products` |
| Слияние двух потоков событий | `merge`, `mergeWith` | `order_status_events` |
| Строгий порядок обработки заказов | `concatMap` vs `flatMap` | `orders` по `user_id` |
| Батчевая запись событий | `buffer`, `flatMap` с concurrency | `order_status_events` |
| Фильтрация + агрегация в потоке | `filter`, `groupBy`, `reduce` | `orders` по `status` |

Материалы: [`docs/interview/reactive/`](../../../../docs/interview/reactive/).

---

## Структура Java-пакетов (классы — позже)

```
com.example.reactivestudy/
  domain/
    model/          ← сущности R2DBC (User, Order, Product, …)
    dto/            ← ответы API, сводки, сравнения операторов
  service/          ← реактивная бизнес-логика (Flux/Mono)
  controller/       ← WebFlux REST
```

---

## Первый запуск с bulk-миграциями

Миграции `V10`–`V11` вставляют ~100k строк — **первый старт может занять 1–3 минуты**.  
В логах Flyway: `Successfully applied … migrations … now at version v11`.

Проверка объёма:

```bash

docker exec reactive-study-postgres psql -U app -d reactive_study -c \
  "SELECT 'orders' AS t, count(*) FROM reactive_study.orders
   UNION ALL SELECT 'payment_attempts', count(*) FROM reactive_study.payment_attempts
   UNION ALL SELECT 'order_status_events', count(*) FROM reactive_study.order_status_events;"
```
