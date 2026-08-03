
# HTTP-REQUEST-DEBUG-BREAKPOINTS.md — исправленная версия (актуально на 31.07.2026)

> Дополнено точками останова для **инициализации транспорта**: серверный сокет, EventLoopGroup (boss/worker), фабрика Channel. Старые точки (Netty pipeline / WebFlux / R2DBC) сохранены и проверены по актуальным версиям библиотек.

## Оглавление

- [Проверенные актуальные версии](#%D0%BF%D1%80%D0%BE%D0%B2%D0%B5%D1%80%D0%B5%D0%BD%D0%BD%D1%8B%D0%B5-%D0%B0%D0%BA%D1%82%D1%83%D0%B0%D0%BB%D1%8C%D0%BD%D1%8B%D0%B5-%D0%B2%D0%B5%D1%80%D1%81%D0%B8%D0%B8)
- [Блок 0 (НОВОЕ) — Инициализация транспорта: где готовится серверный сокет и EventLoopGroup](#%D0%B1%D0%BB%D0%BE%D0%BA-0-%D0%BD%D0%BE%D0%B2%D0%BE%D0%B5--%D0%B8%D0%BD%D0%B8%D1%86%D0%B8%D0%B0%D0%BB%D0%B8%D0%B7%D0%B0%D1%86%D0%B8%D1%8F-%D1%82%D1%80%D0%B0%D0%BD%D1%81%D0%BF%D0%BE%D1%80%D1%82%D0%B0-%D0%B3%D0%B4%D0%B5-%D0%B3%D0%BE%D1%82%D0%BE%D0%B2%D0%B8%D1%82%D1%81%D1%8F-%D1%81%D0%B5%D1%80%D0%B2%D0%B5%D1%80%D0%BD%D1%8B%D0%B9-%D1%81%D0%BE%D0%BA%D0%B5%D1%82-%D0%B8-eventloopgroup)
- [Обновлённая карта пути (сквозная, 12 точек)](#%D0%BE%D0%B1%D0%BD%D0%BE%D0%B2%D0%BB%D1%91%D0%BD%D0%BD%D0%B0%D1%8F-%D0%BA%D0%B0%D1%80%D1%82%D0%B0-%D0%BF%D1%83%D1%82%D0%B8-%D1%81%D0%BA%D0%B2%D0%BE%D0%B7%D0%BD%D0%B0%D1%8F-12-%D1%82%D0%BE%D1%87%D0%B5%D0%BA)
- [Что проверить в IDE, если breakpoint не срабатывает](#%D1%87%D1%82%D0%BE-%D0%BF%D1%80%D0%BE%D0%B2%D0%B5%D1%80%D0%B8%D1%82%D1%8C-%D0%B2-ide-%D0%B5%D1%81%D0%BB%D0%B8-breakpoint-%D0%BD%D0%B5-%D1%81%D1%80%D0%B0%D0%B1%D0%B0%D1%82%D1%8B%D0%B2%D0%B0%D0%B5%D1%82)
- [Источники](#%D0%B8%D1%81%D1%82%D0%BE%D1%87%D0%BD%D0%B8%D0%BA%D0%B8)

***

## Проверенные актуальные версии

- Netty: `4.1.136.Final` (LTS-ветка 4.1) / `4.2.16.Final` (новая ветка 4.2, рекомендуемая)

Источник: https://netty.io/downloads.html

> "netty-4.2.16.Final.tar.gz ‐ 06-Jul-2026 (Stable, Recommended); netty-4.1.136.Final.tar.gz ‐ 09-Jul-2026 (Stable)"

RU:

> «netty-4.2.16.Final.tar.gz — 06.07.2026 (стабильная, рекомендуемая); netty-4.1.136.Final.tar.gz — 09.07.2026 (стабильная)»

- Reactor Netty: BOM `2025.0.6`, `reactor-netty-core`/`reactor-netty-http` — актуальная линейка `1.3.x`

Источник: https://projectreactor.io/docs/netty/release/reference/getting-started.html

> "As of this writing, 2025.0.6 is the latest version of the BOM."

RU:

> «На момент написания 2025.0.6 — последняя версия BOM.»

- Spring Framework / spring-webflux: `6.0.x` (проверенный javadoc — `6.0.16`)

Источник: https://www.javadoc.io/doc/org.springframework/spring-webflux/6.0.16/org/springframework/web/reactive/DispatcherHandler.html

> "Central dispatcher for HTTP request handlers/controllers."

RU:

> «Центральный диспетчер для обработчиков/контроллеров HTTP-запросов.»

***

## Блок 0 (НОВОЕ) — Инициализация транспорта: где готовится серверный сокет и EventLoopGroup

Этого блока не было в исходном файле, хотя именно тут находятся классы, которые вы искали: «класс, который готовит event loop», «класс, который готовит фабрику channel и workers».


| \# | Слой | JAR / модуль | Класс | Метод (ориентир) | Что увидеть |
| :-- | :-- | :-- | :-- | :-- | :-- |
| 0a | **Reactor Netty — точка входа биндинга** | `reactor-netty-core` | `reactor.netty.transport.ServerTransport` (наследник — `reactor.netty.http.server.HttpServerBind` / `reactor.netty.tcp.TcpServerBind`) | `bind()` | Точка, откуда Reactor Netty начинает готовить Netty `ServerBootstrap` |
| 0b | **Пул EventLoop (общий для всего приложения)** | `reactor-netty-core` | `reactor.netty.resources.LoopResources` (реализация — `reactor.netty.resources.DefaultLoopResources`), синглтон-держатель — `reactor.netty.http.server.HttpResources` | `onServerSelect(boolean useNative)` / `onServer(...)` | Здесь создаются обе группы событийных циклов: boss (`serverLoop`) и worker (`cacheNioEventLoopGroup` / `cacheNativeEventLoopGroup`) |
| 0c | **Boss EventLoopGroup (фактическая реализация потока)** | `netty-transport` | `io.netty.channel.nio.NioEventLoopGroup` (или `io.netty.channel.epoll.EpollEventLoopGroup` при native-транспорте) | конструктор `NioEventLoopGroup(int nThreads, ...)` | Реальный класс, который «готовит event loop» — пул потоков-циклов |
| 0d | **Настройка Netty ServerBootstrap** | `netty-transport` | `io.netty.bootstrap.ServerBootstrap` | `group(bossGroup, workerGroup)`, `channelFactory(...)`, `childHandler(...)` | Здесь связываются boss/worker группы, `ReflectiveChannelFactory<NioServerSocketChannel>` (фабрика Channel) и обработчик дочерних соединений |
| 0e | **Bind + создание серверного сокета** | `netty-transport` | `io.netty.bootstrap.AbstractBootstrap` | `doBind(SocketAddress)` → `initAndRegister()` | Здесь физически создаётся `NioServerSocketChannel` — программная обёртка над серверным сокетом (файловым дескриптором, слушающим порт) |
| 0f | **Регистрация интереса к ACCEPT** | `netty-transport` | `io.netty.channel.nio.AbstractNioChannel` | `doBeginRead()` → `selectionKey.interestOps(OP_ACCEPT)` | Момент, когда серверный сокет начинает слушать входящие соединения |
| 0g | **Приём нового соединения (сам accept)** | `netty-transport` | `io.netty.channel.nio.AbstractNioMessageChannel.NioMessageUnsafe` | `read()` → `doReadMessages(...)` | Boss-поток вызывает системный `accept()`, создаёт `NioSocketChannel` для клиента |
| 0h | **Передача принятого канала worker-группе** | `netty-transport` | `io.netty.bootstrap.ServerBootstrap.ServerBootstrapAcceptor` | `channelRead(ChannelHandlerContext ctx, Object msg)` | Именно этот класс регистрирует новый `Channel` в Worker `EventLoopGroup` (`childGroup.register(child)`) — граница между boss и worker |

Источник: https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

> "An EventLoopGroup selector with associated Channel factories."

RU:

> «Селектор EventLoopGroup с привязанными к нему фабриками Channel.»

Именно `LoopResources`/`HttpResources` — тот самый класс, который «готовит фабрику channel и workers», который вы искали.

Источник: https://segmentfault.com/a/1190000041438565/en

Источник: http://www.itsoku.com/article/402

**Если breakpoint не сработает, возможны следующие причины:**

1. В новых версиях Reactor Netty (1.3.x) точка входа bind — не сразу `NioEventLoop`, а сначала `ServerTransport`/`TcpServerBind`/`HttpServerBind`, затем `LoopResources` (п. 0a–0c). Без attach sources на `reactor-netty-core` и `netty-transport` IDE может не подсвечивать эти классы для breakpoint.
2. Из-за модуляризации Netty на `netty-4.1` и `netty-4.2` пакеты (`io.netty.channel.nio.*`) физически лежат в разных jar в зависимости от того, какую ветку тянет ваш Spring Boot BOM — стоит свериться, какая версия реально подтянулась (`gradlew dependencies`).
3. `HttpObjectAggregator` не создаётся по умолчанию в Reactor Netty HTTP server (в отличие от «сырого» Netty), поэтому breakpoint там может не срабатывать, если тестирование идёт через Reactor Netty/WebFlux, а не голый Netty.

***

## Обновлённая карта пути (сквозная, 12 точек)

| \# | Слой | JAR / модуль | Класс | Метод (ориентир) | Что увидеть |
| :-- | :-- | :-- | :-- | :-- | :-- |
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

Уточнение: `HttpServerCodec` — это не самостоятельный класс с логикой кодирования, а обёртка, объединяющая два вложенных класса: `HttpRequestDecoder` и `HttpResponseEncoder`. Отдельного класса `HttpServerEncoder` в API нет.

Источник: https://netty.io/4.1/api/io/netty/handler/codec/http/HttpServerCodec.html

> "public final class HttpServerCodec extends CombinedChannelDuplexHandler<HttpRequestDecoder,HttpResponseEncoder> implements HttpServerUpgradeHandler.SourceCodec"

RU:

> «public final class HttpServerCodec расширяет CombinedChannelDuplexHandler<HttpRequestDecoder,HttpResponseEncoder> и реализует HttpServerUpgradeHandler.SourceCodec»

Ставьте breakpoint именно на `HttpResponseEncoder.encode()` — в IDE он может не отображаться при попытке поставить точку прямо в файле `HttpServerCodec.java`, так как там нет собственного тела метода `encode()`.

***

## Что проверить в IDE, если breakpoint не срабатывает

1. **Attach sources** должен быть подключён отдельно для каждого модуля: `netty-transport`, `netty-codec-http`, `netty-common`, `reactor-netty-core`, `reactor-netty-http`, `spring-webflux`, `reactor-core` — не только `reactor-netty` одним артефактом.
2. Проверьте фактически подтянутую версию через `./gradlew :app:dependencies --configuration runtimeClasspath | grep -E "netty|reactor"` — состав пакетов между Netty 4.1 и 4.2 отличается.
3. Если используется native-транспорт (`epoll`/`kqueue`), классы `NioEventLoopGroup`/`NioEventLoop` не используются вовсе — вместо них `EpollEventLoopGroup`/`EpollEventLoop`. Проверьте `reactor.netty.resources.LoopResources.DEFAULT_NATIVE`.

Источник: https://github.com/reactor/reactor-netty/blob/master/reactor-netty-core/src/main/java/reactor/netty/resources/LoopResources.java

4. Ставьте breakpoint не только на «entry» методы классов, но и conditional breakpoint на `Thread.currentThread().getName().contains("boss")` внутри `NioEventLoop.run()`, чтобы явно поймать boss-поток отдельно от worker-потоков.
5. `LoopResources`/`HttpResources` — по умолчанию **ленивая инициализация**: singleton создаётся при первом старте сервера (`HttpServer.bindNow()` / Spring Boot autoconfiguration), а не при загрузке класса — ставьте breakpoint в `HttpResources.get()`.

***

## Источники

- Netty `ServerBootstrap` API — group/childGroup/channelFactory: https://netty.io/4.0/api/io/netty/bootstrap/ServerBootstrap.html
- `ServerBootstrap.ServerBootstrapAcceptor#channelRead` — регистрация принятого канала в worker-группе: https://segmentfault.com/a/1190000041438565/en
- `LoopResources` — "EventLoopGroup selector with associated Channel factories": https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html
- Reactor Netty BOM версия 2025.0.6: https://projectreactor.io/docs/netty/release/reference/getting-started.html
- Netty 4.1.136.Final / 4.2.16.Final — актуальные релизы: https://netty.io/downloads.html
- Spring WebFlux `DispatcherHandler` javadoc 6.0.16: https://www.javadoc.io/doc/org.springframework/spring-webflux/6.0.16/org/springframework/web/reactive/DispatcherHandler.html
- `HttpServerCodec` — `CombinedChannelDuplexHandler<HttpRequestDecoder, HttpResponseEncoder>`: https://netty.io/4.1/api/io/netty/handler/codec/http/HttpServerCodec.html
