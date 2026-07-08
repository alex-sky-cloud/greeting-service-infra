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
| `server.port` | `${demo.application.port}` | Порт встроенного HTTP-сервера Spring Boot (заглушки и WebClient в одном процессе). |

**Где используется:** Spring Boot (Netty). Не дублируйте порт вручную — задайте `demo.application.port`.

---

## `demo.application`

| Ключ | По умолчанию | Назначение | Где используется |
|------|--------------|------------|------------------|
| `demo.application.port` | `8082` | Единый HTTP-порт модуля | `server.port`; `DemoProperties#stubBaseUrl()` → `WebClientConfig` |

---

## `demo.stub-api`

Базовый URL для `WebClient`, который вызывает локальные заглушки (`DemoStubController`).
Порт **не** задаётся отдельно — берётся из `demo.application.port`.

| Ключ | По умолчанию | Назначение | Где используется |
|------|--------------|------------|------------------|
| `demo.stub-api.scheme` | `http` | Схема URL | `WebClientConfig` (все именованные `WebClient`-бины) |
| `demo.stub-api.host` | `localhost` | Хост заглушек | `WebClientConfig` |

Итоговый URL: `http://localhost:8082` (при дефолтах).

---

## `demo.stub-timing`

Искусственные задержки заглушек, чтобы в логах было видно cold/hot-поведение.

| Ключ | По умолчанию | Назначение | Где используется |
|------|--------------|------------|------------------|
| `product-delay-ms` | `300` | Задержка ответа `GET /products/{id}` | `DemoStubController#getProduct` |
| `fraud-delay-ms` | `400` | Задержка `POST /fraud/check` | `DemoStubController#checkFraud` |
| `tariff-delay-ms` | `300` | Задержка `GET /tariffs` | `DemoStubController#getTariffs` |
| `status-element-delay-ms` | `700` | Пауза между событиями SSE статусов заказа | `DemoStubController#streamStatuses` |
| `status-step-seconds` | `1` | Шаг `createdAt` между статусами в потоке | `DemoStubController#streamStatuses` |
| `quote-interval-ms` | `500` | Интервал котировок в SSE | `DemoStubController#streamQuotes` |

---

## `demo.stub-data`

Тестовые данные, которые отдают заглушки.

| Ключ | По умолчанию | Назначение | Где используется |
|------|--------------|------------|------------------|
| `product-name-prefix` | `"Demo product "` | Префикс имени товара | `DemoStubController#getProduct` |
| `product-price` | `99.90` | Цена товара | `DemoStubController#getProduct` |
| `fraud-status` | `ALLOW` | Результат anti-fraud | `DemoStubController#checkFraud` |
| `fraud-reason` | `stub-approved` | Причина решения fraud | `DemoStubController#checkFraud` |
| `tariff-version` | `v1-local` | Версия тарифной таблицы | `DemoStubController#getTariffs` |
| `tariff-rows` | BY/PL/DE | Строки тарифов (`zone`, `price`) | `DemoStubController#getTariffs` |
| `order-statuses` | CREATED → SHIPPED | Последовательность статусов заказа | `DemoStubController#streamStatuses` |
| `quote-base-bid` | `1.1000` | Начальный bid | `DemoStubController#streamQuotes` |
| `quote-bid-step` | `0.0001` | Приращение bid на каждом тике | `DemoStubController#streamQuotes` |
| `quote-ask-spread` | `0.0002` | Спред ask относительно bid | `DemoStubController#streamQuotes` |
| `quote-max-events` | `20` | Число котировок в потоке | `DemoStubController#streamQuotes` |

---

## `demo.cache`

| Ключ | По умолчанию | Назначение | Где используется |
|------|--------------|------------|------------------|
| `tariff-ttl-minutes` | `10` | TTL `Mono.cache()` для тарифов | `TariffDirectoryClient` |

---

## `demo.runner`

Параметры `CommandLineRunner` (`DemoRunner`, profile `demo`): идентификаторы сценариев и паузы
`Thread.sleep`, чтобы успеть подписаться «поздним» подписчиком.

| Ключ | По умолчанию | Сценарий | Где используется |
|------|--------------|----------|------------------|
| `product-id` | `p-100` | Cold `Mono` — два `subscribe()` | `DemoRunner#coldMono` → `ProductWidgetFacade` |
| `fraud-order-id` | `ord-500` | `Mono.share()` | `DemoRunner#sharedMono` |
| `shared-flux-order-id` | `ord-700` | `Flux.share()` | `DemoRunner#sharedFlux` |
| `replay-flux-order-id` | `ord-701` | `replay(1)` | `DemoRunner#replayFlux` |
| `quote-symbol` | `EURUSD` | `publish().refCount(2)` | `DemoRunner#refCountFlux` |
| `cold-mono-wait-ms` | `1500` | Ожидание после cold mono | `DemoRunner#coldMono` |
| `shared-mono-wait-ms` | `1500` | Ожидание после shared mono | `DemoRunner#sharedMono` |
| `cached-mono-between-requests-ms` | `800` | Пауза между двумя подписчиками cache | `DemoRunner#cachedMono` |
| `cached-mono-wait-ms` | `1200` | Завершение сценария cache | `DemoRunner#cachedMono` |
| `flux-late-subscriber-delay-ms` | `2500` | Задержка «позднего» подписчика flux | `DemoRunner#sharedFlux`, `#replayFlux` |
| `flux-wait-ms` | `5000` | Завершение сценария flux | `DemoRunner#sharedFlux`, `#replayFlux` |
| `ref-count-second-subscriber-delay-ms` | `1500` | Задержка второго подписчика refCount | `DemoRunner#refCountFlux` |
| `ref-count-wait-ms` | `5000` | Завершение сценария refCount | `DemoRunner#refCountFlux` |

---

## Прочее в `application.yml`

| Секция | Назначение |
|--------|------------|
| `spring.application.name` | Имя приложения в логах и метриках |
| `spring.profiles.active: demo` | Включает `DemoRunner` при старте |
| `logging.pattern.console` | Формат строк лога |

---

## Связанные классы

| Класс | Роль |
|-------|------|
| `DemoProperties` | Типизированный доступ к `demo.*` |
| `DemoPropertiesConfig` | `@EnableConfigurationProperties` |
| `WebClientConfig` | `WebClient` → `stubBaseUrl()` |
| `DemoStubController` | HTTP-заглушки |
| `TariffDirectoryClient` | `Mono.cache()` |
| `DemoRunner` | Демонстрация сценариев при старте |

Теория и ожидаемые логи: `docs/interview/Hot Publisher и Cold Publisher - примеры.md`.
