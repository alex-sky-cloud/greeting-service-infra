# reactive-study — точки останова: путь HTTP-запроса

Компактный план отладки цепочки 

**клиент → Netty → Reactor Netty → WebFlux → R2DBC → ответ**.

**Основа**: [`docs/interview/reactive/13 - Путь HTTP-запроса в Netty, Reactor Netty и Spring WebFlux.md`](../../docs/interview/reactive/13%20-%20Путь%20HTTP-запроса%20в%20Netty,%20Reactor%20Netty%20и%20Spring%20WebFlux.md).

**Тестовый endpoint:** `GET http://localhost:8083/api/orders/first-10`  
(profile `local`, Postgres из `docker-reactive-study`).

---

## Подготовка IDE

1. **Attach sources** для зависимостей Gradle (`reactor-core`, `reactor-netty-http`, `netty-*`, `spring-webflux`).
2. **Breakpoint** только на **entry** методов — иначе слишком много срабатываний на **EventLoop**.
3. Запуск: **ReactiveStudyApplication (local)** или `./gradlew bootRun --args='--spring.profiles.active=local'`.
4. Запрос: `curl -v http://localhost:8083/api/orders/first-10`.

---

## Карта пути (8 остановок)

| # | Слой | JAR / модуль | Класс | Метод (ориентир) | Что увидеть |
|---|------|--------------|-------|------------------|-------------|
| 1 | **Netty EventLoop** | `netty-transport` | `io.netty.channel.nio.NioEventLoop` | `run()` → `processSelectedKeys()` | Selector сообщил: сокет готов к чтению |
| 2 | **Входящий pipeline** | `netty-codec-http` | `io.netty.handler.codec.http.HttpServerCodec` | `decode()` | Байты → `HttpRequest` + `HttpContent` |
| 3 | **Агрегация (если включена)** | `netty-codec-http` | `io.netty.handler.codec.http.HttpObjectAggregator` | `decode()` | Части → `FullHttpRequest` |
| 4 | **Reactor Netty** | `reactor-netty-http` | `reactor.netty.http.server.HttpTrafficHandler` | `channelRead()` | Граница Netty → Reactor |
| 5 | **Reactor Netty** | `reactor-netty-http` | `reactor.netty.http.server.HttpServerOperations` | `onInboundNext()` | URI, метод, заголовки; тело как `Flux<DataBuffer>` |
| 6 | **Spring WebFlux** | `spring-webflux` | `org.springframework.web.reactive.DispatcherHandler` | `handle()` | Маршрутизация на `@RestController` |
| 7 | **Ваш код** | `reactive-study` | `OrderController` | `first10()` | Возврат `Flux` без `subscribe()` |
| 7b | **Ваш код** | `reactive-study` | `OrderService` | `findFirst10()` | `map(OrderResponse::from)` |
| 7c | **Spring Data R2DBC** | `spring-data-r2dbc` | `SimpleR2dbcRepository` (decompile) | `findAll` / query method | SQL к PostgreSQL |
| 8 | **Исходящий pipeline** | `netty-codec-http` | `io.netty.handler.codec.http.HttpServerEncoder` | `encode()` | HTTP-ответ → байты в сокет |

 **#7** — три breakpoint в приложении: достаточно для цепочки бизнес-логики.

> **Reactor-операторы** (`map`, `flatMap`) — классы `reactor.core.publisher.FluxMap`, `FluxFlatMap` (срабатывают при подписке, не при объявлении цепочки).

---

## Порядок прохождения (один запрос)

```text
[1 EventLoop read]
  → [2 HttpServerCodec decode]
  → [3 HttpObjectAggregator?]
  → [4 HttpTrafficHandler]
  → [5 HttpServerOperations]
  → [6 DispatcherHandler → OrderController.first10]
  → [7b OrderService.findFirst10 → OrderRepository]
  → [R2DBC / PostgreSQL — неблокирующий I/O]
  → [Flux эмитит 10 OrderResponse]
  → [WebFlux сериализация JSON]
  → [8 HttpServerEncoder → сокет]
```

Подписка (`subscribe`) выполняется **инфраструктурой Spring** после возврата `Flux` из контроллера — поэтому breakpoint в `map()` сработает **после** `DispatcherHandler`, при **demand** от **downstream**.

---

## Project Reactor — где ставить

| Цель | Класс | Когда срабатывает |
|------|-------|-------------------|
| Подписка на цепочку | `reactor.core.publisher.Flux` | `subscribe()` / `subscribe(CoreSubscriber)` |
| `map` в сервисе | `reactor.core.publisher.FluxMap` | `onNext` каждого `Order` |
| Запрос к БД | `reactor.core.publisher.FluxUsingWhen` | R2DBC connection lifecycle |
| Backpressure | `reactor.core.publisher.Flux` | `request(n)` у `Subscription` |

---

## R2DBC / PostgreSQL

| Класс | JAR | Заметка |
|-------|-----|---------|
| `io.r2dbc.postgresql.PostgresqlConnection` | `r2dbc-postgresql` | отправка SQL |
| `org.springframework.r2dbc.core.DefaultDatabaseClient` | `spring-r2dbc` | выполнение запроса репозитория |

Лог SQL (profile `local`): `logging.level.io.r2dbc.postgresql.QUERY=DEBUG` уже в `application-local.yml`.

---

## Что не путать

| Netty ChannelHandler | Project Reactor operator |
|----------------------|--------------------------|
| `HttpServerCodec`, `HttpObjectAggregator` | `map`, `flatMap`, `filter` |
| Работа с байтами и HTTP-кадрами | Бизнес-логика и потоки `Mono`/`Flux` |
| Поток EventLoop | Может быть тот же поток, если нет `publishOn` |

---

## Минимальный сценарий (5 breakpoint)

Если времени мало — только эти:

1. `HttpTrafficHandler.channelRead` — вход в Reactor Netty  
2. `DispatcherHandler.handle` — вход в WebFlux  
3. `OrderController.first10` — ваш API  
4. `OrderService.findFirst10` — реактивная цепочка  
5. `HttpServerEncoder.encode` — ответ уходит клиенту  

---

## Связанные материалы

- Документ 13 (схема PlantUML): `docs/interview/reactive/13 - …WebFlux.md`
- Бизнес-данные: `src/main/resources/db/BUSINESS-CASE.md`
- Запуск БД: `src/main/resources/README.md`
