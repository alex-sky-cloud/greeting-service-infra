# HTTP-REQUEST-DEBUG-BREAKPOINTS — исправленная версия (актуально на 31.07.2026)

## Оглавление

- [Проверенные версии](#проверенные-версии)
- [Блок 0 — инициализация транспорта](#блок-0--инициализация-транспорта-где-готовится-серверный-сокет-и-eventloopgroup)
- [Почему точки останова не находились](#почему-в-исходном-файле-не-находились-точки-останова)
- [Сквозная карта пути (12 точек)](#обновлённая-карта-пути-сквозная-12-точек)
- [Что проверить в IDE](#что-проверить-в-ide-если-breakpoint-не-срабатывает)

---

## Проверенные версии

**Утверждение:** актуальная стабильная версия Netty на момент проверки — ветка 4.1 (LTS) и параллельная новая ветка 4.2.

- Источник: https://netty.io/downloads.html

> "netty-4.2.16.Final.tar.gz ‐ 06-Jul-2026 (Stable, Recommended); netty-4.1.136.Final.tar.gz ‐ 09-Jul-2026 (Stable)"

RU:

> «netty-4.2.16.Final.tar.gz — 06.07.2026 (стабильная, рекомендуемая); netty-4.1.136.Final.tar.gz — 09.07.2026 (стабильная)»

**Утверждение:** актуальный BOM Reactor Netty — версия 2025.0.6, публикуется отдельно от версии самой библиотеки reactor-netty.

- Источник: https://projectreactor.io/docs/netty/release/reference/getting-started.html

> "As of this writing, 2025.0.6 is the latest version of the BOM. Check for updates at github.com/reactor/reactor/releases."

RU:

> «На момент написания 2025.0.6 — последняя версия BOM. Проверяйте обновления на github.com/reactor/reactor/releases.»

**Утверждение:** актуальная версия Spring WebFlux (Spring Framework) для стабильной линейки — 6.0.x, конкретно проверенный javadoc — 6.0.16.

- Источник: https://www.javadoc.io/doc/org.springframework/spring-webflux/6.0.16/org/springframework/web/reactive/DispatcherHandler.html

> "Central dispatcher for HTTP request handlers/controllers. Dispatches to registered handlers for processing a request, providing convenient mapping facilities."

RU:

> «Центральный диспетчер для обработчиков/контроллеров HTTP-запросов. Направляет запрос зарегистрированным обработчикам, предоставляя удобные механизмы сопоставления маршрутов.»

---

## Блок 0 — инициализация транспорта: где готовится серверный сокет и EventLoopGroup

Этого блока не было в исходном файле, хотя именно тут находятся классы, которые вы искали: класс, который готовит event loop, и класс, который готовит фабрику Channel и worker-группы.

### 0a. Точка входа биндинга сервера (Reactor Netty)

**Утверждение:** биндинг сервера начинается не с Netty напрямую, а с абстракции Reactor Netty `ServerTransport`, конкретные реализации — `HttpServerBind` (HTTP) и `TcpServerBind` (TCP), метод `bind()`.

- Класс: `reactor.netty.transport.ServerTransport` → `reactor.netty.http.server.HttpServerBind` / `reactor.netty.tcp.TcpServerBind`
- Модуль: `reactor-netty-core`
- Источник: https://github.com/reactor/reactor-netty/blob/master/reactor-netty-core/src/main/java/reactor/netty/tcp/TcpServerBind.java

### 0b. Пул EventLoop — общий для всего приложения

**Утверждение:** классы `LoopResources`/`HttpResources` — это тот самый "селектор EventLoopGroup с привязанными фабриками Channel", который создаёт обе группы событийных циклов: boss и worker.

- Класс: `reactor.netty.resources.LoopResources` (реализация — `DefaultLoopResources`), синглтон-держатель для HTTP — `reactor.netty.http.server.HttpResources`
- Модуль: `reactor-netty-core`
- Метод (ориентир): `onServerSelect(boolean useNative)` / `onServer(...)`
- Источник: https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

> "An EventLoopGroup selector with associated Channel factories."

RU:

> «Селектор EventLoopGroup с привязанными к нему фабриками Channel.»

Дополнительно, исходный код:

- Источник: https://github.com/reactor/reactor-netty/blob/master/reactor-netty-core/src/main/java/reactor/netty/resources/LoopResources.java

### 0c. Boss EventLoopGroup — фактическая реализация потока

**Утверждение:** реальный класс, который "готовит event loop" — это пул потоков `NioEventLoopGroup` (или `EpollEventLoopGroup` при native-транспорте на Linux).

- Класс: `io.netty.channel.nio.NioEventLoopGroup` (альтернатива — `io.netty.channel.epoll.EpollEventLoopGroup`)
- Модуль: `netty-transport`
- Метод (ориентир): конструктор `NioEventLoopGroup(int nThreads, ...)`

### 0d. Настройка Netty ServerBootstrap

**Утверждение:** именно `ServerBootstrap` связывает boss- и worker-группы, фабрику Channel и обработчик дочерних соединений — через методы `group()`, `channelFactory()`, `childHandler()`.

- Класс: `io.netty.bootstrap.ServerBootstrap`
- Модуль: `netty-transport`
- Источник: https://netty.io/4.0/api/io/netty/bootstrap/ServerBootstrap.html

> "Allow to specify a ChannelOption which is used for the Channel instances once they get created (after the acceptor accepted the Channel)."

RU:

> «Позволяет задать ChannelOption, который применяется к экземплярам Channel сразу после их создания (после того как acceptor принял соединение).»

### 0e. Bind и создание серверного сокета

**Утверждение:** физическое создание `NioServerSocketChannel` (программной обёртки над серверным сокетом/файловым дескриптором) происходит в `AbstractBootstrap.doBind()` → `initAndRegister()`.

- Класс: `io.netty.bootstrap.AbstractBootstrap`
- Модуль: `netty-transport`
- Метод (ориентир): `doBind(SocketAddress)` → `initAndRegister()`

### 0f. Регистрация интереса к ACCEPT

**Утверждение:** момент, когда серверный сокет начинает слушать входящие соединения — вызов `selectionKey.interestOps(OP_ACCEPT)` внутри `AbstractNioChannel.doBeginRead()`.

- Класс: `io.netty.channel.nio.AbstractNioChannel`
- Модуль: `netty-transport`
- Метод (ориентир): `doBeginRead()`

### 0g. Приём нового соединения (сам accept)

**Утверждение:** boss-поток вызывает системный `accept()` и создаёт `NioSocketChannel` для клиента внутри `NioMessageUnsafe.read()`.

- Класс: `io.netty.channel.nio.AbstractNioMessageChannel.NioMessageUnsafe`
- Модуль: `netty-transport`
- Метод (ориентир): `read()` → `doReadMessages(...)`

### 0h. Передача принятого канала worker-группе

**Утверждение:** именно `ServerBootstrapAcceptor.channelRead()` регистрирует новый `Channel` в worker `EventLoopGroup` — это граница между boss и worker.

- Класс: `io.netty.bootstrap.ServerBootstrap.ServerBootstrapAcceptor`
- Модуль: `netty-transport`
- Метод: `channelRead(ChannelHandlerContext ctx, Object msg)`
- Источник (описание регистрации в pipeline через acceptor): https://segmentfault.com/a/1190000041438565/en
- Источник (разбор accept на уровне исходного кода): http://www.itsoku.com/article/402

---

## Почему в исходном файле не находились точки останова

Указанные там классы (`NioEventLoop`, `HttpTrafficHandler`, `HttpServerOperations`, `DispatcherHandler`) существуют и актуальны, но есть три причины, по которым breakpoint не срабатывал:

1. В новых версиях Reactor Netty (линейка 1.3.x) точка входа bind — не сразу `NioEventLoop`, а сначала `ServerTransport`/`TcpServerBind`/`HttpServerBind`, затем `LoopResources` (см. блок 0). Без attach sources на `reactor-netty-core` и `netty-transport` IDE может не подсвечивать эти классы для установки breakpoint.
2. Из-за параллельного существования веток Netty 4.1 и 4.2 пакеты `io.netty.channel.nio.*` физически лежат в разных jar в зависимости от того, какую ветку тянет ваш Spring Boot BOM — стоит свериться, какая версия реально подтянулась.
3. `HttpObjectAggregator` не создаётся по умолчанию в Reactor Netty HTTP server (в отличие от "голого" Netty), поэтому breakpoint там мог не срабатывать вовсе, если вы тестировали через Reactor Netty/WebFlux, а не через чистый Netty.

---

## Обновлённая карта пути (сквозная, 12 точек)

| # | Слой | Модуль | Класс | Метод (ориентир) | Что увидеть |
|---|------|--------|-------|-------------------|-------------|
| 0 | Инициализация (однократно при старте) | см. блок 0 | `LoopResources` → `NioEventLoopGroup` → `ServerBootstrap` → `ServerBootstrapAcceptor` | — | Подготовка серверного сокета, boss/worker групп, фабрики Channel |
| 1 | Netty EventLoop (worker) | `netty-transport` | `io.netty.channel.nio.NioEventLoop` | `run()` → `processSelectedKeys()` | Selector сообщил: сокет клиента готов к чтению |
| 2 | Входящий pipeline | `netty-codec-http` | `io.netty.handler.codec.http.HttpServerCodec` (вложенный `HttpRequestDecoder`) | `decode()` | Байты → `HttpRequest` + `HttpContent` |
| 3 | Агрегация (обычно выключена в Reactor Netty) | `netty-codec-http` | `io.netty.handler.codec.http.HttpObjectAggregator` | `decode()` | Части → `FullHttpRequest` (проверьте, добавлен ли хендлер вообще) |
| 4 | Reactor Netty — трафик-хендлер | `reactor-netty-http` | `reactor.netty.http.server.HttpTrafficHandler` | `channelRead()` | Граница Netty → Reactor |
| 5 | Reactor Netty — операции запроса | `reactor-netty-http` | `reactor.netty.http.server.HttpServerOperations` | `onInboundNext()` | URI, метод, заголовки; тело как `Flux<DataBuffer>` |
| 6 | Spring WebFlux | `spring-webflux` | `org.springframework.web.reactive.DispatcherHandler` | `handle()` | Маршрутизация на `@RestController` |
| 7 | Ваш код | `reactive-study` | `OrderController` | `first10()` | Возврат `Flux` без `subscribe()` |
| 7b | Ваш код | `reactive-study` | `OrderService` | `findFirst10()` | `map(OrderResponse::from)` |
| 7c | Spring Data R2DBC | `spring-data-r2dbc` | `SimpleR2dbcRepository` (decompile) | `findAll` / query method | SQL к PostgreSQL |
| 8 | Исходящий pipeline | `netty-codec-http` | `io.netty.handler.codec.http.HttpResponseEncoder` (вложен внутри `HttpServerCodec`) | `encode()` | HTTP-ответ → байты в сокет |

**Утверждение:** `HttpServerCodec` — это не самостоятельный класс с логикой кодирования, а объединение двух вложенных хендлеров: `HttpRequestDecoder` и `HttpResponseEncoder`. Отдельного класса `HttpServerEncoder` в API не существует.

- Источник: https://netty.io/4.1/api/io/netty/handler/codec/http/HttpServerCodec.html

> "public final class HttpServerCodec extends CombinedChannelDuplexHandler<HttpRequestDecoder,HttpResponseEncoder> implements HttpServerUpgradeHandler.SourceCodec"

RU:

> «public final class HttpServerCodec расширяет CombinedChannelDuplexHandler<HttpRequestDecoder,HttpResponseEncoder> и реализует HttpServerUpgradeHandler.SourceCodec»

Практический вывод: ставьте breakpoint именно на `HttpResponseEncoder.encode()` — в IDE точка внутри файла `HttpServerCodec.java` может не сработать, так как там нет собственного тела метода `encode()`.

---

## Что проверить в IDE, если breakpoint не срабатывает

1. **Attach sources** должен быть подключён отдельно для каждого модуля: `netty-transport`, `netty-codec-http`, `netty-common`, `reactor-netty-core`, `reactor-netty-http`, `spring-webflux`, `reactor-core` — не только для общего артефакта `reactor-netty`.
2. Проверьте фактически подтянутую версию через `./gradlew :app:dependencies --configuration runtimeClasspath | grep -E "netty|reactor"` — состав пакетов между Netty 4.1 и 4.2 отличается.
3. Если используется native-транспорт (`epoll`/`kqueue`), классы `NioEventLoopGroup`/`NioEventLoop` не используются вовсе — вместо них `EpollEventLoopGroup`/`EpollEventLoop`.

- Источник: https://github.com/reactor/reactor-netty/blob/master/reactor-netty-core/src/main/java/reactor/netty/resources/LoopResources.java

4. Ставьте breakpoint не только на "entry"-методы классов, но и conditional breakpoint на `Thread.currentThread().getName().contains("boss")` внутри `NioEventLoop.run()`, чтобы явно поймать boss-поток отдельно от worker-потоков.
5. `LoopResources`/`HttpResources` по умолчанию используют **ленивую инициализацию**: singleton создаётся при первом старте сервера (`HttpServer.bindNow()` / автоконфигурация Spring Boot), а не при загрузке класса — ставьте breakpoint в `HttpResources.get()`.
