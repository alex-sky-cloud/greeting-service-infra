# Путь HTTP-запроса: точки останова (Netty → Reactor Netty → WebFlux → R2DBC)

Компактный план отладки цепочки **клиент → Netty → Reactor Netty → WebFlux → R2DBC → ответ**.

**Теория:** [`docs/interview/reactive/13 - Путь HTTP-запроса…`](../../docs/interview/reactive/13%20-%20Путь%20HTTP-запроса%20в%20Netty,%20Reactor%20Netty%20и%20Spring%20WebFlux.md)

**Endpoint:** `GET http://127.0.0.1:8083/api/orders/first-10` (profile `local`).

**Граница документа:** рассматривается запрос к уже запущенному приложению. Создание `EventLoopGroup`, server Channel и bind `:8083` — в [`BLOCK-0-INIT-PATH-VERIFICATION.md`](BLOCK-0-INIT-PATH-VERIFICATION.md).

**Проверка документа:** 07.08.2026 — один реальный `GET` вернул `200 OK` и 10 заказов. Runtime trace: `docs/block0-verify/agent/http-request-trace.log`; FQCN и сигнатуры сверены с API/исходниками именно версий проекта.

---

## Оглавление

- [Версии: ваш проект](#версии-ваш-проект)
- [Подготовка](#подготовка)
- [Pipeline Reactor Netty (официальная схема)](#pipeline-reactor-netty-официальная-схема)
- [Карта breakpoint (сквозная)](#карта-breakpoint-сквозная)
- [Порядок прохождения одного запроса](#порядок-прохождения-одного-запроса)
- [Project Reactor / R2DBC](#project-reactor--r2dbc)
- [Минимальный сценарий](#минимальный-сценарий)
- [Если breakpoint не срабатывает](#если-breakpoint-не-срабатывает)
- [Источники](#источники)

---

## Версии: ваш проект

### Фактически в `reactive-study`

Проверка:

```bat

cd D:\Project_infra\greeting-service-infra\reactive-study
gradlew.bat dependencies --configuration runtimeClasspath
```

| Компонент | Разрешённая версия |
|-----------|-------------------|
| Spring Boot | 4.0.5 |
| Spring WebFlux | **7.0.6** |
| Reactor Core | **3.8.4** |
| reactor-netty-http | **1.3.4** |
| Netty (netty-codec-http и др.) | **4.2.12.Final** |
| Spring Data R2DBC | **4.0.4** |

Breakpoint ставьте по этим JAR, а не по примерам, написанным для Netty 4.1 или другой патч-версии Reactor Netty.

---

## Подготовка

1. Attach sources: `netty-transport`, `netty-codec-http`, `netty-codec-base`, `netty-handler`, `reactor-netty-core`, `reactor-netty-http`, `spring-webflux`, `reactor-core`.
2. Breakpoint на **entry** или **conditional** (имя потока `reactor-http-n` / `reactor-http-n-1`).
3. Запуск:

```bat

cd D:\Project_infra\greeting-service-infra\reactive-study
gradlew.bat bootRun --args="--spring.profiles.active=local"
```

4. Запрос: `curl.exe --noproxy "*" -v http://127.0.0.1:8083/api/orders/first-10`



## Pipeline Reactor Netty (официальная схема)

Порядок handler'ов для **HTTP/1.1 server** (класс `NettyPipeline`, reactor-netty 1.3.x):

```text
… → [HttpCodec] → … → [HttpTrafficHandler] → … → [ReactiveBridge]
```

**Источник:** https://projectreactor.io/docs/netty/1.3.4/api/reactor/netty/NettyPipeline.html

| Handler в pipeline | Breakpoint | Примечание |
|--------------------|------------|------------|
| `HttpCodec` | `HttpRequestDecoder.decode` | Reactor именует handler `HttpCodec`; это `HttpServerCodec`, внутри которого decoder и encoder |
| `HttpTrafficHandler` | `channelRead` | **Главная** точка входа HTTP-запроса |
| `HttpAggregator` | — | Не обязательный этап обычного HTTP/1.1; в API указан для WebSocket |
| `ReactiveBridge` | — | Передача в Reactor-цепочку |

**Утверждение:** `HttpObjectAggregator` не является обязательной точкой останова обычного HTTP/1.1 запроса в Reactor Netty. В официальной схеме server pipeline есть `HttpTrafficHandler`; aggregator указан в WebSocket-ветке.

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

## Карта breakpoint (сквозная)

| # | Слой | JAR | Класс (полный путь) | Метод | Что увидеть |
|---|------|-----|-------|-------|-------------|
| 0 | Init транспорта | см. Block 0 | — | — | **Не предмет этого документа** |
| 0g | Accept | `reactor-netty-core` | `reactor.netty.transport.ServerTransport$Acceptor` | `channelRead` | Новое TCP-соединение; runtime ✅ |
| 1 | EventLoop | `netty-transport` | `io.netty.channel.nio.NioIoHandler` | `run` | Windows/NIO: обработка selected keys; runtime ✅ |
| 2 | Codec | `netty-codec-http` | `HttpRequestDecoder` *(inbound half of `HttpServerCodec`)* | `decode()` | Байты → `HttpRequest` |
| 3 | *(не обязательный шаг)* | — | `HttpObjectAggregator` | — | Не ждите его при обычном HTTP/1.1 |
| 4 | Reactor Netty | `reactor-netty-http` | `HttpTrafficHandler` | `channelRead()` | `msg instanceof HttpRequest`; создание `HttpServerOperations` |
| 5 | Reactor Netty | `reactor-netty-http` | `HttpServerOperations` | `onInboundNext()` *(protected)* | Входящее HTTP-сообщение, включая завершающий content у GET; альтернатива — `HttpTrafficHandler.channelRead` |
| 5-SB | Spring Framework | `spring-web` | `org.springframework.http.server.reactive.ReactorHttpHandlerAdapter` | `apply(...)` | Мост Reactor Netty → `HttpHandler` |
| 6 | WebFlux | `spring-webflux` | `DispatcherHandler` | `handle()` | Маршрут на `OrderController` |
| 7 | Приложение | `reactive-study` | `OrderController` | `first10()` | Возврат `Flux` |
| 7b | Приложение | `OrderService` | `findFirst10()` | Возвращает repository `Flux` с `map` |
| 7c | Spring Data R2DBC | `spring-data-r2dbc` | `org.springframework.data.r2dbc.repository.query.PartTreeR2dbcQuery` | `execute()` | Derived query `findTop10ByOrderByIdAsc` |
| 8a | WebFlux JSON | `spring-web` | `org.springframework.http.codec.json.Jackson2JsonEncoder` | `encode()` | `OrderResponse` → JSON DataBuffer |
| 8b | Codec out | `netty-codec-http` | `HttpResponseEncoder` *(outbound half of `HttpServerCodec`)* | `encode()` | HTTP-объекты → байты сокета |

**Источник:** https://netty.io/4.2/api/io/netty/handler/codec/http/HttpServerCodec.html

**Цитата:**
> A combination of `HttpRequestDecoder` and `HttpResponseEncoder` which enables easier server side HTTP implementation.

**Перевод:**
> `HttpServerCodec` объединяет `HttpRequestDecoder` и `HttpResponseEncoder` для серверной HTTP-реализации.

**Вывод:** отдельного `HttpServerEncoder` в этом пути нет. Ставьте breakpoint на `HttpRequestDecoder.decode` для входящих данных и на `HttpResponseEncoder.encode` для исходящих.

**Утверждение:** `ReactorHttpHandlerAdapter#apply` — мост из Reactor Netty в Spring `HttpHandler`; `DispatcherHandler#handle` затем выбирает mapping, adapter и result handler.

**Источник:** https://docs.spring.io/spring-framework/docs/7.0.6/javadoc-api/org/springframework/http/server/reactive/ReactorHttpHandlerAdapter.html

**Цитата:**
> Adapt `HttpHandler` to the Reactor Netty channel handling function.

**Перевод:**
> Адаптирует `HttpHandler` к функции обработки канала Reactor Netty.

**Источник:** https://docs.spring.io/spring-framework/docs/7.0.6/javadoc-api/org/springframework/web/reactive/DispatcherHandler.html

**Цитата:**
> Central dispatcher for HTTP request handlers/controllers.

**Перевод:**
> Центральный диспетчер обработчиков HTTP-запросов и контроллеров.

---

## Порядок прохождения одного запроса

### Фаза A — старт приложения (один раз)

Не рассматривается в этой редакции. Для неё используйте [`BLOCK-0-INIT-PATH-VERIFICATION.md`](BLOCK-0-INIT-PATH-VERIFICATION.md): там проверены `NettyWebServer.start`, `ServerTransport.bindNow`, `TransportConnector.bind`, создание EventLoopGroup и server Channel.

### Фаза B — `GET /api/orders/first-10`

```text
curl → TCP connect
→ [0g ServerTransport$Acceptor.channelRead — accept и передача child Channel]
→ [1 NioIoHandler.run — worker EventLoop обрабатывает READ]
  → [2 HttpRequestDecoder.decode → HttpRequest]
→ (HttpObjectAggregator — не обязательный этап HTTP/1.1)
  → [4 HttpTrafficHandler.channelRead — HttpRequest, new HttpServerOperations]
→ [5 HttpServerOperations.onInboundNext]
→ [5-SB ReactorHttpHandlerAdapter.apply → HttpHandler]
  → [6 DispatcherHandler.handle]
  → [7 OrderController.first10() — возврат Flux]
→ [7b OrderService.findFirst10()]
→ [7c PartTreeR2dbcQuery.execute → R2DBC → PostgreSQL]
→ [8a Jackson2JsonEncoder.encode → JSON DataBuffer]
→ [8b HttpResponseEncoder.encode]
  → сокет → curl
```

**Важно:** вход в `OrderController.first10()` и `OrderService.findFirst10()` доказывает создание `Flux`, но не сам SQL. Derived query выполняется при подписке и спросе downstream; для точной остановки ставьте breakpoint на `PartTreeR2dbcQuery.execute`.

---

## Project Reactor / R2DBC

| Цель | Класс → метод |
|------|-------|
| Вызов derived query | `org.springframework.data.r2dbc.repository.query.PartTreeR2dbcQuery` → `execute` |
| Общая логика Spring Data query | `org.springframework.data.r2dbc.repository.query.AbstractR2dbcQuery` → `execute` |
| JSON перед ответом | `org.springframework.http.codec.json.Jackson2JsonEncoder` → `encode` |
| Спрос downstream | `org.reactivestreams.Subscription` → `request(long)` |

SQL: `logging.level.io.r2dbc.postgresql.QUERY=DEBUG`.

---

## Минимальный сценарий

На один запрос:

1. `ServerTransport$Acceptor.channelRead`
2. `HttpTrafficHandler.channelRead` (при `HttpRequest`)
3. `DispatcherHandler.handle`
4. `OrderController.first10`
5. `OrderService.findFirst10`
6. `HttpResponseEncoder.encode`

Для R2DBC добавьте `PartTreeR2dbcQuery.execute`. Инициализация транспорта — отдельный Block 0, не смешивайте оба сценария в одном запуске.

---

## Если breakpoint не срабатывает

1. Сверьте **фактические** JAR: `gradlew.bat dependencies --configuration runtimeClasspath`.
2. Для Netty **4.2** attach sources к `netty-transport`, `netty-codec-http`, `netty-codec-base`, `netty-handler`.
3. На Windows/NIO смотрите `io.netty.channel.nio.NioIoHandler#run`, а не `NioEventLoop#run`. Для Linux native transport классы будут другими.
4. `HttpObjectAggregator` — **не ждите** срабатывания в default Reactor Netty HTTP server.
5. `HttpServerOperations.onInboundNext` — **protected**; используйте `HttpTrafficHandler.channelRead`.
6. `ReactorHttpHandlerAdapter` лежит в `spring-web`, не в `spring-boot-reactor-netty`.
7. Не ставьте breakpoint на интерфейс `OrderRepository`, если нужен реальный вызов query: используйте `PartTreeR2dbcQuery.execute`.
8. Block 0 — только при **перезапуске**; `ServerTransport$Acceptor.channelRead` — при каждом новом TCP-соединении.

---

## Источники

- [`NettyPipeline`, Reactor Netty 1.3.4](https://projectreactor.io/docs/netty/1.3.4/api/reactor/netty/NettyPipeline.html)
- [`HttpServerCodec`, Netty 4.2](https://netty.io/4.2/api/io/netty/handler/codec/http/HttpServerCodec.html)
- [`HttpTrafficHandler`, исходники Reactor Netty 1.3.4](https://github.com/reactor/reactor-netty/blob/v1.3.4/reactor-netty-http/src/main/java/reactor/netty/http/server/HttpTrafficHandler.java)
- [`ReactorHttpHandlerAdapter`, Spring Framework 7.0.6](https://docs.spring.io/spring-framework/docs/7.0.6/javadoc-api/org/springframework/http/server/reactive/ReactorHttpHandlerAdapter.html)
- [`DispatcherHandler`, Spring Framework 7.0.6](https://docs.spring.io/spring-framework/docs/7.0.6/javadoc-api/org/springframework/web/reactive/DispatcherHandler.html)
- Runtime trace: `docs/block0-verify/agent/http-request-trace.log`
