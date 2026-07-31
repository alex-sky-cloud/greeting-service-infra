
# HTTP-REQUEST-DEBUG-BREAKPOINTS.md — исправленная версия (актуально на 31.07.2026)

> Дополнено точками останова для **инициализации транспорта**: серверный сокет, EventLoopGroup (boss/worker), фабрика Channel. Старые точки (Netty pipeline / WebFlux / R2DBC) сохранены и проверены по актуальным версиям библиотек.

**Проверенные актуальные версии (июль 2026):**
- Netty: `4.1.136.Final` (LTS-ветка 4.1) / `4.2.16.Final` (новая ветка 4.2, рекомендуемая) [web:30][web:24]
- Reactor Netty: BOM `2025.0.6`, `reactor-netty-core`/`reactor-netty-http` — актуальная линейка `1.3.x` [web:23][web:19]
- Spring Framework / spring-webflux: `6.0.x` (актуальный минорный `6.0.16`, ветка `7.0` в milestone) [web:25]

---

## Блок 0 (НОВОЕ) — Инициализация транспорта: где готовится серверный сокет и EventLoopGroup

Этого блока не было в исходном файле, хотя именно тут находятся классы, которые вы искали: "класс, который готовит event loop", "класс, который готовит фабрику channel и workers".

| # | Слой | JAR / модуль | Класс | Метод (ориентир) | Что увидеть |
|---|------|--------------|-------|-------------------|-------------|
| 0a | **Reactor Netty — точка входа биндинга** | `reactor-netty-core` | `reactor.netty.transport.ServerTransport` (наследник — `reactor.netty.http.server.HttpServerBind` / `reactor.netty.tcp.TcpServerBind`) | `bind()` | Точка, откуда Reactor Netty начинает готовить Netty `ServerBootstrap` |
| 0b | **Пул EventLoop (общий для всего приложения)** | `reactor-netty-core` | `reactor.netty.resources.LoopResources` (реализация — `reactor.netty.resources.DefaultLoopResources`), синглтон-держатель — `reactor.netty.http.server.HttpResources` | `onServerSelect(boolean useNative)` / `onServer(...)` | Здесь создаются **обе** группы событийных циклов: boss (`serverLoop`) и worker (`cacheNioEventLoopGroup` / `cacheNativeEventLoopGroup`) [web:4][web:7] |
| 0c | **Boss EventLoopGroup (фактическая реализация потока)** | `netty-transport` | `io.netty.channel.nio.NioEventLoopGroup` (или `io.netty.channel.epoll.EpollEventLoopGroup` при native-транспорте) | конструктор `NioEventLoopGroup(int nThreads, ...)` | Реальный класс, который "готовит event loop" — пул потоков-циклов |
| 0d | **Настройка Netty ServerBootstrap** | `netty-transport` | `io.netty.bootstrap.ServerBootstrap` | `group(bossGroup, workerGroup)`, `channelFactory(...)`, `childHandler(...)` | Здесь связываются boss/worker группы, `ReflectiveChannelFactory<NioServerSocketChannel>` (фабрика Channel) и обработчик дочерних соединений [web:13] |
| 0e | **Bind + создание серверного сокета** | `netty-transport` | `io.netty.bootstrap.AbstractBootstrap` | `doBind(SocketAddress)` → `initAndRegister()` | Здесь физически создаётся `NioServerSocketChannel` — программная обёртка над серверным сокетом (файловым дескриптором, слушающим порт) |
| 0f | **Регистрация интереса к ACCEPT** | `netty-transport` | `io.netty.channel.nio.AbstractNioChannel` | `doBeginRead()` → `selectionKey.interestOps(OP_ACCEPT)` | Момент, когда серверный сокет начинает слушать входящие соединения |
| 0g | **Приём нового соединения (сам accept)** | `netty-transport` | `io.netty.channel.nio.AbstractNioMessageChannel.NioMessageUnsafe` | `read()` → `doReadMessages(...)` | Boss-поток вызывает системный `accept()`, создаёт `NioSocketChannel` для клиента |
| 0h | **Передача принятого канала worker-группе** | `netty-transport` | `io.netty.bootstrap.ServerBootstrap.ServerBootstrapAcceptor` | `channelRead(ChannelHandlerContext ctx, Object msg)` | **Именно этот класс** регистрирует новый `Channel` в Worker `EventLoopGroup` (`childGroup.register(child)`) — граница между boss и worker [web:10][web:16] |

> Источник (актуальная документация): `LoopResources` — "An `EventLoopGroup` selector with associated `Channel` factories" [web:4][web:7]. Именно `LoopResources`/`HttpResources` — тот самый класс, который "готовит фабрику channel и workers", который вы искали.

**Почему в исходном файле не находились точки останова:**
Указанные там классы (`NioEventLoop`, `HttpTrafficHandler`, `HttpServerOperations`, `DispatcherHandler`) существуют и актуальны, но:
1. В новых версиях Reactor Netty (1.3.x) точка входа bind — не сразу `NioEventLoop`, а сначала `ServerTransport`/`TcpServerBind`/`HttpServerBind`, затем `LoopResources` (п. 0a–0c). Без attach sources на `reactor-netty-core` и `netty-transport` IDE может не подсвечивать эти классы для breakpoint.
2. Из-за модуляризации Netty на `netty-4.1` и `netty-4.2` пакеты (`io.netty.channel.nio.*`) физически лежат в разных jar в зависимости от того, какую ветку тянет ваш Spring Boot BOM — стоит свериться, какая версия реально подтянулась (`gradlew dependencies`).
3. `HttpObjectAggregator` — не создаётся по умолчанию в Reactor Netty HTTP server (в отличие от «сырого» Netty), поэтому breakpoint там мог не срабатывать вовсе, если вы тестировали через Reactor Netty/WebFlux, а не голый Netty.

---

## Обновлённая карта пути (сквозная, 12 точек)

| # | Слой | JAR / модуль | Класс | Метод (ориентир) | Что увидеть |
|---|------|--------------|-------|-------------------|-------------|
| 0 | Инициализация (однократно при старте) | см. блок 0 | `LoopResources` → `NioEventLoopGroup` → `ServerBootstrap` → `ServerBootstrapAcceptor` | — | Подготовка серверного сокета, boss/worker групп, фабрики channel |
| 1 | **Netty EventLoop (worker)** | `netty-transport` | `io.netty.channel.nio.NioEventLoop` | `run()` → `processSelectedKeys()` | Selector сообщил: сокет клиента готов к чтению |
| 2 | **Входящий pipeline** | `netty-codec-http` | `io.netty.handler.codec.http.HttpServerCodec` | `decode()` | Байты → `HttpRequest` + `HttpContent` |
| 3 | **Агрегация (обычно выключена в Reactor Netty)** | `netty-codec-http` | `io.netty.handler.codec.http.HttpObjectAggregator` | `decode()` | Части → `FullHttpRequest` (проверьте, добавлен ли хендлер вообще) |
| 4 | **Reactor Netty — трафик-хендлер** | `reactor-netty-http` | `reactor.netty.http.server.HttpTrafficHandler` | `channelRead()` | Граница Netty → Reactor |
| 5 | **Reactor Netty — операции запроса** | `reactor-netty-http` | `reactor.netty.http.server.HttpServerOperations` | `onInboundNext()` | URI, метод, заголовки; тело как `Flux<DataBuffer>` |
| 6 | **Spring WebFlux** | `spring-webflux` | `org.springframework.web.reactive.DispatcherHandler` | `handle()` | Маршрутизация на `@RestController` |
| 7 | **Ваш код** | `reactive-study` | `OrderController` | `first10()` | Возврат `Flux` без `subscribe()` |
| 7b | **Ваш код** | `reactive-study` | `OrderService` | `findFirst10()` | `map(OrderResponse::from)` |
| 7c | **Spring Data R2DBC** | `spring-data-r2dbc` | `SimpleR2dbcRepository` (decompile) | `findAll` / query method | SQL к PostgreSQL |
| 8 | **Исходящий pipeline** | `netty-codec-http` | `io.netty.handler.codec.http.HttpResponseEncoder` (вложен внутри `HttpServerCodec`) | `encode()` | HTTP-ответ → байты в сокет |

> Уточнение: `HttpServerCodec` — это не самостоятельный класс с логикой кодирования, а `CombinedChannelDuplexHandler<HttpRequestDecoder, HttpResponseEncoder>` [web:51] — обёртка, объединяющая в одном хендлере pipeline два вложенных класса: `HttpRequestDecoder` (декодирует входящие байты) и `HttpResponseEncoder` (кодирует исходящий ответ). Отдельного класса `HttpServerEncoder` в API нет. Ставьте breakpoint именно на `HttpResponseEncoder.encode()` — в IDE он может не отображаться при попытке поставить точку прямо в файле `HttpServerCodec.java`, так как там нет собственного тела метода `encode()`.

---

## Что проверить в IDE, если breakpoint не срабатывает

1. **Attach sources** должен быть подключён отдельно для каждого модуля: `netty-transport`, `netty-codec-http`, `netty-common`, `reactor-netty-core`, `reactor-netty-http`, `spring-webflux`, `reactor-core` — не только "reactor-netty" одним артефактом.
2. Проверьте фактически подтянутую версию через `./gradlew :app:dependencies --configuration runtimeClasspath | grep -E "netty|reactor"` — состав пакетов между Netty 4.1 и 4.2 отличается.
3. Если используется native-транспорт (`epoll`/`kqueue`), классы `NioEventLoopGroup`/`NioEventLoop` не используются вовсе — вместо них `EpollEventLoopGroup`/`EpollEventLoop`. Проверьте `reactor.netty.resources.LoopResources.DEFAULT_NATIVE` [web:7].
4. Ставьте breakpoint не только на "entry" методы классов, но и conditional breakpoint на `Thread.currentThread().getName().contains("boss")` внутри `NioEventLoop.run()`, чтобы явно поймать boss-поток отдельно от worker-потоков.
5. `LoopResources`/`HttpResources` — по умолчанию **ленивая инициализация**: singleton создаётся при первом старте сервера (`HttpServer.bindNow()` / Spring Boot autoconfiguration), а не при загрузке класса — ставьте breakpoint в `HttpResources.get()`.

---

## Источники
- Netty `ServerBootstrap` API — group/childGroup/channelFactory [web:13]
- `ServerBootstrap.ServerBootstrapAcceptor#channelRead` — регистрация принятого канала в worker-группе [web:10][web:16]
- `LoopResources` — "EventLoopGroup selector with associated Channel factories" [web:4][web:7]
- Reactor Netty BOM версия 2025.0.6 (актуально на момент проверки) [web:23]
- Netty 4.1.136.Final / 4.2.16.Final — актуальные релизы [web:30][web:24]
- Spring WebFlux `DispatcherHandler` javadoc 6.0.16 [web:25]
- `HttpServerCodec` — `CombinedChannelDuplexHandler<HttpRequestDecoder, HttpResponseEncoder>` [web:51]
