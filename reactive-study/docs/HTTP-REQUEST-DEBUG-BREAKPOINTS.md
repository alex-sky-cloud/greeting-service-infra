# Путь HTTP-запроса: точки останова (Netty → Reactor Netty → WebFlux → R2DBC)

Компактный план отладки цепочки **клиент → Netty → Reactor Netty → WebFlux → R2DBC → ответ**.

**Теория:** [`docs/interview/reactive/13 - Путь HTTP-запроса…`](../../docs/interview/reactive/13%20-%20Путь%20HTTP-запроса%20в%20Netty,%20Reactor%20Netty%20и%20Spring%20WebFlux.md)

**Endpoint:** `GET http://localhost:8083/api/orders/first-10` (profile `local`).

**Проверка документа:** 03.08.2026 — сверка с javadoc Netty/Reactor Netty, исходниками reactor-netty **v1.3.4** и `./gradlew dependencies` модуля `reactive-study`.

---

## Оглавление

- [Версии: upstream и ваш проект](#версии-upstream-и-ваш-проект)
- [Подготовка](#подготовка)
- [Блок 0 — инициализация транспорта](#блок-0--инициализация-транспорта)
- [Pipeline Reactor Netty (официальная схема)](#pipeline-reactor-netty-официальная-схема)
- [Карта breakpoint (сквозная)](#карта-breakpoint-сквозная)
- [Порядок прохождения одного запроса](#порядок-прохождения-одного-запроса)
- [Project Reactor / R2DBC](#project-reactor--r2dbc)
- [Минимальный сценарий](#минимальный-сценарий)
- [Если breakpoint не срабатывает](#если-breakpoint-не-срабатывает)
- [Источники](#источники)

---

## Версии: upstream и ваш проект

### Актуально upstream (на дату проверки)

| Компонент | Версия | Источник |
|-----------|--------|----------|
| Netty (рекомендуемая) | **4.2.16.Final** (06.07.2026) | https://netty.io/downloads.html |
| Netty 4.1 LTS | 4.1.136.Final (09.07.2026) | там же |
| Reactor BOM | **2025.0.6** | https://projectreactor.io/docs/netty/release/reference/getting-started.html |
| Reactor Netty | линейка **1.3.x**, transitive **Netty 4.2.x** | там же |

**Цитата (Reactor Netty Getting Started):**
> As of this writing, 2025.0.6 is the latest version of the BOM. … It has transitive dependencies on … Netty v4.2.x

**Перевод:**
> BOM 2025.0.6 — последняя версия. Транзитивная зависимость — Netty 4.2.x.

### Фактически в `reactive-study` (Spring Boot 4.0.5)

Проверка:

```bash

cd reactive-study
./gradlew dependencies --configuration runtimeClasspath
```

| Компонент | Разрешённая версия |
|-----------|-------------------|
| Spring Boot | 4.0.5 |
| Spring WebFlux | **7.0.6** |
| Reactor Core | **3.8.4** |
| reactor-netty-http | **1.3.4** |
| Netty (netty-codec-http и др.) | **4.2.12.Final** |

> Spring Boot **фиксирует** свои версии Netty/Reactor — они могут отличаться от «последних» на netty.io. Breakpoint ставьте по **фактическим** jar из Gradle, не по таблице upstream.

---

## Подготовка

1. Attach sources: `netty-transport`, `netty-codec-http`, `netty-codec-base`, `netty-handler`, `reactor-netty-core`, `reactor-netty-http`, `spring-webflux`, `reactor-core`.
2. Breakpoint на **entry** или **conditional** (имя потока `reactor-http-n` / `reactor-http-n-1`).
3. Запуск:

```bash

cd reactive-study
./gradlew bootRun --args='--spring.profiles.active=local'
```

4. Запрос: `curl -v http://localhost:8083/api/orders/first-10`

---

## Блок 0 — инициализация транспорта

Один раз при старте контекста Spring (до `curl`).

| # | JAR | Класс | Метод | Что увидеть |
|---|-----|-------|-------|-------------|
| 0-SB | `spring-boot-reactor-netty` | `org.springframework.boot.reactor.netty.NettyWebServer` | `start()` | Spring поднимает embedded-сервер |
| 0-SB | `spring-boot-reactor-netty` | `org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory` | `getWebServer(HttpHandler)` | Сборка `HttpServer` + `ReactorHttpHandlerAdapter` |
| 0a | `reactor-netty-http` | `HttpServerBind` (extends `HttpServer`) | `bind()` → `Mono<DisposableServer>` | Точка входа Reactor Netty (v1.3.4: `bind()` возвращает `Mono`) |
| 0b | `reactor-netty-core` | `LoopResources` / `HttpResources` | `create(...)` / `get()` | Boss + worker EventLoopGroup |
| 0c | `netty-transport` | `NioEventLoopGroup` или `EpollEventLoopGroup` | конструктор | Пул EventLoop (на Linux по умолчанию часто Epoll) |
| 0d | `netty-transport` | `ServerBootstrap` | `group(...)`, `childHandler(...)` | Boss/worker + pipeline дочернего channel |
| 0e | `netty-transport` | `AbstractBootstrap` | `doBind()` | `NioServerSocketChannel`, bind порта 8083 |
| 0f | `netty-transport` | `AbstractNioChannel` | `doBeginRead()` | `OP_ACCEPT` |
| 0g | `netty-transport` | `AbstractNioMessageChannel.NioMessageUnsafe` | `read()` | Boss: `accept()` |
| 0h | `netty-transport` | `ServerBootstrap.ServerBootstrapAcceptor` | `channelRead(...)` | `childGroup.register(child)` |

**Источник:** https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

**Цитата:**
> An EventLoopGroup selector with associated Channel factories.

**Перевод:**
> Селектор EventLoopGroup с фабриками Channel.

**Источник (Spring Boot 4):** https://docs.spring.io/spring-boot/4.0.5/api/java/org/springframework/boot/reactor/netty/NettyReactiveWebServerFactory.html

**Цитата:**
> ReactiveWebServerFactory that can be used to create NettyWebServers.

**Перевод:**
> Фабрика для создания embedded NettyWebServer в Spring Boot WebFlux.

---

--- Реально проверенный путь прохождения при инициализации приложения (момент старта приложения)


- **0-SB**

  - org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;

```java
    public WebServer getWebServer(HttpHandler httpHandler) {
        HttpServer httpServer = this.createHttpServer();
        ReactorHttpHandlerAdapter handlerAdapter = new ReactorHttpHandlerAdapter(httpHandler);
        NettyWebServer webServer = this.createNettyWebServer(httpServer, handlerAdapter, this.lifecycleTimeout, this.getShutdown());
        webServer.setRouteProviders(this.routeProviders);
        return webServer;
    }
```

- **O-SB**

  - org.springframework.boot.reactor.netty.NettyWebServer
```java

 public void start() throws WebServerException {
        DisposableServer disposableServer = this.disposableServer;
        if (disposableServer == null) {
            try {
                disposableServer = this.startHttpServer();
                this.disposableServer = disposableServer;
            } catch (Exception var4) {
                ...
```



- ???

  - reactor.netty.transport.ServerTransport

```java
public Mono<? extends DisposableServer> bind() {
		CONF config = configuration();
		Supplier<? extends SocketAddress> bindAddress = config.bindAddress();
		Objects.requireNonNull(bindAddress, "bindAddress");

		Mono<? extends DisposableServer> mono =  Mono.create(sink -> {
			SocketAddress local = Objects.requireNonNull(bindAddress.get(), "Bind Address supplier returned null");
			if (local instanceof InetSocketAddress) {
				InetSocketAddress localInet = (InetSocketAddress) local;
              ...............

```

- ???
  - reactor.netty.transport.ServerTransportConfig
  
```java

@Override
	protected final EventLoopGroup eventLoopGroup() {
		return loopResources().onServerSelect(isPreferNative());
	}
```

- ???

  - reactor.netty.resources.DefaultLoopResources

```java

	@Override
	public EventLoopGroup onServerSelect(boolean useNative) {
		if (useNative && LoopResources.hasNativeSupport()) {
			return cacheNativeSelectLoops();
		}
		return cacheNioSelectLoops();
	}
```

- ???
 - io.netty.channel.nio.AbstractNioChannel

```java

protected void addAndSubmit(NioIoOps addOps) {
  int interestOps = selectionKey().interestOps();
  if (!addOps.isIncludedIn(interestOps)) {
    try {
      registration().submit(NioIoOps.valueOf(interestOps).with(addOps));
    } catch (Exception e) {
      throw new ChannelException(e);
    }
  }
}


```

- io.netty.channel.nio.AbstractNioChannel

```java

   protected void doBeginRead() throws Exception {
        // Channel.read() or ChannelHandlerContext.read() was called
        IoRegistration registration = this.registration;
        if (registration == null || !registration.isValid()) {
            return;
        }

        readPending = true;

        addAndSubmit(readOps);
    }
```
---

## Pipeline Reactor Netty (официальная схема)

Порядок handler'ов для **HTTP/1.1 server** (класс `NettyPipeline`, reactor-netty 1.3.x):

```text
… → [HttpCodec] → … → [HttpTrafficHandler] → … → [ReactiveBridge]
```

**Источник:** https://projectreactor.io/docs/netty/release/api/reactor/netty/NettyPipeline.html

| Handler в pipeline | Breakpoint | Примечание |
|--------------------|------------|------------|
| `HttpCodec` | `HttpServerCodec` / `HttpRequestDecoder.decode` | Reactor именует handler `HttpCodec`; внутри — Netty codec |
| `HttpTrafficHandler` | `channelRead` | **Главная** точка входа HTTP-запроса |
| `HttpObjectAggregator` | — | **Не в default pipeline** для обычного HTTP/1.1; только для websocket-upgrade (`http aggregator (websocket)`) |
| `ReactiveBridge` | — | Передача в Reactor-цепочку |

> **Исправление к старым версиям документа:** `HttpObjectAggregator` **не обязателен** и в Reactor Netty **обычно отсутствует** — не ставьте на него как на обязательный шаг.

---

## Карта breakpoint (сквозная)

| # | Слой | JAR | Класс | Метод | Что увидеть |
|---|------|-----|-------|-------|-------------|
| 0 | Старт | см. блок 0 | `NettyWebServer` → `HttpServerBind.bind` | — | Bind порта |
| 0g–0h | Accept | `netty-transport` | `NioMessageUnsafe` / `ServerBootstrapAcceptor` | `read` / `channelRead` | Новое TCP-соединение |
| 1 | EventLoop worker | `netty-transport` | `NioEventLoop` / `EpollEventLoop` | `run()` | READ ready |
| 2 | Codec | `netty-codec-http` | `HttpRequestDecoder` *(inbound half of `HttpServerCodec`)* | `decode()` | Байты → `HttpRequest` |
| 3 | *(пропуск)* | — | `HttpObjectAggregator` | — | Обычно **нет** в pipeline |
| 4 | Reactor Netty | `reactor-netty-http` | `HttpTrafficHandler` | `channelRead()` | `msg instanceof HttpRequest`; создание `HttpServerOperations` |
| 5 | Reactor Netty | `reactor-netty-http` | `HttpServerOperations` | `onInboundNext()` *(protected)* | Тело запроса; для GET часто пусто. Альтернатива: breakpoint на строке `new HttpServerOperations(...)` в `HttpTrafficHandler` |
| 5-SB | Spring Boot | `spring-boot-reactor-netty` | `ReactorHttpHandlerAdapter` | `apply(...)` / обработка | Мост Reactor Netty → `HttpHandler` |
| 6 | WebFlux | `spring-webflux` | `DispatcherHandler` | `handle()` | Маршрут на `OrderController` |
| 7 | Приложение | `reactive-study` | `OrderController` | `first10()` | Возврат `Flux` |
| 7b | Приложение | `OrderService` | `findFirst10()` | `map` |
| 7c | R2DBC | `spring-data-r2dbc` | `SimpleR2dbcRepository` | query method | SQL |
| 8 | Codec out | `netty-codec-http` | `HttpResponseEncoder` *(outbound half of `HttpServerCodec`)* | `encode()` | JSON → байты |

**Источник:** https://netty.io/4.2/api/io/netty/handler/codec/http/HttpServerCodec.html

**Цитата:**
> public final class HttpServerCodec extends CombinedChannelDuplexHandler<HttpRequestDecoder, HttpResponseEncoder>

**Перевод:**
> Отдельного `HttpServerEncoder` нет — encoder это `HttpResponseEncoder` внутри duplex-handler.

---

## Порядок прохождения одного запроса

### Фаза A — старт приложения (один раз)

```text
ApplicationContext refresh
  → NettyReactiveWebServerFactory.getWebServer(ReactorHttpHandlerAdapter)
  → HttpServerBind.bind() [Mono]
  → LoopResources / HttpResources (HttpResources.get())
  → ServerBootstrap.doBind → порт 8083 слушает
```

Breakpoint: перезапуск с отладчиком, **до** curl.

### Фаза B — `GET /api/orders/first-10`

```text
curl → TCP connect
  → [0g boss accept] → [0h register в worker group]
  → [1 EventLoop: READ]
  → [2 HttpRequestDecoder.decode → HttpRequest]
  → (HttpObjectAggregator — пропуск, нет в default pipeline)
  → [4 HttpTrafficHandler.channelRead — HttpRequest, new HttpServerOperations]
  → [5-SB ReactorHttpHandlerAdapter → HttpHandler]
  → [6 DispatcherHandler.handle]
  → [7 OrderController.first10() — возврат Flux]
  → Spring subscribe на Flux
  → [7b OrderService.map]
  → [7c R2DBC → PostgreSQL]
  → Flux → JSON DataBuffer
  → [8 HttpResponseEncoder.encode]
  → сокет → curl
```

**Важно:** контроллер выполняется **до** subscribe; `map` и SQL — **после** `request(n)`.

---

## Project Reactor / R2DBC

| Цель | Класс |
|------|-------|
| subscribe | `reactor.core.publisher.Flux` |
| map | `reactor.core.publisher.FluxMap` |
| R2DBC | `FluxUsingWhen`, `PostgresqlConnection` |
| backpressure | `Subscription.request(n)` |

SQL: `logging.level.io.r2dbc.postgresql.QUERY=DEBUG`.

---

## Минимальный сценарий

На один запрос:

1. `HttpTrafficHandler.channelRead` (при `HttpRequest`)
2. `DispatcherHandler.handle`
3. `OrderController.first10`
4. `OrderService.findFirst10`
5. `HttpResponseEncoder.encode`

На старт: `NettyWebServer.start()` или `HttpServerBind.bind()`.

---

## Если breakpoint не срабатывает

1. Сверьте **фактические** jar: `./gradlew dependencies --configuration runtimeClasspath`.
2. Netty **4.2** — модули `netty-codec-base`, `netty-codec-compression`; attach sources для codec.
3. Linux: может быть **Epoll**, не Nio — классы `EpollEventLoop*`.
4. `HttpObjectAggregator` — **не ждите** срабатывания в default Reactor Netty HTTP server.
5. `HttpServerOperations.onInboundNext` — **protected**; используйте `HttpTrafficHandler.channelRead`.
6. `HttpServer.bind()` в 1.3.x — **Mono**; Spring вызывает `bindNow()` внутри `NettyWebServer.start()`.
7. Блок 0 — только при **перезапуске**; accept (0g–0h) — при **каждом** новом TCP-соединении curl.

---

## Источники

- Netty releases: https://netty.io/downloads.html  
- `HttpServerCodec`: https://netty.io/4.2/api/io/netty/handler/codec/http/HttpServerCodec.html  
- Reactor Netty Getting Started / BOM 2025.0.6: https://projectreactor.io/docs/netty/release/reference/getting-started.html  
- `NettyPipeline` (порядок handlers): https://projectreactor.io/docs/netty/release/api/reactor/netty/NettyPipeline.html  
- `LoopResources`: https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html  
- `HttpServer` API: https://projectreactor.io/docs/netty/1.3.6/api/reactor/netty/http/server/HttpServer.html  
- `NettyReactiveWebServerFactory` (Spring Boot 4.0.5): https://docs.spring.io/spring-boot/4.0.5/api/java/org/springframework/boot/reactor/netty/NettyReactiveWebServerFactory.html  
- `DispatcherHandler` (Spring Framework 7): https://docs.spring.io/spring-framework/docs/7.0.x/javadoc-api/org/springframework/web/reactive/DispatcherHandler.html  
- Исходники reactor-netty v1.3.4: https://github.com/reactor/reactor-netty/tree/v1.3.4  
- Документ 13: `docs/interview/reactive/13 - …WebFlux.md`
