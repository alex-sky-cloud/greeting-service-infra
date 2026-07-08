# Reactor: cold/hot, share/cache/replay/refCount + Spring WebClient examples

## Оглавление

- [1. Базовые понятия](#1-базовые-понятия)
- [2. Когда использовать что](#2-когда-использовать-что)
- [3. Структура примера](#3-структура-примера)
- [4. `@Configuration` и `WebClient`-бины](#4-configuration-и-webclient-бины)
- [5. Минимальные DTO](#5-минимальные-dto)
- [6. Клиенты и сервисы](#6-клиенты-и-сервисы)
- [7. `DemoRunner`](#7-demorunner)
- [8. Что должно быть видно в логах](#8-что-должно-быть-видно-в-логах)

## 1. Базовые понятия

**Cold publisher** — это источник, который заново выполняет работу для каждого `subscribe()`.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "They generate data anew for each subscription. If no subscription is created, data never gets generated."
>
> **Ru**: 
> "Они заново генерируют данные для каждой подписки. Если подписка не создана, данные вообще не генерируются."

Практический смысл для Spring WebFlux такой: если один и тот же `Mono` с HTTP-вызовом подписать два раза, то обычно будут выполнены два отдельных запроса.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "Think of an HTTP request: Each new subscriber triggers an HTTP call, but no call is made if no one is interested in the result."
>
> **Ru**: 
> "Представь HTTP-запрос: каждый новый подписчик запускает HTTP-вызов, а если результат никому не нужен, вызова вообще не будет."

**Hot publisher** — это источник, который не обязан пересоздаваться заново для каждого нового подписчика.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "Hot publishers, on the other hand, do not depend on any number of subscribers."
>
> **Ru**: 
> "Hot publishers, напротив, не зависят от количества подписчиков."

- Если подписчик подключился поздно к уже идущему **hot**-потоку, он обычно видит только новые элементы после своей подписки.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "...the subscriber would see only new elements emitted after it subscribed."
>
> **Ru**: 
>  "...подписчик увидит только новые элементы, которые были отправлены после того, как он подписался."

 - `share()` и `replay(...)` используются, чтобы превратить **cold**-источник в общий **hot**-поток.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "On the opposite, `share()` and `replay(…​)` can be used to turn a cold publisher into a hot one (at least once a first subscription has happened)."
>
> **Ru**: 
> "Напротив, `share()` и `replay(...)` можно использовать, чтобы превратить cold-источник в hot-источник (по крайней мере после первой подписки)."

 - Для `Flux` оператор `share()` по смыслу эквивалентен `publish().refCount()`.

**Источник:** https://stackoverflow.com/questions/56922389/why-project-reactors-mono-doesnt-have-a-share-operator

> "`share()` is equivalent to you calling `publish().refcount()` on your Flux."
>
> **Ru**: 
>  "`share()` эквивалентен вызову `publish().refCount()` на `Flux`."

`Mono.cache()` нужен в тех случаях, когда результат дорого получить, но потом его нужно быстро отдавать следующим подписчикам без нового вызова источника.

**Источник:** https://www.javacodegeeks.com/using-reactor-mono-cache-for-memoization-in-spring.html

> "The Mono.cache() operator in Project Reactor allows you to cache the result of a Mono and replay it to subsequent subscribers."
>
> **Ru**: 
> "Оператор `Mono.cache()` в Project Reactor позволяет закэшировать результат `Mono` и переигрывать его последующим подписчикам."

## 2. Когда использовать что

| Сценарий | Оператор | Смысл |
|---|---|---|
| Каждый подписчик должен запустить свою независимую операцию | cold `Mono` / cold `Flux` | Каждый `subscribe()` заново запускает источник |
| Несколько подписчиков должны разделить один текущий запуск | `share()` | Делится только текущий живой запуск, без истории |
| Нужно сохранить результат и отдать его поздним подписчикам | `cache()` | Следующие подписчики получают уже готовый результат |
| Позднему подписчику нужен последний статус и дальше live | `replay(1)` | Отдаёт последний сохранённый элемент и продолжает live-поток |
| Нельзя открывать дорогой stream, пока не соберётся нужное число потребителей | `publish().refCount(n)` | Upstream стартует только при достижении порога подписчиков |

## 3. Структура примера

Ниже собран единый **demo-набор** в стиле Spring Boot:

- `infra/WebClientConfig.java`
- `dto/*`
- `catalog/*`
- `fraud/*`
- `tariff/*`
- `status/*`
- `market/*`
- `DemoRunner.java`

Все примеры оформлены так, чтобы их можно было взять как основу для документации или demo-проекта: 
  - `WebClient`, 
  - `doOnSubscribe`, 
  - `doOnNext`, 
  - именованные бины, 
  - минимальные `record`-DTO и 
  - демонстрация через `CommandLineRunner`.

## 4. `@Configuration` и `WebClient`-бины

```java
package com.example.demo.infra;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public ExchangeFilterFunction correlationIdFilter() {
        return (request, next) -> next.exchange(
            ClientRequest.from(request)
                .header("X-Correlation-Id", UUID.randomUUID().toString())
                .build()
        );
    }

    @Bean
    @Qualifier("catalogWebClient")
    public WebClient catalogWebClient(WebClient.Builder builder,
                                      ExchangeFilterFunction correlationIdFilter) {
        return builder
            .baseUrl("http://catalog-service")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .filter(correlationIdFilter)
            .build();
    }

    @Bean
    @Qualifier("fraudWebClient")
    public WebClient fraudWebClient(WebClient.Builder builder,
                                    ExchangeFilterFunction correlationIdFilter) {
        return builder
            .baseUrl("http://fraud-service")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .filter(correlationIdFilter)
            .build();
    }

    @Bean
    @Qualifier("tariffWebClient")
    public WebClient tariffWebClient(WebClient.Builder builder,
                                     ExchangeFilterFunction correlationIdFilter) {
        return builder
            .baseUrl("http://tariff-service")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .filter(correlationIdFilter)
            .build();
    }

    @Bean
    @Qualifier("orderWebClient")
    public WebClient orderWebClient(WebClient.Builder builder,
                                    ExchangeFilterFunction correlationIdFilter) {
        return builder
            .baseUrl("http://order-service")
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
            .filter(correlationIdFilter)
            .build();
    }

    @Bean
    @Qualifier("marketWebClient")
    public WebClient marketWebClient(WebClient.Builder builder,
                                     ExchangeFilterFunction correlationIdFilter) {
        return builder
            .baseUrl("http://market-data-service")
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
            .filter(correlationIdFilter)
            .build();
    }
}
```

## 5. Минимальные DTO

```java
package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductDto(
    String id,
    String name,
    BigDecimal price
) {}

public record FraudCheckRequest(
    String orderId
) {}

public record FraudDecision(
    String orderId,
    String status,
    String reason
) {}

public record FraudResponseDto(
    String orderId,
    String status
) {}

public record TariffTable(
    String version,
    List<TariffRow> rows
) {}

public record TariffRow(
    String zone,
    BigDecimal price
) {}

public record OrderStatusEvent(
    String orderId,
    String status,
    Instant createdAt
) {}

public record QuoteEvent(
    String symbol,
    BigDecimal bid,
    BigDecimal ask,
    Instant timestamp
) {}
```

## 6. Клиенты и сервисы

### 6.1 `cold Mono`: каждый `subscribe()` делает новый HTTP-вызов

```java
package com.example.demo.catalog;

import com.example.demo.dto.ProductDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ProductCatalogClient {

    private final WebClient catalogWebClient;

    public ProductCatalogClient(@Qualifier("catalogWebClient") WebClient catalogWebClient) {
        this.catalogWebClient = catalogWebClient;
    }

    public Mono<ProductDto> getProduct(String productId) {
        return catalogWebClient.get()
            .uri("/products/{id}", productId)
            .retrieve()
            .bodyToMono(ProductDto.class)
            .doOnSubscribe(s -> log.info("catalog -> GET /products/{}", productId))
            .doOnNext(p -> log.info("catalog <- id={}, price={}", p.id(), p.price()))
            .doOnError(e -> log.error("catalog !! failed productId={}", productId, e));
    }
}
```

```java
package com.example.demo.catalog;

import com.example.demo.dto.ProductDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductWidgetFacade {

    private final ProductCatalogClient productCatalogClient;

    public void coldMonoDemo(String productId) {
        Mono<ProductDto> productMono = productCatalogClient.getProduct(productId);

        productMono.subscribe(p -> log.info("widget-1 <- {}", p));
        productMono.subscribe(p -> log.info("widget-2 <- {}", p));
    }
}
```

### 6.2 `Mono.share()`: один текущий anti-fraud вызов делится между текущими подписчиками

```java
package com.example.demo.fraud;

import com.example.demo.dto.FraudCheckRequest;
import com.example.demo.dto.FraudDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class FraudClient {

    private final WebClient fraudWebClient;

    public FraudClient(@Qualifier("fraudWebClient") WebClient fraudWebClient) {
        this.fraudWebClient = fraudWebClient;
    }

    public Mono<FraudDecision> check(String orderId) {
        return fraudWebClient.post()
            .uri("/fraud/check")
            .bodyValue(new FraudCheckRequest(orderId))
            .retrieve()
            .bodyToMono(FraudDecision.class)
            .doOnSubscribe(s -> log.info("fraud -> POST /fraud/check orderId={}", orderId))
            .doOnNext(d -> log.info("fraud <- orderId={}, status={}", d.orderId(), d.status()))
            .doOnError(e -> log.error("fraud !! failed orderId={}", orderId, e));
    }
}
```

```java
package com.example.demo.fraud;

import com.example.demo.dto.FraudDecision;
import com.example.demo.dto.FraudResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFraudOrchestrator {

    private final FraudClient fraudClient;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ResponseMapper responseMapper;

    public void processOrder(String orderId) {
        Mono<FraudDecision> sharedCheck =
            fraudClient.check(orderId)
                .share();

        sharedCheck.subscribe(d -> auditService.save(orderId, d));
        sharedCheck.subscribe(d -> metricsService.incrementFraudStatus(d.status()));
        sharedCheck.map(responseMapper::toDto)
            .subscribe(dto -> log.info("response <- {}", dto));
    }
}

@Slf4j
@Service
class AuditService {
    public void save(String orderId, FraudDecision decision) {
        log.info("audit <- orderId={}, status={}", orderId, decision.status());
    }
}

@Slf4j
@Service
class MetricsService {
    public void incrementFraudStatus(String status) {
        log.info("metrics <- fraud_status={}", status);
    }
}

@Component
class ResponseMapper {
    public FraudResponseDto toDto(FraudDecision decision) {
        return new FraudResponseDto(decision.orderId(), decision.status());
    }
}
```

### 6.3 `Mono.cache()`: тарифы сохраняются и отдаются поздним подписчикам

```java
package com.example.demo.tariff;

import com.example.demo.dto.TariffTable;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class TariffDirectoryClient {

    private final WebClient tariffWebClient;
    private final Mono<TariffTable> cachedTariffs;

    public TariffDirectoryClient(@Qualifier("tariffWebClient") WebClient tariffWebClient) {
        this.tariffWebClient = tariffWebClient;
        this.cachedTariffs = Mono.defer(this::loadTariffs)
            .cache(Duration.ofMinutes(10));
    }

    public Mono<TariffTable> getTariffs() {
        return cachedTariffs;
    }

    private Mono<TariffTable> loadTariffs() {
        return tariffWebClient.get()
            .uri("/tariffs")
            .retrieve()
            .bodyToMono(TariffTable.class)
            .doOnSubscribe(s -> log.info("tariff -> GET /tariffs"))
            .doOnNext(t -> log.info("tariff <- version={}", t.version()))
            .doOnError(e -> log.error("tariff !! failed", e));
    }
}
```

### 6.4 `Flux.share()`: поздний подписчик видит только live-хвост

```java
package com.example.demo.status;

import com.example.demo.dto.OrderStatusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class OrderStatusStreamClient {

    private final WebClient orderWebClient;

    public OrderStatusStreamClient(@Qualifier("orderWebClient") WebClient orderWebClient) {
        this.orderWebClient = orderWebClient;
    }

    public Flux<OrderStatusEvent> liveStatusesShared(String orderId) {
        return orderWebClient.get()
            .uri("/orders/{id}/statuses/stream", orderId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(OrderStatusEvent.class)
            .doOnSubscribe(s -> log.info("status -> OPEN /orders/{}/statuses/stream", orderId))
            .doOnNext(e -> log.info("status <- orderId={}, status={}", e.orderId(), e.status()))
            .doOnError(e -> log.error("status !! failed orderId={}", orderId, e))
            .share();
    }

    public Flux<OrderStatusEvent> liveStatusesReplayLast(String orderId) {
        return orderWebClient.get()
            .uri("/orders/{id}/statuses/stream", orderId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(OrderStatusEvent.class)
            .doOnSubscribe(s -> log.info("status(replay) -> OPEN /orders/{}/statuses/stream", orderId))
            .doOnNext(e -> log.info("status(replay) <- orderId={}, status={}", e.orderId(), e.status()))
            .replay(1)
            .autoConnect(1);
    }
}
```

### 6.5 `publish().refCount(2)`: дорогой market stream открывается только при двух подписчиках

```java
package com.example.demo.market;

import com.example.demo.dto.QuoteEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class MarketDataClient {

    private final WebClient marketWebClient;

    public MarketDataClient(@Qualifier("marketWebClient") WebClient marketWebClient) {
        this.marketWebClient = marketWebClient;
    }

    public Flux<QuoteEvent> sharedQuotes(String symbol) {
        return marketWebClient.get()
            .uri("/quotes/{symbol}/stream", symbol)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(QuoteEvent.class)
            .doOnSubscribe(s -> log.info("quotes -> OPEN /quotes/{}/stream", symbol))
            .doOnNext(q -> log.info("quotes <- symbol={}, bid={}, ask={}", q.symbol(), q.bid(), q.ask()))
            .doFinally(signal -> log.info("quotes xx CLOSE symbol={}, signal={}", symbol, signal))
            .publish()
            .refCount(2);
    }
}
```

## 7. `DemoRunner`

```java
package com.example.demo;

import com.example.demo.catalog.ProductWidgetFacade;
import com.example.demo.fraud.OrderFraudOrchestrator;
import com.example.demo.market.MarketDataClient;
import com.example.demo.status.OrderStatusStreamClient;
import com.example.demo.tariff.TariffDirectoryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoRunner implements CommandLineRunner {

    private final ProductWidgetFacade productWidgetFacade;
    private final OrderFraudOrchestrator orderFraudOrchestrator;
    private final TariffDirectoryClient tariffDirectoryClient;
    private final OrderStatusStreamClient orderStatusStreamClient;
    private final MarketDataClient marketDataClient;

    @Override
    public void run(String... args) throws Exception {
        coldMono();
        sharedMono();
        cachedMono();
        sharedFlux();
        replayFlux();
        refCountFlux();
    }

    private void coldMono() throws InterruptedException {
        log.info("=== cold mono ===");
        productWidgetFacade.coldMonoDemo("p-100");
        Thread.sleep(1500);
    }

    private void sharedMono() throws InterruptedException {
        log.info("=== shared mono ===");
        orderFraudOrchestrator.processOrder("ord-500");
        Thread.sleep(1500);
    }

    private void cachedMono() throws InterruptedException {
        log.info("=== cached mono ===");

        tariffDirectoryClient.getTariffs()
            .subscribe(t -> log.info("request-1 <- version={}", t.version()));

        Thread.sleep(800);

        tariffDirectoryClient.getTariffs()
            .subscribe(t -> log.info("request-2 <- version={}", t.version()));

        Thread.sleep(1200);
    }

    private void sharedFlux() throws InterruptedException {
        log.info("=== shared flux ===");

        var shared = orderStatusStreamClient.liveStatusesShared("ord-700");

        shared.subscribe(e -> log.info("audit <- {}", e.status()));

        Thread.sleep(2500);

        shared.subscribe(e -> log.info("ui-late <- {}", e.status()));

        Thread.sleep(5000);
    }

    private void replayFlux() throws InterruptedException {
        log.info("=== replay flux ===");

        var replayed = orderStatusStreamClient.liveStatusesReplayLast("ord-701");

        replayed.subscribe(e -> log.info("audit <- {}", e.status()));

        Thread.sleep(2500);

        replayed.subscribe(e -> log.info("ui-late <- {}", e.status()));

        Thread.sleep(5000);
    }

    private void refCountFlux() throws InterruptedException {
        log.info("=== refCount(2) flux ===");

        var quotes = marketDataClient.sharedQuotes("EURUSD");

        quotes.subscribe(q -> log.info("ui <- {}", q));

        Thread.sleep(1500);

        quotes.subscribe(q -> log.info("audit <- {}", q));

        Thread.sleep(5000);
    }
}
```

## 8. Что должно быть видно в логах

**Cold `Mono`**

Ожидание: строка `catalog -> GET /products/...` появится два раза, потому что каждый `subscribe()` запускает новый вызов.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "Think of an HTTP request: Each new subscriber triggers an HTTP call..."
>
> Ru: "Представь HTTP-запрос: каждый новый подписчик запускает HTTP-вызов..."

**`Mono.share()`**

Ожидание: строка `fraud -> POST /fraud/check ...` появится один раз, если подписчики подключились к одному и тому же текущему запуску.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "...`share()` ... can be used to turn a cold publisher into a hot one..."
>
> Ru: "...`share()` можно использовать, чтобы превратить cold-источник в hot-источник..."

**`Mono.cache()`**

Ожидание: `tariff -> GET /tariffs` выполнится один раз, а следующие подписчики получат уже сохранённый результат.

**Источник:** https://www.javacodegeeks.com/using-reactor-mono-cache-for-memoization-in-spring.html

> "The Mono.cache() operator in Project Reactor allows you to cache the result of a Mono and replay it to subsequent subscribers."
>
> Ru: "Оператор `Mono.cache()` в Project Reactor позволяет закэшировать результат `Mono` и переигрывать его последующим подписчикам."

**`Flux.share()`**

Ожидание: поздний подписчик увидит только ту часть потока статусов, которая ещё не прошла.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "...the subscriber would see only new elements emitted after it subscribed."
>
> Ru: "...подписчик увидит только новые элементы, которые были отправлены после того, как он подписался."

**`replay(1)`**

Ожидание: поздний подписчик сразу получит последний сохранённый статус, а потом продолжит получать live-события.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "On the opposite, `share()` and `replay(…​)` can be used to turn a cold publisher into a hot one..."
>
> Ru: "Напротив, `share()` и `replay(...)` можно использовать, чтобы превратить cold-источник в hot-источник..."

**`publish().refCount(2)`**

Ожидание: подключение к дорогому stream API откроется только после второго подписчика.

**Источник:** https://stackoverflow.com/questions/56922389/why-project-reactors-mono-doesnt-have-a-share-operator

> "`share()` is equivalent to you calling `publish().refcount()` on your Flux."
>
> Ru: "`share()` эквивалентен вызову `publish().refCount()` на `Flux`."
