# Spring WebClient: руководство и вопросы для собеседования

> Краткое руководство по **Spring WebClient** (реактивный HTTP-клиент) и типичным вопросам на Java-собеседованиях.  
> Формат каждого блока: **ответ простым языком → вопрос → источник → цитата (EN/RU)**.

**См. также:** [project-reactor-interview-guide.md](../interview/project-reactor-interview-guide.md) — Mono, Flux, flatMap, retry.

---

## Оглавление

1. [Что такое WebClient](#1-что-такое-webclient)
2. [WebClient vs RestTemplate vs RestClient](#2-webclient-vs-resttemplate-vs-restclient)
3. [Создание и настройка WebClient](#3-создание-и-настройка-webclient)
4. [GET-запрос и Mono](#4-get-запрос-и-mono)
5. [Стриминг ответа — Flux](#5-стриминг-ответа--flux)
6. [POST, PUT, DELETE с телом запроса](#6-post-put-delete-с-телом-запроса)
7. [Обработка HTTP-ошибок (4xx, 5xx)](#7-обработка-http-ошибок-4xx-5xx)
8. [Таймауты и retry](#8-таймауты-и-retry)
9. [Заголовки, фильтры, базовый URL](#9-заголовки-фильтры-базовый-url)
10. [Почему block() — антипаттерн](#10-почему-block--антипаттерн)
11. [Тестирование WebClient](#11-тестирование-webclient)
12. [WebClient в цепочке с R2DBC и WebFlux](#12-webclient-в-цепочке-с-r2dbc-и-webflux)

---

## Введение

**WebClient** — неблокирующий HTTP-клиент Spring (модуль Spring WebFlux). Запросы возвраща **`Mono<ResponseEntity<T>>`**, **`Mono<T>`** или **`Flux<T>`** (стриминг). Под капотом — Reactor Netty (по умолчанию), JDK HttpClient или Jetty Reactive.

Зависимость:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

---

## 1. Что такое WebClient

**Ответ:** WebClient — реактивная замена старому **RestTemplate**. Вы описываете HTTP-запрос fluent-цепочкой; результат — `Mono` или `Flux`. Пока на поток не подписались (или Spring WebFlux не подписался за вас), запрос не уходит. Подходит для высоконагруженных сервисов и композиции нескольких HTTP-вызовов через `flatMap`/`zip`.

**Вопрос:** *What is WebClient in Spring?*

**Источник:** [Spring Framework — WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)

> **EN:** «WebClient is a non-blocking, reactive client to perform HTTP requests. It exposes functional, fluent APIs and exposes reactive types Mono and Flux as composable building blocks.»

> **RU:** «WebClient — неблокирующий реактивный клиент для HTTP. Fluent API, результаты — Mono и Flux как composable-блоки.»

---

## 2. WebClient vs RestTemplate vs RestClient

**Ответ:**

| Клиент | Модель | Статус |
|--------|--------|--------|
| **RestTemplate** | Блокирующий | Legacy, в maintenance mode |
| **WebClient** | Reactive (`Mono`/`Flux`) | Для WebFlux и async-композиции |
| **RestClient** (Spring 6.1+) | Синхронный, fluent | Замена RestTemplate в **MVC** |

Для **Spring MVC + виртуальные потоки** часто берут RestClient; для **WebFlux** — WebClient. На собеседовании: «RestTemplate устарел, WebClient — reactive, RestClient — modern sync».

**Вопрос:** *What is the difference between WebClient and RestTemplate?*

**Источник:** [Spring Framework — WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)

> **EN:** «WebClient is the reactive alternative to RestTemplate. It supports synchronous and asynchronous operations as well as streaming.»

> **RU:** «WebClient — реактивная альтернатива RestTemplate с поддержкой sync, async и стриминга.»

**Источник (доп.):** [Spring Boot — REST Clients](https://docs.spring.io/spring-boot/docs/current/reference/html/io.html#io.rest-client)

> **EN:** «RestClient is the modern synchronous HTTP client. WebClient is the reactive alternative.»

> **RU:** «RestClient — современный синхронный клиент; WebClient — реактивная альтернатива.»

---

## 3. Создание и настройка WebClient

**Ответ:** Через **`WebClient.builder()`**: базовый URL, default headers, codecs, filters. В Spring Boot можно зарегистрировать `@Bean WebClient` или `@Bean WebClient.Builder` для `@LoadBalanced` (Eureka/Consul).

```java
@Bean
WebClient paymentWebClient(WebClient.Builder builder) {
    return builder
        .baseUrl("https://api.payment.example")
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();
}
```

**Вопрос:** *How do you create and configure a WebClient bean?*

**Источник:** [Spring Framework — WebClient Builder](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html#webflux-webclient-builder)

> **EN:** «You can create a WebClient with one of the static factory methods: create(), builder(), or create(String). The WebClient.Builder is the preferred way to configure and create a WebClient.»

> **RU:** «WebClient создают через create(), builder() или create(String). WebClient.Builder — предпочтительный способ настройки.»

---

## 4. GET-запрос и Mono

**Ответ:** Типичный паттерн — `get()` → `uri(...)` → `retrieve()` → `bodyToMono(Dto.class)`. `retrieve()` сразу бросает/мапит ошибки 4xx/5xx (если не настроено иначе). Для полного контроля над статусом — `exchangeToMono`.

```java
public Mono<UserDto> fetchUser(Long id) {
    return webClient.get()
        .uri("/users/{id}", id)
        .retrieve()
        .bodyToMono(UserDto.class);
}
```

**Вопрос:** *How do you perform a GET request with WebClient and deserialize JSON to Mono?*

**Источник:** [Spring Framework — Retrieve](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html#webflux-webclient-retrieve)

> **EN:** «The retrieve() method is the simplest way to get a response body and convert it to a Mono or Flux.»

> **RU:** «retrieve() — простейший способ получить тело ответа и преобразовать в Mono или Flux.»

---

## 5. Стриминг ответа — Flux

**Ответ:** Если сервер отдаёт **NDJSON**, SSE или chunked JSON-массив — используйте **`bodyToFlux(T.class)`**. Элементы приходят по мере чтения, без загрузки всего ответа в память. Backpressure передаётся по цепочке Reactor.

```java
return webClient.get()
    .uri("/events/stream")
    .accept(MediaType.APPLICATION_NDJSON)
    .retrieve()
    .bodyToFlux(Event.class);
```

**Вопрос:** *When would you use bodyToFlux instead of bodyToMono?*

**Источник:** [Spring Framework — WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)

> **EN:** «WebClient supports streaming through bodyToFlux for multi-valued responses.»

> **RU:** «WebClient поддерживает стриминг через bodyToFlux для многозначных ответов.»

---

## 6. POST, PUT, DELETE с телом запроса

**Ответ:** `post()` / `put()` / `delete()` → `contentType(APPLICATION_JSON)` → `bodyValue(dto)` или `body(Mono.just(dto), Dto.class)` → `retrieve()` → `bodyToMono(...)`. Для `Mono`-тела — второй вариант, когда payload ещё асинхронный.

```java
return webClient.post()
    .uri("/orders")
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(new CreateOrderRequest(productId, qty))
    .retrieve()
    .bodyToMono(OrderDto.class);
```

**Вопрос:** *How do you send a JSON POST request with WebClient?*

**Источник:** [Spring Framework — Request Body](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html#webflux-webclient-body)

> **EN:** «You can provide the request body through bodyValue(Object) for a plain object or body(Publisher, Class) for reactive types.»

> **RU:** «Тело запроса: bodyValue(Object) для обычного объекта или body(Publisher, Class) для реактивных типов.»

---

## 7. Обработка HTTP-ошибок (4xx, 5xx)

**Ответ:** С **`retrieve()`** по умолчанию 4xx/5xx → `WebClientResponseException`. Настраивают через **`onStatus`**: для 404 вернуть `Mono.empty()`, для 500 — свой exception.

```java
return webClient.get()
    .uri("/users/{id}", id)
    .retrieve()
    .onStatus(HttpStatusCode::is4xxClientError,
        resp -> Mono.error(new NotFoundException(id)))
    .onStatus(HttpStatusCode::is5xxServerError,
        resp -> Mono.error(new UpstreamException("Payment service down")))
    .bodyToMono(UserDto.class);
```

**Вопрос:** *How do you handle HTTP error status codes with WebClient?*

**Источник:** [Spring Framework — onStatus](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html#webflux-webclient-exchange)

> **EN:** «The retrieve() method provides built-in support for handling HTTP error status codes through onStatus handlers.»

> **RU:** «retrieve() поддерживает обработку HTTP-ошибок через обработчики onStatus.»

---

## 8. Таймауты и retry

**Ответ:** Таймаут — на уровне **Reactor** (`timeout(Duration)`) или **HttpClient** (Reactor Netty: `responseTimeout`). Retry — `retryWhen(Retry.backoff(3, Duration.ofMillis(500)))` только для **идемпотентных** GET; для POST без idempotency-key — осторожно.

```java
return webClient.get()
    .uri("/health")
    .retrieve()
    .bodyToMono(String.class)
    .timeout(Duration.ofSeconds(2))
    .retryWhen(Retry.backoff(3, Duration.ofMillis(200))
        .filter(ex -> ex instanceof WebClientRequestException));
```

**Вопрос:** *How do you configure timeouts and retries for WebClient calls?*

**Источник:** [Reactor Reference — retry](https://projectreactor.io/docs/core/release/reference/#error.handling)

> **EN:** «It works by re-subscribing to the upstream Flux. This is really a different sequence, and the original one is still terminated.»

> **RU:** «retry переподписывается на upstream — новая попытка, исходная цепочка уже завершена.»

---

## 9. Заголовки, фильтры, базовый URL

**Ответ:**

- **Заголовки:** `.header(...)`, `.headers(h -> h.setBearerAuth(token))`.
- **Фильтры:** `ExchangeFilterFunction` — логирование, correlation-id, OAuth refresh, метрики.
- **baseUrl:** относительные URI в `.uri("/path")`.

```java
WebClient client = WebClient.builder()
    .baseUrl("https://api.example")
    .filter(ExchangeFilterFunction.ofRequestProcessor(req ->
        ClientRequest.from(req).header("X-Request-Id", UUID.randomUUID().toString()).build()))
    .build();
```

**Вопрос:** *What are ExchangeFilterFunctions in WebClient?*

**Источник:** [Spring Framework — Filters](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html#webflux-webclient-filter)

> **EN:** «WebClient supports filters that can intercept and modify requests and responses. Filters are typically used for cross-cutting concerns such as logging or authentication.»

> **RU:** «WebClient поддерживает фильтры для перехвата и изменения запросов/ответов — логирование, аутентификация и т.д.»

---

## 10. Почему block() — антипаттерн

**Ответ:** `webClient.get()...block()` **блокирует** поток Netty event loop → падение throughput, возможны deadlock. В WebFlux-контроллере возвращайте `Mono`/`Flux` до конца. `block()` допустим в тестах, CLI, `@Scheduled` на `boundedElastic`, или на **границе** imperative/reactive кода.

**Вопрос:** *Why should you avoid block() when using WebClient in WebFlux?*

**Источник:** [Reactor — Schedulers](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html)

> **EN:** «The use of Reactor blocking APIs (block(), blockFirst(), blockLast()) inside the default single and parallel schedulers results in an IllegalStateException being thrown.»

> **RU:** «block() на reactive-scheduler'ах Netty/WebFlux недопустим — IllegalStateException или starvation event loop.»

---

## 11. Тестирование WebClient

**Ответ:**

1. **`WebTestClient`** — bind к mock MVC или router, проверка контроллера, который внутри вызывает WebClient (WebClient можно замокать `@MockBean`).
2. **`MockWebServer`** (OkHttp) — поднять fake HTTP-сервер, направить WebClient на `localhost:port`, проверить StepVerifier на `Mono`.
3. **`WireMock`** — stub внешних API.

```java
@Test
void fetchUser() {
    mockWebServer.enqueue(new MockResponse()
        .setBody("{\"id\":1,\"name\":\"Ann\"}")
        .addHeader("Content-Type", "application/json"));

    StepVerifier.create(client.fetchUser(1L))
        .expectNextMatches(u -> u.name().equals("Ann"))
        .verifyComplete();
}
```

**Вопрос:** *How do you test code that uses WebClient?*

**Источник:** [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html)

> **EN:** «WebTestClient is an HTTP client designed for testing server applications. It can connect to any server over HTTP or bind directly to WebFlux router functions.»

> **RU:** «WebTestClient — HTTP-клиент для тестов серверных приложений; подключается к серверу или bind к router.»

---

## 12. WebClient в цепочке с R2DBC и WebFlux

**Ответ:** Типичный BFF/сервис: загрузить id из БД (R2DBC `Mono`), затем **`flatMap`** → WebClient к другому API, **`zip`** несколько Monos параллельно, собрать DTO, вернуть из `@RestController` без block.

```java
@GetMapping("/orders/{id}/summary")
public Mono<OrderSummaryDto> summary(@PathVariable Long id) {
    return orderRepository.findById(id)
        .switchIfEmpty(Mono.error(new NotFoundException()))
        .flatMap(order ->
            Mono.zip(
                paymentClient.getPayment(order.paymentId()),
                shippingClient.getTracking(order.trackingId())
            ).map(tuple -> OrderSummaryDto.of(order, tuple.getT1(), tuple.getT2()))
        );
}
```

**Вопрос:** *How do you compose WebClient calls with reactive database access?*

**Источник:** [Reactor Reference — flatMap and zip](https://projectreactor.io/docs/core/release/reference/#which-operator)

> **EN:** «flatMap transforms elements asynchronously into inner Publishers and merges them. zip combines sources waiting for all to emit one element.»

> **RU:** «flatMap асинхронно разворачивает вложенные Publisher; zip ждёт по одному элементу от каждого источника и объединяет.»

---

## Полезные ссылки

| Ресурс | URL |
|--------|-----|
| Spring — WebClient | https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html |
| Spring Boot — REST Clients | https://docs.spring.io/spring-boot/docs/current/reference/html/io.html#io.rest-client |
| WebTestClient | https://docs.spring.io/spring-framework/reference/testing/webtestclient.html |
| Reactor — error/retry | https://projectreactor.io/docs/core/release/reference/#error.handling |
| R2DBC (БД в цепочке) | [project-reactor-r2dbc-guide.md](project-reactor-r2dbc-guide.md) |

---

*Документ для подготовки к собеседованиям. Источники — официальная документация Spring и Project Reactor (2024–2026).*
