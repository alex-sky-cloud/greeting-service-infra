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

`ServerTransport$Acceptor`
(класс: `reactor.netty.transport.ServerTransport$Acceptor`) — внутренний
**handler** Reactor Netty, который получает **новое TCP-соединение**.

Например, когда `curl` подключается к `127.0.0.1:8083`, операционная система
сообщает Netty: «появился новый клиент». 

Тогда вызывается:

```text
ServerTransport$Acceptor#channelRead
```

- Этот метод принимает созданный Netty `Channel` — объект, представляющий
соединение с конкретным клиентом — и добавляет к нему настройку дальнейшей
обработки: **event loop** и **HTTP pipeline**.

```text
curl подключается к порту 8083
  → Netty принимает TCP-соединение
  → ServerTransport$Acceptor#channelRead
  → для соединения настраивается обработка HTTP-запросов
```

Под ним в trace видны Netty-методы:

```text
io.netty.channel.nio.AbstractNioMessageChannel$NioMessageUnsafe#read
io.netty.channel.nio.NioIoHandler#run
io.netty.channel.SingleThreadIoEventLoop#run
```

- `NioIoHandler#run` ждёт сетевые события от NIO: новое подключение или данные.
- `AbstractNioMessageChannel$NioMessageUnsafe#read` забирает готовое новое
  TCP-соединение из NIO.
- `SingleThreadIoEventLoop#run` — главный цикл Netty-потока, который по кругу
  обрабатывает сетевые события и задачи.

### 2. Codec: байты → HTTP request

```text
io.netty.handler.codec.http.HttpRequestDecoder#decode
```

`HttpRequestDecoder`
(класс: `io.netty.handler.codec.http.HttpRequestDecoder`) — читает байты,
полученные из TCP-сокета, и разбирает их как HTTP-запрос.

Например, из таких байтов:

```text
GET /api/orders/first-10 HTTP/1.1
Host: 127.0.0.1:8083
```

он создаёт Netty-объекты `HttpRequest` и, если есть тело запроса,
`HttpContent`.

`HttpServerCodec`
(класс: `io.netty.handler.codec.http.HttpServerCodec`) — объединяет две части:

```text
входящий запрос:
 - HttpRequestDecoder
байты → HttpRequest

исходящий ответ:
 - HttpResponseEncoder
HttpResponse → байты
```

То есть `HttpRequestDecoder#decode` нужен, чтобы Netty понял: полученные из
сокета байты — это HTTP-запрос, и передал его дальше как объект `HttpRequest`.

### 3. Главная точка Reactor Netty

```text
reactor.netty.http.server.HttpTrafficHandler#channelRead
```

`HttpTrafficHandler`
(класс: `reactor.netty.http.server.HttpTrafficHandler`) — обработчик входящего
HTTP-запроса в Reactor Netty.

`channelRead(...)` — метод, который вызывается, когда Netty уже прочитал
запрос из сети и распознал его как HTTP-запрос (`HttpRequest`).

Дальше он создаёт `HttpServerOperations`:

```text
reactor.netty.http.server.HttpServerOperations
```

`HttpServerOperations` — объект, который хранит всё, что относится к одному
конкретному HTTP-обмену: 
 - текущий request, 
 - будущий response и 
 - состояние обработки.

Коротко путь такой:

```text
Netty получил HTTP-запрос
  → HttpTrafficHandler#channelRead
  → создаётся HttpServerOperations для этого запроса
  → запрос передаётся дальше в Reactor Netty и затем в Spring WebFlux
```

`HttpTrafficHandler#channelRead` — подтверждённая runtime-точка вашего trace.

### 4. HTTP operation → WebFlux

```text
reactor.netty.http.server.HttpServerOperations#onInboundNext
```

`HttpServerOperations`  
- (пакет: `reactor.netty.http.server`) — объект Reactor Netty, который
представляет один текущий HTTP-обмен: 
  - входящий request, формируемый response
и состояние их обработки.

`onInboundNext(...)` — метод, который получает очередной **inbound** HTTP-объект
данного запроса: сам `HttpRequest`, часть тела или завершающий HTTP-фрагмент.

Метод `protected`, поэтому breakpoint на нём можно поставить только с
поддержкой non-public methods в IDE. 

Практичнее ставить breakpoint на:

```text
reactor.netty.http.server.HttpTrafficHandler#channelRead
```

`HttpTrafficHandler#channelRead` получает **HTTP-объект** из Netty pipeline,
создаёт или находит `HttpServerOperations` для текущего запроса и передаёт
объект в обработку операции.

Подтверждённый trace показывает следующий переход:

```text
HttpServerOperations#onInboundNext
  → HttpServerOperations#handleDefaultHttpRequest
  → HttpServer$HttpServerHandle#onStateChange
  → DispatcherHandler#handle
```

Роли методов:

- `HttpServerOperations#onInboundNext` — принимает входящее HTTP-событие
  текущего запроса.

- `HttpServerOperations#handleDefaultHttpRequest` — для обычного HTTP-запроса
  запускает стандартный серверный handler Reactor Netty.

- `HttpServer$HttpServerHandle#onStateChange` — получает изменение состояния
  HTTP-операции и запускает обработчик, настроенный для `HttpServer`.
  В Spring Boot WebFlux этим обработчиком является мост
  `ReactorHttpHandlerAdapter`.

- `DispatcherHandler#handle` — первая центральная точка Spring WebFlux:
  принимает уже адаптированный Spring HTTP request и начинает поиск
  контроллера, который должен его обработать.

**Идея перехода:** 
 - `HttpServerOperations` ещё относится к Reactor Netty и
управляет текущей HTTP-операцией. 
 - После `HttpServerHandle#onStateChange`
выполняется **handler**, подключённый Spring Boot, и запрос переходит в WebFlux.

### 5. Мост в Spring

```text
org.springframework.http.server.reactive.ReactorHttpHandlerAdapter#apply
```

`ReactorHttpHandlerAdapter`  
  - (пакет: `org.springframework.http.server.reactive`) — адаптер между API
  - Reactor Netty и общим серверным API Spring WebFlux (`HttpHandler`).

Метод `apply(...)` получает **нативные объекты** Reactor Netty:

```text
HttpServerRequest
HttpServerResponse
```

и оборачивает их в HTTP-объекты Spring WebFlux:

```text
ReactorServerHttpRequest
ReactorServerHttpResponse
```

После этого он вызывает:

```text
httpHandler.handle(request, response)
```

- То есть передаёт запрос из Reactor Netty в WebFlux. 
- В обычном WebFlux
приложении, этим `httpHandler` -  является цепочка Spring, которая далее приходит
в `DispatcherHandler#handle`.

- Отдельно для `HEAD`-запроса response оборачивается в
`HttpHeadResponseDecorator`: 
   - обработчик может сформировать тело ответа, но
клиенту оно не отправляется, как требует HTTP-метод `HEAD`.

Метод возвращает `Mono<Void>` — реактивный сигнал завершения обработки
HTTP-запроса. При некорректном URI он выставляет `400 Bad Request`.

---

Смысл этого участка: 
 - до `apply` запрос представлен типами Reactor Netty, после создания `ReactorServerHttpRequest` и `ReactorServerHttpResponse` — типами Spring WebFlux. 
 - Это и есть граница, на которой транспорт **Netty** передаёт обработку Spring. 
 - Сам класс реализует `BiFunction<HttpServerRequest, HttpServerResponse, Mono<Void>>` и адаптирует `HttpHandler` к функции обработки канала Reactor Netty.


---

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
