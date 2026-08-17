# Путь HTTP-запроса — проверенные точки останова

**Сценарий:** `GET http://127.0.0.1:8083/api/orders/first-10`  
**Модуль:** `reactive-study` — Spring Boot 4.0.5, Reactor Netty 1.3.4, Netty 4.2.12.Final.  
**Проверка:** 07.08.2026. Один запуск с `InitPathAgent`, один успешный HTTP-ответ `200 OK` (10 заказов).

Этот документ начинается **после уже поднятого сервера**. 
   - Создание transport, 
   - bind порта и 
   - EventLoopGroup 
 - здесь намеренно н**е рассматриваются** — это отдельная тема [Block 0](BLOCK-0-INIT-PATH-VERIFICATION.md).

---

## Оглавление

- [Что подтверждено](#что-подтверждено)
- [Короткая карта запроса](#короткая-карта-запроса)
- [Точки останова по порядку](#точки-останова-по-порядку)
- [Pipeline HTTP/1.1](#pipeline-http11)
- [Граница доказательства](#граница-доказательства)
- [Повторная проверка](#повторная-проверка)

---

## Что подтверждено

Trace: `docs/block0-verify/agent/http-request-trace.log`.

| Участок | Статус | Основание |
|---|---|---|
| Новое TCP-соединение | ✅ runtime | `ServerTransport$Acceptor#channelRead` |
| Worker event loop | ✅ runtime | `NioIoHandler.run` → `SingleThreadIoEventLoop.run` |
| HTTP в Reactor Netty | ✅ runtime | `HttpTrafficHandler#channelRead` |
| HTTP server operation | ✅ runtime | `HttpServerOperations#onInboundNext` |
| WebFlux dispatcher | ✅ runtime | `DispatcherHandler#handle` |
| Адаптер аннотационных контроллеров | ✅ runtime | `RequestMappingHandlerAdapter#handle` |
| Код приложения | ✅ runtime | `OrderController#first10` → `OrderService#findFirst10` |
| `ReactorHttpHandlerAdapter#apply` | △ API/исходник | agent не смог инструментировать класс в fat JAR |
| `HttpRequestDecoder#decode` | △ API/стек | codec виден в runtime stack, метод не был в фильтре |
| R2DBC query / SQL | ❌ не трассировалось | proxy и драйвер не были в фильтре |
| `HttpResponseEncoder#encode` | △ API | не был в фильтре agent |

`✅` означает вход в метод в этом конкретном **trace**, а не только существование метода в API.

---

## Короткая карта запроса

```text
curl
  → ServerTransport.Acceptor.channelRead            [runtime ✅]
  → NioIoHandler.run / SingleThreadIoEventLoop.run  [runtime ✅]
  → HttpServerCodec: bytes → HttpRequest            [API + stack]
  → HttpTrafficHandler.channelRead                  [runtime ✅]
  → HttpServerOperations.onInboundNext              [runtime ✅]
  → DispatcherHandler.handle                        [runtime ✅]
  → RequestMappingHandlerAdapter.handle             [runtime ✅]
  → OrderController.first10                         [runtime ✅]
  → OrderService.findFirst10                        [runtime ✅]
  → OrderRepository proxy → R2DBC                   [не трассировалось]
  → HttpResponseEncoder.encode                      [API; не трассировалось]
```

---

## Точки останова по порядку

### 1. TCP accept

```text
reactor.netty.transport.ServerTransport$Acceptor#channelRead
```

Это предпочитаемая точка при новом TCP-соединении. 

- В **trace** под ней видны:

```text
io.netty.channel.nio.AbstractNioMessageChannel$NioMessageUnsafe.read
io.netty.channel.nio.NioIoHandler.run
io.netty.channel.SingleThreadIoEventLoop.run
```

Значит для Netty 4.2 в этом приложении не нужно искать `ServerBootstrapAcceptor` или старый `NioEventLoop.run()` как обязательный runtime-путь.

### 2. Codec: байты → HTTP request

```text
io.netty.handler.codec.http.HttpRequestDecoder#decode
```

`HttpServerCodec` объединяет inbound `HttpRequestDecoder` и outbound `HttpResponseEncoder`.

**Источник:** https://netty.io/4.2/api/io/netty/handler/codec/http/HttpServerCodec.html

**Цитата:**
> A combination of `HttpRequestDecoder` and `HttpResponseEncoder` which enables easier server side HTTP implementation.

**Перевод:**
> Комбинация `HttpRequestDecoder` и `HttpResponseEncoder`, упрощающая серверную HTTP-реализацию.

### 3. Главная точка Reactor Netty

```text
reactor.netty.http.server.HttpTrafficHandler#channelRead
```

При `msg instanceof HttpRequest` handler создаёт `HttpServerOperations`, вызывает `ops.bind()` и передаёт сообщение дальше. В runtime trace этот метод сработал.

**Источник:** https://github.com/reactor/reactor-netty/blob/v1.3.4/reactor-netty-http/src/main/java/reactor/netty/http/server/HttpTrafficHandler.java

**Цитата:**
> if (msg instanceof HttpRequest) {
>
>     ...
>
>     ops = new HttpServerOperations(...);
>
>     ops.bind();
>
>     ...
>
>     ctx.fireChannelRead(msg);

**Перевод:**
> Для входящего `HttpRequest` Reactor Netty создаёт `HttpServerOperations`, привязывает его к channel и передаёт сообщение следующему handler.

### 4. HTTP operation → WebFlux

```text
reactor.netty.http.server.HttpServerOperations#onInboundNext
```

Метод protected, поэтому как обычную точку останова используйте `HttpTrafficHandler#channelRead`. Trace показывает переход:

```text
HttpServerOperations.onInboundNext
  → HttpServerOperations.handleDefaultHttpRequest
  → HttpServer$HttpServerHandle.onStateChange
  → DispatcherHandler.handle
```

### 5. Мост в Spring

```text
org.springframework.http.server.reactive.ReactorHttpHandlerAdapter#apply
```

Это правильный FQCN. JAR — `spring-web-7.0.6.jar`, не `spring-boot-reactor-netty`.

**Источник:** https://docs.spring.io/spring-framework/docs/7.0.6/javadoc-api/org/springframework/http/server/reactive/ReactorHttpHandlerAdapter.html

**Цитата:**
> Adapt `HttpHandler` to the Reactor Netty channel handling function.

**Перевод:**
> Адаптирует `HttpHandler` к функции обработки канала Reactor Netty.

В этом прогоне agent не смог инструментировать класс, поэтому это API-подтверждённая точка, но не runtime ✅.

### 6. WebFlux → контроллер

**Кратко по ролям**:

- `DispatcherHandler#handle`  
  (класс: `org.springframework.web.reactive.DispatcherHandler`) — центральная
  точка входа WebFlux. Получает HTTP-запрос, через `HandlerMapping` находит
  подходящий handler/контроллер и выбирает `HandlerAdapter`, который умеет его
  вызвать.

- `RequestMappingHandlerAdapter#handle`  
  (класс:
  `org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter`)
  — адаптер для методов с `@RequestMapping`, `@GetMapping` и подобными
  аннотациями. Он подготавливает аргументы метода контроллера и вызывает
  найденный endpoint-метод.

- `OrderController#first10`  
  (класс: `com.example.reactivestudy.controller.OrderController`) — endpoint
  вашего приложения: принимает запрос `GET /api/orders/first-10` и передаёт
  работу сервисному слою.

- `OrderService#findFirst10`  
  (класс: `com.example.reactivestudy.service.OrderService`) — сервисный метод
  приложения: содержит прикладной сценарий получения первых десяти заказов и
  вызывает репозиторий.


В одной цепочке это выглядит так:
- `DispatcherHandler` **находит, кому отдать запрос** → 
- `RequestMappingHandlerAdapter` **вызывает нужный метод контроллера** → 
- `OrderController` **принимает HTTP-вызов** → 
- `OrderService` **делает прикладную работу**. 
- `DispatcherHandler` обнаруживает `HandlerMapping`, `HandlerAdapter` и `HandlerResultHandler` в Spring-контексте; 
- `RequestMappingHandlerAdapter` прямо предназначен для вызова методов с `@RequestMapping`.

   - https://docs.spring.io/spring-framework/docs/7.0.6/javadoc-api/org/springframework/web/reactive/DispatcherHandler.html

   - https://docs.spring.io/spring-framework/docs/7.0.6/javadoc-api/org/springframework/web/reactive/result/method/annotation/RequestMappingHandlerAdapter.html




**Источник:** https://docs.spring.io/spring-framework/docs/7.0.6/javadoc-api/org/springframework/web/reactive/DispatcherHandler.html

**Цитата:**
> Central dispatcher for HTTP request handlers/controllers.

**Перевод:**
> Центральный диспетчер обработчиков HTTP-запросов и контроллеров.

### 7. R2DBC

 Описанный ниже участок кода, выполняется **при инициализации приложения**, когда Spring создаёт один раз
**repository proxy**, а не при каждом HTTP-запросе.

- Для метода репозитория `findTop10ByOrderByIdAsc()` Spring Data создаёт объект:

```text
org.springframework.data.r2dbc.repository.query.PartTreeR2dbcQuery
```

`PartTreeR2dbcQuery`  
- (пакет: `org.springframework.data.r2dbc.repository.query`) — объект,
представляющий **derived query**: 
  - запрос, который Spring Data выводит из имени
метода репозитория.

**Создание выполняет:**

```text
org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory
  $R2dbcQueryLookupStrategy#resolveQuery
```

Логика `resolveQuery(...)`:

```text
есть named query или @Query
  → StringBasedR2dbcQuery

нет named query и нет @Query
  → PartTreeR2dbcQuery
```

- Для `findTop10ByOrderByIdAsc()` создаётся `PartTreeR2dbcQuery`, потому что
метод не содержит `@Query` и для него не найден **named query.**

В конструкторе `PartTreeR2dbcQuery` Spring:

```text
new PartTree(methodName, domainType)
  → R2dbcQueryCreator.validate(tree, parameters)
```

- `PartTree` разбирает имя `findTop10ByOrderByIdAsc`:
  `find` — чтение, `Top10` — лимит 10, `OrderByIdAsc` — сортировка по `id`
  по возрастанию.
- `R2dbcQueryCreator.validate(...)` проверяет, что разобранные части имени
  метода и его параметры допустимы для R2DBC derived query.

 - Если имя метода некорректно, приложение **падает** именно на startup **с ошибкой**
создания **query**, а не при первом HTTP-запросе.

Созданный `RepositoryQuery` сохраняется в map:

```text
org.springframework.data.repository.core.support
  .QueryExecutorMethodInterceptor#queries
```

`QueryExecutorMethodInterceptor`  
(пакет: `org.springframework.data.repository.core.support`) — interceptor
repository proxy. 
  - Он сопоставляет метод интерфейса репозитория с заранее
созданным объектом `RepositoryQuery` и при runtime-вызове направляет вызов
в этот query.

- При HTTP-запросе, `OrderService` вызывает метод repository proxy. 
- Proxy берёт заранее созданный `RepositoryQuery` из `QueryExecutorMethodInterceptor#queries`
и вызывает его `execute(...)`. 
 - В вашем runtime breakpoint это зафиксировано как `AbstractR2dbcQuery#execute`.

---

Итоговая модель простая:

```text

Startup:
OrderRepository method
  → R2dbcRepositoryFactory...resolveQuery
  → new PartTreeR2dbcQuery(...)
  → PartTree + validate
  → QueryExecutorMethodInterceptor.queries[method] = RepositoryQuery

HTTP request:
OrderService
  → repository proxy
  → QueryExecutorMethodInterceptor
  → заранее созданный RepositoryQuery
  → AbstractR2dbcQuery.execute(...)
  → executeQuery(...)
  → R2DBC execution
```

 - https://docs.spring.io/spring-data/commons/reference/repositories/query-methods-details.html 
---


- То есть теперь `PartTreeR2dbcQuery` - создаётся и валидируется при подготовке репозитория. 
- `AbstractR2dbcQuery#execute` — это уже runtime-выполнение подготовленного query-объекта, которое вы реально подтвердили breakpoint’ом. 
  - По умолчанию Spring Data сначала ищет объявленный запрос, а если его нет — строит store-specific query по имени метода; эта стратегия выполняется на bootstrap.


---

Подтверждённая точка входа Spring Data R2DBC:

```text
org.springframework.data.r2dbc.repository.query.AbstractR2dbcQuery#execute
```

`AbstractR2dbcQuery`
(пакет: `org.springframework.data.r2dbc.repository.query`) — базовый класс
выполнения **repository query** в Spring Data R2DBC.

- **Метод** `execute(Object[] parameters)`:

1. получает аргументы метода репозитория;
2. создаёт `R2dbcParameterAccessor`;
3. вызывает `createQuery(...)`, получая `PreparedOperation<?>`;
4. передаёт подготовленную операцию в `executeQuery(...)`;
5. возвращает реактивный `Publisher`.

Подтверждённый переход:

```text
AbstractR2dbcQuery#execute
  → AbstractR2dbcQuery#executeQuery
  → R2dbcQueryExecution$ResultProcessingExecution#execute
```

`AbstractR2dbcQuery#executeQuery`
(пакет: `org.springframework.data.r2dbc.repository.query`) — выбирает способ
выполнения запроса:

- modifying query: `DatabaseClient.sql(operation).fetch()`;
- exists query: `DatabaseClient.sql(operation).map(...)`;
- обычный select: `R2dbcEntityOperations.query(...)`.

Для обычного select вызывается:

```text
R2dbcEntityOperations#query(
    PreparedOperation<?>,
    Class<?>,
    Class<?>
)
```

После получения `RowsFetchSpec` Spring Data создаёт
`R2dbcQueryExecution$ResultProcessingExecution`.

`R2dbcQueryExecution$ResultProcessingExecution#execute`
(пакет: `org.springframework.data.r2dbc.repository.query`) — вызывает
вложенную стратегию выполнения `delegate.execute(fetchSpec)`, затем применяет
converter результата и возвращает итоговый `Publisher`.


## Что именно видно из кода

Ваш конкретный путь выглядит так:

```text
repository proxy
  → AbstractR2dbcQuery.execute(parameters)
  → createQuery(parameterAccessor)
  → AbstractR2dbcQuery.executeQuery(parameterAccessor, operation)
  → entityOperations.query(operation, domainType, resultType)
  → ResultProcessingExecution.execute(fetchSpec)
  → delegate.execute(fetchSpec)
  → converter.convert(...)
  → Publisher
```

`execute()` здесь **не выполняет SQL сразу**. 

- Он собирает реактивную цепочку и возвращает `Publisher`;
- фактическая работа с БД начинается, когда этот `Publisher` будет подписан downstream-кодом. 
- Это прямо следует из того, что метод возвращает результат `flatMapMany(...)`, а не блокирующий результат запроса. 
- `AbstractR2dbcQuery` является базовым классом для R2DBC repository-query реализаций.


### 8. Response клиенту

```text
- org.springframework.http.codec.json.Jackson2JsonEncoder#encode
- io.netty.handler.codec.http.HttpResponseEncoder#encode
```

- Первый метод превращает `OrderResponse` в JSON DataBuffer;
- второй кодирует **outbound** HTTP-объекты **в байты** сокета. 

- `HttpResponseEncoder` — outbound-половина `HttpServerCodec`.

---

## Pipeline HTTP/1.1

```text
[HttpCodec] → [HttpTrafficHandler] → … → [ReactiveBridge]
```

`HttpObjectAggregator` не является обязательным шагом обычного HTTP/1.1 request path. В API Reactor Netty aggregator указан в WebSocket-ветке.

**Источник:** https://projectreactor.io/docs/netty/1.3.4/api/reactor/netty/NettyPipeline.html

**Цитата:**
> -> http traffic handler ? [HttpTrafficHandler]
>
> ...
>
> -> websocket frame aggregator ? [WsFrameAggregator]
>
> ...
>
> => [ReactiveBridge]

**Перевод:**
> В server pipeline расположен `HttpTrafficHandler`; frame aggregator относится к WebSocket-ветке перед `ReactiveBridge`.

---

## Граница доказательства

Этот trace доказывает:

```text

Acceptor
(reactor.netty.transport.ServerTransport$Acceptor#channelRead,
 - пакет: reactor.netty.transport;
 - внутренний handler Reactor Netty, который принимает новый TCP Channel
 и подключает его к дальнейшей обработке
)
→

Netty read
(io.netty.channel.nio.AbstractNioMessageChannel$NioMessageUnsafe#read,
 - пакет: io.netty.channel.nio;
 - низкоуровневый Netty-метод: забирает готовые TCP-соединения/данные из NIO
)

→

HttpTrafficHandler
(reactor.netty.http.server.HttpTrafficHandler#channelRead,
 - пакет: reactor.netty.http.server;
 - Reactor Netty handler: получает декодированный HttpRequest,
 создаёт HttpServerOperations и передаёт запрос дальше
)

→

HttpServerOperations
(reactor.netty.http.server.HttpServerOperations#onInboundNext,
 - пакет: reactor.netty.http.server;
 - объект, представляющий текущую HTTP-операцию:
     - request, 
     - response и 
     - состояние обработки этого запроса
)

→

DispatcherHandler
(org.springframework.web.reactive.DispatcherHandler#handle,
 - пакет: org.springframework.web.reactive;
 - центральный диспетчер WebFlux: находит контроллер, который должен обработать HTTP-запрос
)

→

RequestMappingHandlerAdapter
(org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter#handle,
 - пакет: org.springframework.web.reactive.result.method.annotation;
 - адаптер, который вызывает найденный метод аннотационного контроллера,
 например метод с @GetMapping
)

→

OrderController
(com.example.reactivestudy.controller.OrderController#first10,
 - пакет: com.example.reactivestudy.controller;
 - ваш HTTP-контроллер: принимает запрос GET /api/orders/first-10
)

→

OrderService
(com.example.reactivestudy.service.OrderService#findFirst10,
 - пакет: com.example.reactivestudy.service;
 - ваш сервисный слой: выполняет прикладную логику получения первых 10 заказов
)



---

## Повторная проверка

```bat

cd D:\Project_infra\greeting-service-infra\reactive-study
gradlew.bat bootRun --args="--spring.profiles.active=local"
```

```bat

curl.exe --noproxy "*" -v http://127.0.0.1:8083/api/orders/first-10
```

Минимальный набор breakpoint:

1. `ServerTransport$Acceptor#channelRead`
2. `HttpTrafficHandler#channelRead`
3. `DispatcherHandler#handle`
4. `OrderController#first10`
5. `OrderService#findFirst10`
6. `HttpResponseEncoder#encode`

Инструкция по фильтру agent: [`docs/java-agent-trace/Java agent — проверка пути вызовов своими руками.md`](../../docs/java-agent-trace/Java%20agent%20%E2%80%94%20проверка%20пути%20вызовов%20своими%20руками.md).
