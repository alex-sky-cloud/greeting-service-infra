# Задание 2.1 — backpressure, `limitRate`

Демо-классы лабы **не менять**. Новые файлы. Домен: **показания счётчиков** (`readings`), не магазин.

Правила: JavaDoc, HTML-списки, `<pre>{@code ... }</pre>`, `//` у операторов, без magic numbers, префикс `T02`, `@Table(..., schema = "reactor_workshop")`, аннотация над полем, пустая строка между полями.

---

## Что создать

| Файл | Пакет |
|---|---|
| `T02ReadingExportDto.java` | domain |
| `T02ReadingExportRepository.java` | repository |
| `T02ReadingExportService.java` | service |
| `T02ReadingExportController.java` | controller |

---

## DTO

Record: `id`, `meterId`, `kwh`.

---

## Репозиторий

```

public interface T02ReadingExportRepository extends ReactiveCrudRepository<T02ReadingEntity, Long> {
}

```

Переиспользуй `T02ReadingEntity`. Свой query не обязателен.

---

## Сервис

Константы: `MAX_READINGS_PER_REQUEST = 500`, `DEFAULT_READINGS_PER_REQUEST = 50`.

```

public Flux<T02ReadingExportDto> exportReadings(int requestedBatchSize)

```

Нормализация пачки, `findAll()`, `limitRate`, `map` в DTO. Без Drop/Latest, без `collectList`, без `boundedElastic`.

---

## Контроллер

`/api/t02/homework/readings?batchSize=`

```

GET http://localhost:8084/api/t02/homework/readings?batchSize=50

```

Ожидание: поток показаний (их много). `batchSize` режет demand, не «верни ровно 50 строк и остановись».

Пришли четыре `.java`.
