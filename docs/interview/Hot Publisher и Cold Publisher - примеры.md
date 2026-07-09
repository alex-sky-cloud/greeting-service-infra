
# Reactor: 
 - cold Publisher/hot Publisher, 
 - share/cache/replay/refCount
    - Spring WebClient examples

## Оглавление

- [1. Базовые понятия](#1-%D0%B1%D0%B0%D0%B7%D0%BE%D0%B2%D1%8B%D0%B5-%D0%BF%D0%BE%D0%BD%D1%8F%D1%82%D0%B8%D1%8F)
- [2. Когда использовать что](#2-%D0%BA%D0%BE%D0%B3%D0%B4%D0%B0-%D0%B8%D1%81%D0%BF%D0%BE%D0%BB%D1%8C%D0%B7%D0%BE%D0%B2%D0%B0%D1%82%D1%8C-%D1%87%D1%82%D0%BE)
- [3. Структура примера](#3-%D1%81%D1%82%D1%80%D1%83%D0%BA%D1%82%D1%83%D1%80%D0%B0-%D0%BF%D1%80%D0%B8%D0%BC%D0%B5%D1%80%D0%B0)
- [4. `WebClient` и registry stub-клиентов](#4-webclient-и-registry-stub-клиентов)
- [5. Минимальные model-типы](#5-минимальные-model-типы)
- [6. Клиенты и сервисы](#6-%D0%BA%D0%BB%D0%B8%D0%B5%D0%BD%D1%82%D1%8B-%D0%B8-%D1%81%D0%B5%D1%80%D0%B2%D0%B8%D1%81%D1%8B)
- [7. Учебные сценарии (HTTP-клиент)](#7-учебные-сценарии-http-клиент)
- [8. Что должно быть видно в логах](#8-%D1%87%D1%82%D0%BE-%D0%B4%D0%BE%D0%BB%D0%B6%D0%BD%D0%BE-%D0%B1%D1%8B%D1%82%D1%8C-%D0%B2%D0%B8%D0%B4%D0%BD%D0%BE-%D0%B2-%D0%BB%D0%BE%D0%B3%D0%B0%D1%85)


## 1. Базовые понятия

**Cold publisher** — это источник, который заново выполняет работу для каждого `subscribe()`.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "They generate data anew for each subscription. If no subscription is created, data never gets generated."

**Ru**:

> "Они заново генерируют данные для каждой подписки. Если подписка не создана, данные вообще не генерируются."

Практический смысл для Spring WebFlux такой: 
 - если один и тот же `Mono` с HTTP-вызовом подписать два раза, то обычно будут выполнены два отдельных запроса.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "Think of an HTTP request: Each new subscriber triggers an HTTP call, but no call is made if no one is interested in the result."

**Ru**:

> "Представь HTTP-запрос: каждый новый подписчик запускает HTTP-вызов, а если результат никому не нужен, вызова вообще не будет."

**Hot publisher** — это источник, который не обязан пересоздаваться заново для каждого нового подписчика.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "Hot publishers, on the other hand, do not depend on any number of subscribers."

**Ru**:

> "Hot publishers, напротив, не зависят от количества подписчиков."

- Если подписчик подключился поздно к уже идущему **hot**-потоку, он обычно видит только новые элементы после своей подписки.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "...the subscriber would see only new elements emitted after it subscribed."

Ru:

> "...подписчик увидит только новые элементы, которые были отправлены после того, как он подписался."

- `share()` и `replay(...)` используются, чтобы превратить **cold**-источник в общий **hot**-поток.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "On the opposite, `share()` and `replay(…​)` can be used to turn a cold publisher into a hot one (at least once a first subscription has happened)."

**Ru**:

> "Напротив, `share()` и `replay(...)` можно использовать, чтобы превратить cold-источник в hot-источник (по крайней мере после первой подписки)."

- Для `Flux` оператор `share()` по смыслу эквивалентен `publish().refCount()`.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "On the opposite, `share()` and `replay(…​)` can be used to turn a cold publisher into a hot one..."

**Ru**:

> "`share()` используют, чтобы сделать поток общим hot-потоком."

`Mono.cache()` нужен в тех случаях, когда результат дорого получить, но потом его нужно быстро отдавать следующим подписчикам без нового вызова источника.

**Источник:** https://projectreactor.io/docs/core/3.4.8/api/reactor/core/publisher/Mono.html

> "Turn this Mono into a hot source and cache last emitted signals for further Subscriber."

Ru:

> "Преобразует `Mono` в hot-источник и кэширует последний сигнал для следующих подписчиков."

## 2. Когда использовать что

| Сценарий | Оператор | Смысл |
| :-- | :-- | :-- |
| Каждый подписчик должен запустить свою независимую операцию | cold `Mono` / cold `Flux` | Каждый `subscribe()` заново запускает источник |
| Несколько подписчиков должны разделить один текущий запуск | `share()` | Делится только текущий живой запуск, без истории |
| Нужно сохранить результат и отдать его поздним подписчикам | `cache()` | Следующие подписчики получают уже готовый результат |
| Позднему подписчику нужен последний статус и дальше live | `replay(1)` | Отдаёт последний сохранённый элемент и продолжает live-поток |
| Нельзя открывать дорогой stream, пока не соберётся нужное число потребителей | `publish().refCount(n)` | Upstream стартует только при достижении порога подписчиков |

## 3. Структура примера

Модуль **`reactor-cold-hot-publisher`** (`com.example.coldhotpublisher`). Верхний уровень пакетов — **слой приложения**:

```
com.example.coldhotpublisher/
  controller/
    shop/              ShopProductController, ShopOrderController, …
  service/
    catalog/           ProductCatalog, ProductCatalogClient
    fraud/             OrderFraudOrchestrator
      checker/         FraudChecker, WebClientFraudChecker
      audit/           FraudAuditService, LoggingFraudAuditService
      metrics/         FraudMetricsService, LoggingFraudMetricsService
      response/        FraudResponseMapper, DefaultFraudResponseMapper
    tariff/            TariffDirectory, TariffDirectoryClient
    status/            OrderStatusStream, OrderStatusStreamClient
    market/            MarketDataStream, MarketDataClient
    demo/              (удалён — сценарии через HTTP)
  model/               record-DTO (ProductDto, FraudDecision, …)
  config/              DemoProperties, DemoPropertiesConfig
  infra/
    WebClientConfig
    webclient/         ExternalApiClient, ExternalApiClientRegistry, *ExternalApiClient
      stub/            ExternalSystemStubExchange, ExternalSystemStubResponses
```

Принципы:

- один top-level тип на файл;
- зависимости через **интерфейсы** (`ProductCatalog`, `FraudChecker`, …), не конкретные клиенты;
- выбор исходящего `WebClient` — **registry** по `ApiClientKind`, без `@Qualifier`
  (см. `docs/interview/Архитурный подход к выбору реализации в Spring без Qualifier, Primary, Profile.md`).

Порт по умолчанию **8082** (`demo.application.port`) — только API магазина (`/api/shop/...`).

Внешние системы в учебном стенде **не** публикуются как REST-контроллер: ответ подставляет `ExternalSystemStubExchange` внутри `WebClient`.

Все примеры оформлены так, чтобы их можно было взять как основу для документации или demo-проекта:

- `WebClient`,
- `doOnSubscribe`,
- `doOnNext`,
- `@ConfigurationProperties`,
- минимальные `record` в `model/`,
- демонстрация через HTTP-клиента ([`shop-demo.http`](../../reactor-cold-hot-publisher/docs/shop-demo.http)).

## 4. `WebClient`, registry и учебная подмена сети

Вместо пяти `@Qualifier`-бинов — **self-describing strategy + registry**.

`WebClientConfig` — только общая инфраструктура:

```java
package com.example.coldhotpublisher.infra;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

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
}
```

Контракт и одна из реализаций:

```java
package com.example.coldhotpublisher.infra.webclient;

import org.springframework.web.reactive.function.client.WebClient;

public interface ExternalApiClient {

    ApiClientKind getKind();

    WebClient webClient();
}
```

```java
package com.example.coldhotpublisher.infra.webclient;

import com.example.coldhotpublisher.config.DemoProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class CatalogExternalApiClient implements ExternalApiClient {

    private final WebClient webClient;

    public CatalogExternalApiClient(WebClient.Builder builder,
                                    ExternalSystemStubExchange stubExchange,
                                    ExchangeFilterFunction correlationIdFilter) {
        this.webClient = ExternalApiClientFactory.jsonClient(builder, stubExchange, correlationIdFilter);
    }

    @Override
    public ApiClientKind getKind() {
        return ApiClientKind.CATALOG;
    }

    @Override
    public WebClient webClient() {
        return webClient;
    }
}
```

Реестр собирается из `List<ExternalApiClient>` при старте:

```java
package com.example.coldhotpublisher.infra.webclient;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExternalApiClientConfiguration {

    @Bean
    public Map<ApiClientKind, ExternalApiClient> externalApiClientMap(List<ExternalApiClient> clients) {
        return clients.stream()
            .collect(Collectors.toUnmodifiableMap(ExternalApiClient::getKind, Function.identity()));
    }

    @Bean
    public ExternalApiClientRegistry externalApiClientRegistry(Map<ApiClientKind, ExternalApiClient> externalApiClientMap) {
        return new ExternalApiClientRegistry(externalApiClientMap);
    }
}
```

Сервисы получают клиент по доменному ключу:

```java
public ProductCatalogClient(ExternalApiClientRegistry externalApiClients) {
    this.catalogWebClient = externalApiClients.webClient(ApiClientKind.CATALOG);
}
```


## 5. Минимальные model-типы

Каждый `record` — отдельный файл в `model/`:

```java
package com.example.coldhotpublisher.model;

import java.math.BigDecimal;

public record ProductDto(
    String id,
    String name,
    BigDecimal price
) {}
```

```java
package com.example.coldhotpublisher.model;

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
```

```java
package com.example.coldhotpublisher.model;

import java.math.BigDecimal;
import java.util.List;

public record TariffTable(
    String version,
    List<TariffRow> rows
) {}

public record TariffRow(
    String zone,
    BigDecimal price
) {}
```

```java
package com.example.coldhotpublisher.model;

import java.math.BigDecimal;
import java.time.Instant;

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

### 6.1 `cold Mono`: каждый HTTP-запрос клиента — новый вызов `WebClient`

Учебная подмена внешнего каталога — `ExternalSystemStubExchange` (не REST-контроллер):

```java
package com.example.coldhotpublisher.infra.webclient.stub;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

@Component
public class ExternalSystemStubExchange implements ExchangeFunction {

  @Override
  public Mono<ClientResponse> exchange(ClientRequest request) {
    // GET /products/{id} → ExternalSystemStubResponses#product
    // … fraud, tariffs, SSE streams
  }
}
```

Вход для покупателя — `ShopProductController` (`GET /api/shop/products/{id}`).

```java
package com.example.coldhotpublisher.service.catalog;

import com.example.coldhotpublisher.model.ProductDto;
import com.example.coldhotpublisher.infra.webclient.ApiClientKind;
import com.example.coldhotpublisher.infra.webclient.ExternalApiClientRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public interface ProductCatalog {
    Mono<ProductDto> getProduct(String productId);
}

@Service
public class ProductCatalogClient implements ProductCatalog {

    private final WebClient catalogWebClient;

    public ProductCatalogClient(ExternalApiClientRegistry externalApiClients) {
        this.catalogWebClient = externalApiClients.webClient(ApiClientKind.CATALOG);
    }

    @Override
    public Mono<ProductDto> getProduct(String productId) {
        return catalogWebClient.get()
            .uri("/products/{id}", productId)
            .retrieve()
            .bodyToMono(ProductDto.class)
            .doOnSubscribe(s -> log.info("catalog -> GET /products/{}", productId))
            .doOnNext(p -> log.info("catalog <- id={}, price={}", p.id(), p.price()));
    }
}
```

```java
package com.example.coldhotpublisher.service.catalog;

import com.example.coldhotpublisher.model.ProductDto;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ProductWidgetFacade {

    private final ProductCatalog productCatalog;

    public void coldMonoDemo(String productId) {
        Mono<ProductDto> productMono = productCatalog.getProduct(productId);

        productMono.subscribe(p -> log.info("widget-1 <- {}", p));
        productMono.subscribe(p -> log.info("widget-2 <- {}", p));
    }
}
```


### 6.2 `Mono.share()`: один текущий anti-fraud вызов делится между текущими подписчиками

Каждая роль — отдельный класс в своём подпакете `service.fraud.*`:

```java
package com.example.coldhotpublisher.service.fraud.checker;

import com.example.coldhotpublisher.model.FraudCheckRequest;
import com.example.coldhotpublisher.model.FraudDecision;
import com.example.coldhotpublisher.infra.webclient.ApiClientKind;
import com.example.coldhotpublisher.infra.webclient.ExternalApiClientRegistry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

public interface FraudChecker {
    Mono<FraudDecision> check(String orderId);
}

@Service
public class WebClientFraudChecker implements FraudChecker {

    private final WebClient fraudWebClient;

    public WebClientFraudChecker(ExternalApiClientRegistry externalApiClients) {
        this.fraudWebClient = externalApiClients.webClient(ApiClientKind.FRAUD);
    }

    @Override
    public Mono<FraudDecision> check(String orderId) {
        return fraudWebClient.post()
            .uri("/fraud/check")
            .bodyValue(new FraudCheckRequest(orderId))
            .retrieve()
            .bodyToMono(FraudDecision.class)
            .doOnSubscribe(s -> log.info("fraud -> POST /fraud/check orderId={}", orderId));
    }
}
```

```java
package com.example.coldhotpublisher.service.fraud.audit;

public interface FraudAuditService {
    void save(String orderId, FraudDecision decision);
}

@Service
public class LoggingFraudAuditService implements FraudAuditService { /* log */ }
```

```java
package com.example.coldhotpublisher.service.fraud.metrics;

public interface FraudMetricsService {
    void incrementFraudStatus(String status);
}

@Service
public class LoggingFraudMetricsService implements FraudMetricsService { /* log */ }
```

```java
package com.example.coldhotpublisher.service.fraud.response;

public interface FraudResponseMapper {
    FraudResponseDto toDto(FraudDecision decision);
}

@Component
public class DefaultFraudResponseMapper implements FraudResponseMapper { /* map */ }
```

Оркестратор зависит только от интерфейсов:

```java
package com.example.coldhotpublisher.service.fraud;

import com.example.coldhotpublisher.service.fraud.audit.FraudAuditService;
import com.example.coldhotpublisher.service.fraud.checker.FraudChecker;
import com.example.coldhotpublisher.service.fraud.metrics.FraudMetricsService;
import com.example.coldhotpublisher.service.fraud.response.FraudResponseMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class OrderFraudOrchestrator {

    private final FraudChecker fraudChecker;
    private final FraudAuditService fraudAuditService;
    private final FraudMetricsService fraudMetricsService;
    private final FraudResponseMapper fraudResponseMapper;

    public void processOrder(String orderId) {
        Mono<FraudDecision> sharedCheck = fraudChecker.check(orderId).share();

        sharedCheck.subscribe(d -> fraudAuditService.save(orderId, d));
        sharedCheck.subscribe(d -> fraudMetricsService.incrementFraudStatus(d.status()));
        sharedCheck.map(fraudResponseMapper::toDto)
            .subscribe(dto -> log.info("response <- {}", dto));
    }
}
```


### 6.3 `Mono.cache()`: тарифы сохраняются и отдаются поздним подписчикам

```java
package com.example.coldhotpublisher.service.tariff;

import com.example.coldhotpublisher.config.DemoProperties;
import com.example.coldhotpublisher.model.TariffTable;
import com.example.coldhotpublisher.infra.webclient.ApiClientKind;
import com.example.coldhotpublisher.infra.webclient.ExternalApiClientRegistry;
import java.time.Duration;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

public interface TariffDirectory {
    Mono<TariffTable> getTariffs();
}

@Service
public class TariffDirectoryClient implements TariffDirectory {

    private final WebClient tariffWebClient;
    private final Mono<TariffTable> cachedTariffs;

    public TariffDirectoryClient(ExternalApiClientRegistry externalApiClients,
                                 DemoProperties demoProperties) {
        this.tariffWebClient = externalApiClients.webClient(ApiClientKind.TARIFF);
        this.cachedTariffs = Mono.defer(this::loadTariffs)
            .cache(Duration.ofMinutes(demoProperties.getCache().getTariffTtlMinutes()));
    }

    @Override
    public Mono<TariffTable> getTariffs() {
        return cachedTariffs;
    }

    private Mono<TariffTable> loadTariffs() {
        return tariffWebClient.get()
            .uri("/tariffs")
            .retrieve()
            .bodyToMono(TariffTable.class)
            .doOnSubscribe(s -> log.info("tariff -> GET /tariffs"));
    }
}
```


### 6.4 `Flux.share()`: поздний подписчик видит только live-хвост

```java
package com.example.coldhotpublisher.service.status;

import com.example.coldhotpublisher.model.OrderStatusEvent;
import com.example.coldhotpublisher.infra.webclient.ApiClientKind;
import com.example.coldhotpublisher.infra.webclient.ExternalApiClientRegistry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

public interface OrderStatusStream {
    Flux<OrderStatusEvent> liveStatusesShared(String orderId);
    Flux<OrderStatusEvent> liveStatusesReplayLast(String orderId);
}

@Service
public class OrderStatusStreamClient implements OrderStatusStream {

    private final WebClient orderWebClient;

    public OrderStatusStreamClient(ExternalApiClientRegistry externalApiClients) {
        this.orderWebClient = externalApiClients.webClient(ApiClientKind.ORDER_STATUS);
    }

    @Override
    public Flux<OrderStatusEvent> liveStatusesShared(String orderId) {
        return orderWebClient.get()
            .uri("/orders/{id}/statuses/stream", orderId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(OrderStatusEvent.class)
            .doOnSubscribe(s -> log.info("status -> OPEN /orders/{}/statuses/stream", orderId))
            .share();
    }

    @Override
    public Flux<OrderStatusEvent> liveStatusesReplayLast(String orderId) {
        return orderWebClient.get()
            .uri("/orders/{id}/statuses/stream", orderId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(OrderStatusEvent.class)
            .replay(1)
            .autoConnect(1);
    }
}
```


### 6.5 `publish().refCount(2)`: дорогой market stream открывается только при двух подписчиках

```java
package com.example.coldhotpublisher.service.market;

import com.example.coldhotpublisher.model.QuoteEvent;
import com.example.coldhotpublisher.infra.webclient.ApiClientKind;
import com.example.coldhotpublisher.infra.webclient.ExternalApiClientRegistry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

public interface MarketDataStream {
    Flux<QuoteEvent> sharedQuotes(String symbol);
}

@Service
public class MarketDataClient implements MarketDataStream {

    private final WebClient marketWebClient;

    public MarketDataClient(ExternalApiClientRegistry externalApiClients) {
        this.marketWebClient = externalApiClients.webClient(ApiClientKind.MARKET);
    }

    @Override
    public Flux<QuoteEvent> sharedQuotes(String symbol) {
        return marketWebClient.get()
            .uri("/quotes/{symbol}/stream", symbol)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(QuoteEvent.class)
            .doOnSubscribe(s -> log.info("quotes -> OPEN /quotes/{}/stream", symbol))
            .doFinally(signal -> log.info("quotes xx CLOSE symbol={}, signal={}", symbol, signal))
            .publish()
            .refCount(2);
    }
}
```


## 7. Учебные сценарии (HTTP-клиент)

Сценарии **не запускаются при старте** приложения. После `./gradlew bootRun` вызывайте API магазина из `reactor-cold-hot-publisher/docs/shop-demo.http` (или curl).

| Вызов | Что демонстрирует |
|-------|-------------------|
| `GET /api/shop/products/p-100` ×2 | Cold `Mono` — два похода в каталог |
| `POST /api/shop/orders/ord-500/process` | `Mono.share()` — одна проверка fraud |
| `GET /api/shop/tariffs` ×2 | `Mono.cache()` — второй ответ из кэша |
| `GET .../statuses/stream?mode=shared` ×2 | `Flux.share()` — опоздавший без прошлого |
| `GET .../statuses/stream?mode=replay` ×2 | `replay(1)` — сразу последний статус |
| `GET /api/shop/quotes/EURUSD/stream` ×2 | `refCount(2)` — поток после двух подписчиков |

`ExternalSystemStubExchange` подставляет ответы на виртуальные URI (`/products`, `/fraud/check/{orderId}`, …) внутри `WebClient`. Публичных endpoint'ов внешних систем нет.


## 8. Что должно быть видно в логах

**Cold `Mono`**

Ожидание: строка `catalog -> GET /products/...` появится два раза, потому что каждый `subscribe()` запускает новый вызов.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "Think of an HTTP request: Each new subscriber triggers an HTTP call..."

Ru:

> "Представь HTTP-запрос: каждый новый подписчик запускает HTTP-вызов..."

**`Mono.share()`**

Ожидание: строка `fraud -> POST /fraud/check ...` появится один раз, если подписчики подключились к одному и тому же текущему запуску.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "...`share()` and `replay(…​)` can be used to turn a cold publisher into a hot one..."

Ru:

> "`share()` и `replay(...)` можно использовать, чтобы превратить cold-источник в hot-источник."

**`Mono.cache()`**

Ожидание: `tariff -> GET /tariffs` выполнится один раз, а следующие подписчики получат уже сохранённый результат.

**Источник:** https://projectreactor.io/docs/core/3.4.8/api/reactor/core/publisher/Mono.html

> "Turn this Mono into a hot source and cache last emitted signals for further Subscriber."

Ru:

> "Преобразует `Mono` в hot-источник и кэширует последний сигнал для следующих подписчиков."

**`Flux.share()`**

Ожидание: поздний подписчик увидит только ту часть потока статусов, которая ещё не прошла.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/reactor-hotCold.html

> "...the subscriber would see only new elements emitted after it subscribed."

Ru:

> "...подписчик увидит только новые элементы, которые были отправлены после того, как он подписался."

**`replay(1)`**

Ожидание: поздний подписчик сразу получит последний сохранённый статус, а потом продолжит получать live-события.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/advanced-broadcast-multiple-subscribers-connectableflux.html

> "replay buffers data seen through the first subscription, up to configurable limits (in time and buffer size), and replays it to subsequent subscribers."

Ru:

> "`replay` буферизует данные и затем повторно отдаёт их последующим подписчикам."

**`publish().refCount(2)`**

Ожидание: подключение к дорогому stream API откроется только после второго подписчика.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/advanced-broadcast-multiple-subscribers-connectableflux.html

> "`refCount(n)` not only automatically tracks incoming subscriptions but also detects when these subscriptions are cancelled."

Ru:

> "`refCount(n)` автоматически отслеживает входящие подписки и их отмену."


