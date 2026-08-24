# Задание 2.1 — backpressure, `limitRate`

Тема интервью: **2.1 Что такое backpressure**.  
Демо-классы (`T02BackpressureLabController`, `T02BackpressureLabService`, …) **не менять**.  
Пишешь **новые** файлы. Скинь мне только их.

Правила из корневого `PLAN.md`: JavaDoc на класс и метод, HTML-списки, примеры в `<pre>{@code ... }</pre>`, однострочные `//` у операторов, без магических литералов в вызовах операторов, префикс `T02`.

---

## Зачем

WebFlux-подписчик часто делает `request(Long.MAX_VALUE)`. Нужно самим нарезать спрос к R2DBC пачками, чтобы выгрузка заказов не пыталась забрать всю таблицу одним запросом. Элементы **нельзя** дропать (`onBackpressureDrop` — это 2.2).

---

## Что создать (четыре файла)

Кладёшь в те же пакеты, что и лаба:

| Файл | Пакет |
|---|---|
| `T02OrderExportDto.java` | `...t02_backpressure.domain` |
| `T02OrderExportRepository.java` | `...t02_backpressure.repository` |
| `T02OrderExportService.java` | `...t02_backpressure.service` |
| `T02OrderExportController.java` | `...t02_backpressure.controller` |

Отдельный репозиторий нужен, чтобы не плодить второй бин на `T02OrderRepository` и чтобы ты сам описал R2DBC-интерфейс.

---

## 1. DTO — `T02OrderExportDto`

Record: `id`, `productName`, `amount`.  
JavaDoc: это ответ HTTP, не entity.

---

## 2. Репозиторий — `T02OrderExportRepository`

```java
public interface T02OrderExportRepository extends ReactiveCrudRepository<T02OrderEntity, Long> {
}
```

Entity **переиспользуй** `T02OrderEntity` (таблица `orders` уже есть).  
JavaDoc на интерфейс: зачем отдельный тип (уникальное имя бина `t02OrderExportRepository`) и пример:

<pre>{@code
exportRepository.findAll();
}</pre>

Свой query-метод писать не обязательно: достаточно `findAll()`.

---

## 3. Сервис — `T02OrderExportService`

`@Service`. В конструктор — `T02OrderExportRepository`.

Константа класса (не литерал в цепочке):

```text
MAX_ORDERS_PER_REQUEST = 50
DEFAULT_ORDERS_PER_REQUEST = 10
```

Метод:

```java
public Flux<T02OrderExportDto> exportOrders(int requestedBatchSize)
```

Поведение:

1. Нормализуй размер пачки в **локальные** переменные с понятными именами:
   - меньше 1 → `DEFAULT_ORDERS_PER_REQUEST`
   - больше `MAX_ORDERS_PER_REQUEST` → `MAX_ORDERS_PER_REQUEST`
   - иначе как пришло
2. `findAll()`
3. `limitRate(нормализованный размер)` — комментарий, что это нарезка `request`, не drop
4. `map` в `T02OrderExportDto` — комментарий, что mapper синхронный

JavaDoc метода: зачем `limitRate`, что проверяем (все заказы доходят, меняется только prefetch). Пример цепочки в `<pre>{@code ... }</pre>`.

**Нельзя:** `onBackpressureDrop`, `onBackpressureLatest`, `subscribeOn(boundedElastic)` (это не 2.1), `collectList()` (тогда смысл пачек на HTTP пропадает).

---

## 4. Контроллер — `T02OrderExportController`

- `@RequestMapping("/api/t02/homework")`
- метод:

```java
@GetMapping("/orders")
public Flux<T02OrderExportDto> orders(
        @RequestParam(name = "batchSize") int batchSize)
```

Прокинь `batchSize` в сервис.  
JavaDoc: какой URL, какой query-param, что произойдёт при `batchSize=0` и `batchSize=1000`.

Default на `@RequestParam` не обязателен: нормализация в сервисе.

---

## Как проверить у себя

Профиль `local`, Postgres модуля поднят.

```text
GET http://localhost:8084/api/t02/homework/orders?batchSize=2
```

Ожидание: все seed-заказы (4 штуки), не 2. `batchSize` режет demand, не результат.

Демо-пути `/api/t02/orders-limit-rate` и `/paced-ids` должны по-прежнему работать.

---

## Что прислать мне

Только эти четыре `.java` (вложениями). Я разложу по пакетам, соберу, прогоню запрос/тест и напишу, что ок или что поправить.
