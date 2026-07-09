# application.yml — назначение свойств

Конфигурация модуля **reactor-cold-hot-publisher**. Значения из секции `demo.*` биндятся в
`com.example.coldhotpublisher.config.DemoProperties` (`@ConfigurationProperties`).

См. также Javadoc в `DemoProperties.java`.

## Порты модулей репозитория

| Модуль | HTTP-порт |
|--------|-----------|
| `app` | 8080 |
| `reactive-demo` | 8081 |
| `reactor-cold-hot-publisher` | **8082** (по умолчанию) |

Чтобы сменить порт этого модуля, измените **только** `demo.application.port`. Свойство
`server.port` ссылается на него через `${demo.application.port}`.

---

## `server.port`

| Ключ | Значение по умолчанию | Назначение |
|------|----------------------|------------|
| `server.port` | `${demo.application.port}` | Порт HTTP API магазина (`/api/shop/...`). |

**Где используется:** Spring Boot (Netty). Внешние системы в учебном стенде **не** публикуются как отдельные HTTP-endpoint'ы.

---

## `demo.application`

| Ключ | По умолчанию | Назначение | Где используется |
|------|--------------|------------|------------------|
| `demo.application.port` | `8082` | HTTP-порт API магазина | `server.port` |

---

## Исходящие WebClient (registry)

Несколько каналов к внешним системам — **не** через `@Qualifier`, а по паттерну из
`docs/interview/Архитурный подход к выбору реализации в Spring без Qualifier, Primary, Profile.md`:

| Класс | Роль |
|-------|------|
| `ApiClientKind` | доменный ключ (CATALOG, FRAUD, …) |
| `ExternalApiClient` | контракт + `getKind()` |
| `*ExternalApiClient` | self-describing реализации (`@Component`) |
| `ExternalApiClientConfiguration` | `List<ExternalApiClient>` → `Map` → registry |
| `ExternalApiClientRegistry` | выбор по `ApiClientKind` в сервисах |
| `ExternalSystemStubExchange` | подмена сети в `WebClient` (учебный стенд) |
| `ExternalSystemStubResponses` | данные и задержки ответов «внешних» систем |

В проде вместо `ExternalSystemStubExchange` — обычный HTTP-коннектор с реальным `baseUrl`.

---

## `demo.stub-timing`

Искусственные задержки ответов «внешних» систем, чтобы в логах было видно cold/hot-поведение.

| Ключ | По умолчанию | Назначение | Где используется |
|------|--------------|------------|------------------|
| `product-delay-ms` | `300` | Задержка `GET /products/{id}` | `ExternalSystemStubResponses#product` |
| `fraud-delay-ms` | `400` | Задержка `POST /fraud/check/{orderId}` | `ExternalSystemStubResponses#fraudDecision` |
| `tariff-delay-ms` | `300` | Задержка `GET /tariffs` | `ExternalSystemStubResponses#tariffs` |
| `status-element-delay-ms` | `700` | Пауза между событиями SSE статусов заказа | `ExternalSystemStubResponses#orderStatusStream` |
| `status-step-seconds` | `1` | Шаг `createdAt` между статусами в потоке | `ExternalSystemStubResponses#orderStatusStream` |
| `quote-interval-ms` | `500` | Интервал котировок в SSE | `ExternalSystemStubResponses#quoteStream` |

---

## `demo.stub-data`

Тестовые данные, которые подставляет `ExternalSystemStubResponses`.

| Ключ | По умолчанию | Назначение | Где используется |
|------|--------------|------------|------------------|
| `product-name-prefix` | `"Demo product "` | Префикс имени товара | `ExternalSystemStubResponses#product` |
| `product-price` | `99.90` | Цена товара | `ExternalSystemStubResponses#product` |
| `fraud-status` | `ALLOW` | Результат anti-fraud | `ExternalSystemStubResponses#fraudDecision` |
| `fraud-reason` | `stub-approved` | Причина решения fraud | `ExternalSystemStubResponses#fraudDecision` |
| `tariff-version` | `v1-local` | Версия тарифной таблицы | `ExternalSystemStubResponses#tariffs` |
| `tariff-rows` | BY/PL/DE | Строки тарифов (`zone`, `price`) | `ExternalSystemStubResponses#tariffs` |
| `order-statuses` | CREATED → SHIPPED | Последовательность статусов заказа | `ExternalSystemStubResponses#orderStatusStream` |
| `quote-base-bid` | `1.1000` | Начальный bid | `ExternalSystemStubResponses#quoteStream` |
| `quote-bid-step` | `0.0001` | Приращение bid на каждом тике | `ExternalSystemStubResponses#quoteStream` |
| `quote-ask-spread` | `0.0002` | Спред ask относительно bid | `ExternalSystemStubResponses#quoteStream` |
| `quote-max-events` | `20` | Число котировок в потоке | `ExternalSystemStubResponses#quoteStream` |

---

## `demo.cache`

| Ключ | По умолчанию | Назначение | Где используется |
|------|--------------|------------|------------------|
| `tariff-ttl-minutes` | `10` | TTL `Mono.cache()` для тарифов | `TariffDirectoryClient` |

---

## Учебные HTTP-вызовы

Готовые запросы — [`docs/shop-demo.http`](../docs/shop-demo.http) (идентификаторы `p-100`, `ord-500`, … в URL).

Паузы между «опоздавшими» подписчиками SSE задаёт сам клиент (второй запрос через 2–3 с).

---

## Прочее в `application.yml`

| Секция | Назначение |
|--------|------------|
| `spring.application.name` | Имя приложения в логах и метриках |
| `logging.pattern.console` | Формат строк лога |

---

## Связанные классы

| Класс | Роль |
|-------|------|
| `DemoProperties` | Типизированный доступ к `demo.*` |
| `DemoPropertiesConfig` | `@EnableConfigurationProperties` |
| `WebClientConfig` | общий `correlationIdFilter` |
| `ExternalApiClientRegistry` | выбор клиента по `ApiClientKind` |
| `ExternalSystemStubExchange` | учебная подмена HTTP в `WebClient` |
| `ShopProductController` и др. | HTTP API магазина (`/api/shop/...`) |
| `TariffDirectoryClient` | `service.tariff` — `Mono.cache()` |

Теория и ожидаемые логи: `docs/interview/Hot Publisher и Cold Publisher - примеры.md`.
