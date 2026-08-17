# Блок 0 — что создаётся при инициализации транспорта

**Модуль:** `reactive-study` (Spring Boot 4.0.5, Reactor Netty 1.3.4, Netty 4.2.12)  
**Цель:** 
 - понять **какие объекты уже существуют**, 
 - когда в логе появляется `Netty started on port 8083` — 
 - и **в каждом разделе** сразу видеть **класс + метод**, 
 - куда поставить breakpoint для перепроверки (не искать по всему документу).

> Проверено: `javap` по JAR (SB 4.0.5 / RN 1.3.4), **InitPathAgent** (bytecode agent), jstack после старта.  
> Инструкция agent: [`Java agent для логирования входов в методы.md`](Java%20agent%20для%20логирования%20входов%20в%20методы.md) → `docs/block0-verify/run-with-agent.cmd`.  
> Сверка с учебными материалами: `BLOCK-0-ARCHITECTURE-CROSSCHECK.md`.  
> Сводная таблица всех точек Block 0 — [§3.4](#34-все-точки-проверки-block-0).

---

## Оглавление

- [1. Главная идея](#1-главная-идея)
- [2. Когда начинается init транспорта](#2-когда-начинается-init-транспорта)
  - [Что именно означает `refresh()`](#что-именно-означает-refresh)
  - [`Bean lifecycle` и `Lifecycle` — разные вещи](#bean-lifecycle-и-lifecycle--разные-вещи)

  - [2.1 HttpServer и bindNow — где искать (не гадать)](#21-httpserver-и-bindnow--где-искать-не-гадать)
- [3. Хронология: что создаётся и когда](#3-хронология-что-создаётся-и-когда)
  - [3.1 Схема инициализации — функциональные блоки](#31-схема-инициализации--функциональные-блоки)
  - [3.2 Состояние после init — что уже есть и чего нет](#32-состояние-после-init--что-уже-есть-и-чего-нет)
  - [3.4 Все точки проверки Block 0](#34-все-точки-проверки-block-0)
- [4. Boss и Worker — что это у вас на самом деле](#4-boss-и-worker--что-это-у-вас-на-самом-деле)
- [5. Server socket и Channel — в какой момент](#5-server-socket-и-channel--в-какой-момент)
- [6. EventLoop и Selector — в какой момент](#6-eventloop-и-selector--в-какой-момент)
- [7. Что готово после «Started ReactiveStudyApplication»](#7-что-готово-после-started-reactivestudyapplication)
- [8. Чего ещё нет до первого curl](#8-чего-ещё-нет-до-первого-curl)
- [9. Расхождение с «классическим Netty» из интернета](#9-расхождение-с-классическим-netty-из-интернета)
- [Приложение A — breakpoint](#приложение-a--breakpoint)
- [Приложение B — как проверялось](#приложение-b--как-проверялось)

---

## 1. Главная идея

**Netty разделяет роли** :
  - _одна_ **группа потоков** принимает новые TCP-соединения на listening socket (условный **boss / acceptor**), 
  - другая обслуживает уже принятые соединения клиентов (**worker**).

**Важно для init:** 
  - при старте **Spring WebFlux** создаётся **не весь HTTP-путь**, а **только серверный транспорт**:
     - пул event loop (**boss** + **worker**),
     - **один** server `io.netty.channel.Channel` (обёртка над **listening socket** на порту 8083 (порт приложения из примера)),
     - этот socket переведён в режим «жду подключений» (`OP_ACCEPT`).

Пока никто не вызвал `curl`, **клиентских Channel ещё нет**, HTTP pipeline на соединениях не крутится (не запущен).

**Источник (Netty User Guide):** https://netty.io/wiki/user-guide-for-4.x.html

**Цитата:**
> The first one, often called 'boss', accepts an incoming connection. The second one, often called 'worker', handles the traffic of the accepted connection once the boss accepts the connection and registers the accepted connection to the worker.

**Перевод:**
> Первая группа, обычно **"boss"**, принимает входящее соединение.
> 
> Вторая, **"worker"**, обрабатывает трафик уже **принятого соединения** после того, как **boss** зарегистрировал его в **worker**.

---

## 2. Когда начинается init транспорта

Транспорт **не** создаётся в момент `main()` и **не** при загрузке классов **Netty**.


| Момент | Что происходит | Breakpoint: класс → метод |
| :-- | :-- | :-- |
| `org.springframework.boot.SpringApplication.run(...)` | Это **внешняя точка входа всего startup-процесса**. Здесь Spring Boot подготавливает `ApplicationContext`, а затем вызывает его `refresh()`. Сам breakpoint здесь нужен только чтобы проследить цепочку запуска от `main()`; он **не означает отдельную фазу «до refresh»** и не является точкой инициализации транспорта. | *(опционально)* `org.springframework.boot.SpringApplication` → `run` |
| Выполнение `ApplicationContext.refresh()` | Это основной процесс сборки контекста: обработка конфигурации, создание singleton-бинов, DI, `BeanPostProcessor`, `@PostConstruct`, `InitializingBean`, init-methods и т. п. В ходе этого процесса может быть создан объект `NettyWebServer`, но порт ещё не обязан быть привязан. | `org.springframework.context.support.AbstractApplicationContext` → `refresh` |
| Завершение `refresh()`: `finishRefresh()` → `LifecycleProcessor.onRefresh()` | Здесь Spring запускает lifecycle-компоненты с auto-start. В Spring Boot это приводит к запуску встроенного web server. Следовательно, **старт HTTP-транспорта — это часть завершающей стадии `refresh()`, а не действие после него**. | `org.springframework.context.support.AbstractApplicationContext` → `finishRefresh`; `org.springframework.context.support.DefaultLifecycleProcessor` → `onRefresh` / `startBeans` |
| `org.springframework.boot.reactor.netty.NettyWebServer.start()` | Spring Boot вызывает `reactor.netty.transport.ServerTransport.bindNow()`. **Здесь начинается фактический запуск транспорта**: Reactor Netty создаёт и регистрирует server channel, затем выполняет bind listening socket. | `org.springframework.boot.reactor.netty.NettyWebServer` → `start` или `startHttpServer` |
| Лог `Netty started on port 8083` | Bind завершён: listening socket уже создан, и сервер способен принимать соединения. Низкоуровневый путь регистрации канала, включая `TransportConnector.doInitAndRegister`, к этому моменту уже выполнен; далее для NIO-канала начинается чтение через `AbstractNioChannel.doBeginRead`. | `reactor.netty.transport.TransportConnector` → `doInitAndRegister`; `io.netty.channel.nio.AbstractNioChannel` → `doBeginRead` |
| Лог `Started ReactiveStudyApplication` | `SpringApplication.run()` завершает startup-последовательность. Контекст уже refreshed, сервер уже bound и доступен; после этой точки breakpoint’ы первоначального старта транспорта не сработают до перезапуска процесса. | — |

Основание для **ключевого утверждения**: 
 - `finishRefresh()` завершает `refresh()` и внутри себя вызывает `LifecycleProcessor.onRefresh()`. 
 - Поэтому **lifecycle-запуск** не расположен «после refresh» — он включён в `refresh()`.

- Источник: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/support/AbstractApplicationContext.html

EN:

> “Finish the refresh of this context, invoking the LifecycleProcessor's onRefresh() method and publishing the ContextRefreshedEvent.”

RU:

> «Завершает refresh данного контекста, вызывая метод `onRefresh()` у `LifecycleProcessor` и публикуя `ContextRefreshedEvent`.»

## Что именно означает `refresh()`

`refresh()` — не отдельная фаза, которая наступает **после** инициализации приложения. Это **весь процесс инициализации `ApplicationContext`**.

Внутри `refresh()` происходят, в частности:

- создание и связывание бинов;
- обработка `BeanPostProcessor`;
- обычная инициализация бинов: 
  - `@PostConstruct`, 
  - `afterPropertiesSet()`, 
  - custom init-method;
- пред-создание оставшихся singleton-бинов;
- запуск auto-start lifecycle-компонентов в завершающей части `finishRefresh()`.

Spring определяет контекст как инициализированный/обновлённый, когда **beans** загружены, post-processor-**beans** обнаружены и активированы, singleton-**beans** пред-созданы, а сам `ApplicationContext` готов к использованию.

- Источник: https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html

EN:

> “Here, ‘initialized’ means that all beans are loaded, post-processor beans are detected and activated, singletons are pre-instantiated, and the ApplicationContext object is ready for use.”

RU:

> «Здесь “инициализированный” означает, что все бины загружены, post-processor-бины обнаружены и активированы, singleton-бины предсозданы, а объект `ApplicationContext` готов к использованию.»

Также API Spring прямо указывает, что `finishBeanFactoryInitialization()` завершает инициализацию фабрики **beans**, создавая все оставшиеся singleton-**beans**.

- Источник: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/support/AbstractApplicationContext.html

EN:

> “Finish the initialization of this context's bean factory, initializing all remaining singleton beans.”

RU:

> «Завершает инициализацию фабрики **beans** данного контекста, инициализируя все оставшиеся singleton-бины.»

## `Bean lifecycle` и `Lifecycle` — разные вещи

Здесь смешивались два разных механизма.


| Механизм | Что описывает | Отношение к Netty transport |
| :-- | :-- | :-- |
| **Bean lifecycle** | Создание конкретного бина: constructor, DI, `BeanPostProcessor`, `@PostConstruct`, `InitializingBean`, init-method | Может создать и настроить `NettyWebServer` как объект, но сам по себе не обязан открыть порт |
| **Context lifecycle** (`Lifecycle` / `SmartLifecycle`) | Запуск и остановку уже созданных компонентов в составе контекста | Именно этот механизм во время `finishRefresh()` вызывает запуск web server и, как следствие, `NettyWebServer.start()` |

**Важно**:  
 - `ApplicationContext.start()` и lifecycle-запуск при `refresh()` — не одно и то же API-событие.

- Явный `context.start()` посылает сигнал всем `Lifecycle`-**beans**. 
- При завершении `refresh()` вызывается `LifecycleProcessor.onRefresh()`, который запускает auto-start компоненты. 
  - `ContextStartedEvent` относится именно к явному вызову `start()`, а 
  - `ContextRefreshedEvent` — к инициализации или обновлению контекста.

- Источник: https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html

EN:

> “ContextStartedEvent: Published when the ApplicationContext is started by using the start() method on the ConfigurableApplicationContext interface. Here, ‘started’ means that all Lifecycle beans receive an explicit start signal.”

RU:

> «`ContextStartedEvent` публикуется, когда `ApplicationContext` запускается методом `start()` интерфейса `ConfigurableApplicationContext`. Здесь “запущен” означает, что все `Lifecycle`-бины получают явный сигнал запуска.»

EN:

> “ContextRefreshedEvent: Published when the ApplicationContext is initialized or refreshed.”

RU:

> «`ContextRefreshedEvent` публикуется, когда `ApplicationContext` инициализирован или обновлён.»

Итоговая причинно-следственная цепочка такая:

```text
main()
  → SpringApplication.run()
    → applicationContext.refresh()
      → создание и инициализация бинов
      → finishRefresh()
        → LifecycleProcessor.onRefresh()
          → WebServer lifecycle start
            → NettyWebServer.start()
              → ServerTransport.bindNow()
                → порт начинает слушаться
```

То есть корректная формулировка: **транспорт запускается не «до refresh» и не «после refresh», а во время завершающей lifecycle-стадии самого `refresh()`.**

---

**Короткий ответ:** 
 - **boss-** и **worker**-группы создаются **внутри** `ServerTransport.bind()` → `TransportConnector.bind()`, через `DefaultLoopResources`. **Worker запрашивается раньше** (для `Acceptor`), **boss** — при регистрации server Channel. При дефолтной конфигурации Spring (`selectCount = -1`) это **один и тот же** `EventLoopGroup`.


> **`bind()` сначала запрашивает worker EventLoopGroup (`childEventLoopGroup` → `onServer`),
> 
> затем в `TransportConnector.bind` — boss (`eventLoopGroup` → `onServerSelect`), создаёт server Channel и bind порта;
> 
> при дефолте Spring boss и worker — один `MultiThreadIoEventLoopGroup`.**


## Порядок вызовов


```text

NettyWebServer#start
NettyWebServer#startHttpServer
ServerTransport#bindNow
HttpServerBind#bind
ServerTransport#bind
ServerTransportConfig#childEventLoopGroup      ← worker (1-й запрос группы)
DefaultLoopResources#onServer
TransportConnector#bind
ServerTransportConfig#eventLoopGroup           ← boss / acceptor
DefaultLoopResources#onServerSelect
DefaultLoopResources#cacheNioSelectLoops
TransportConnector#doInitAndRegister           ← server Channel + register
AbstractNioChannel#doBeginRead                 ← OP_ACCEPT
```

---
## Цепочка вызовов (как в коде)

---

### 1. Spring — старт embedded-сервера

`org.springframework.boot.reactor.netty.NettyWebServer`

```java
public void start() throws WebServerException {
    DisposableServer disposableServer = this.disposableServer;
    if (disposableServer == null) {
        disposableServer = startHttpServer();   // ← сюда
        this.disposableServer = disposableServer;
    }
}
```

→ `startHttpServer()` вызывает `server.bindNow()` (`HttpServer` extends `ServerTransport`).

---

### 2. Reactor Netty — блокирующий bind

`reactor.netty.transport.ServerTransport`

```java
public final DisposableServer bindNow() {
    return bindNow(Duration.ofSeconds(45));
}

public final DisposableServer bindNow(Duration timeout) {
    return Objects.requireNonNull(bind().block(timeout), "aborted");
}
```

→ `bindNow()` — обёртка над **`bind()`** + `Mono.block()`.

---

### 3. Reactor Netty — `bind()` собирает pipeline и запускает connector

`reactor.netty.transport.ServerTransport`

```java
public Mono<? extends DisposableServer> bind() {
    // ...
    Mono<? extends DisposableServer> mono = Mono.create(sink -> {
        // ...
        Acceptor acceptor = new Acceptor(
            config.childEventLoopGroup(),   // ← (A) worker EventLoopGroup — ПЕРВЫЙ запрос
            channelInitializer, ...);
        channelInitializer = new AcceptorInitializer(acceptor);

        TransportConnector.bind(config, channelInitializer, local, isDomainSocket)  // ← (B)
            .subscribe(disposableServer);
    });
    return mono;
}
```

**Что происходит в `(A)`:
 - ** Reactor Netty **сначала** просит **worker**-группу — она нужна объекту `Acceptor` (будет принимать child Channel).

**Что происходит в `(B)`:** 
 - `TransportConnector.bind()` создаёт **server Channel** и привязывает его к **boss/acceptor** EventLoop.

**Runtime-класс** HTTP-сервера: `reactor.netty.http.server.HttpServerBind#bind()` → `super.bind()`.

---

### 4. Откуда берётся worker EventLoopGroup

`reactor.netty.transport.ServerTransportConfig`

```java
final EventLoopGroup childEventLoopGroup() {
    return loopResources().onServer(isPreferNative());
}

protected final EventLoopGroup eventLoopGroup() {
    return loopResources().onServerSelect(isPreferNative());
}
```

| Роль | Метод config | Callback LoopResources | Что создаётся |
|------|--------------|------------------------|---------------|
| **Worker** (child Channel) | `childEventLoopGroup()` | `onServer()` | `DefaultLoopResources.cacheNioServerLoops()` |
| **Boss / acceptor** (listening Channel) | `eventLoopGroup()` | `onServerSelect()` | `DefaultLoopResources.cacheNioSelectLoops()` → при `selectCount=-1` делегирует в `cacheNioServerLoops()` |

**Источник:** https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

**Цитата:**
> `onServer(boolean useNative)` — Callback for server EventLoopGroup creation, this is the EventLoopGroup for the **child channel**.  
> `onServerSelect(boolean useNative)` — Callback for server select EventLoopGroup creation, this is the EventLoopGroup for the **acceptor channel**.

**Перевод:**
> `onServer` — EventLoopGroup для **child channel** (соединения клиентов).  
> `onServerSelect` — EventLoopGroup для **acceptor channel** (listening socket).

---

### 5. Где реально создаётся EventLoopGroup (конструктор)

`reactor.netty.resources.DefaultLoopResources` *(package-private, JAR `reactor-netty-core`)*

**Worker-пул** — `cacheNioServerLoops()`:

```java
EventLoopGroup newEventLoopGroup = new MultiThreadIoEventLoopGroup(
    workerCount,
    threadFactory(this, "nio"),
    NioIoHandler.newFactory());
```

**Boss-пул** (если `selectCount != -1`) — `cacheNioSelectLoops()`:

```java
EventLoopGroup newEventLoopGroup = new MultiThreadIoEventLoopGroup(
    selectCount,
    threadFactory(this, "select-nio"),
    NioIoHandler.newFactory());
```

**Дефолт Spring / HttpResources:** 
 - `LoopResources.create("reactor-http")` → `selectCount = -1` → **boss и worker — один `MultiThreadIoEventLoopGroup`**, потоки `reactor-http-nio-*`. 
 - Agent при этом всё равно логирует **оба** входа: `onServer` (раньше) и `onServerSelect` (позже).

Число worker-потоков: `max(CPU, 4)`.

**Источник:** https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

**Цитата:**
> Default worker thread count, fallback to available processor (but with a minimum value of 4).  
> Default selector thread count, fallback to -1 (no selector thread).

**Перевод:**
> Число worker-потоков по умолчанию — число CPU, но не меньше 4.  
> Число selector-потоков по умолчанию — **-1** (отдельные selector-потоки не создаются; worker-потоки же работают как selector).

---

### 6. TransportConnector — server Channel и boss EventLoop

`reactor.netty.transport.TransportConnector`

```java
public static Mono<Channel> bind(..., ChannelInitializer channelInitializer, ...) {
    return doInitAndRegister(config, channelInitializer, isDomainSocket,
            config.eventLoopGroup().next())   // ← boss: берёт поток из acceptor-группы
        .flatMap(channel -> {
            channel.eventLoop().execute(() -> channel.bind(bindAddress, promise));
            return promise;
        });
}
```

`doInitAndRegister(...)`:

```java
ChannelFactory<?> channelFactory = config.connectionFactory(config.eventLoopGroup(), isDomainSocket);
Channel channel = channelFactory.newChannel();          // ← NioServerSocketChannel
channel.pipeline().addLast(channelInitializer);         // ← Acceptor в pipeline
channel.unsafe().register(eventLoop, monoChannelPromise); // ← register на boss EventLoop
```

| Объект | Класс / метод | Когда |
|--------|---------------|-------|
| **Server Channel** | `TransportConnector.doInitAndRegister` → `channelFactory.newChannel()` | после создания EventLoopGroup |
| **Bind порта 8083** | `channel.bind(bindAddress)` на boss EventLoop | внутри `TransportConnector.bind` |
| **OP_ACCEPT** | `AbstractNioChannel.doBeginRead()` | после `channelActive` server Channel |

---

## Сводка: что создаётся и где

| № | Что | Класс → метод | Момент в цепочке |
|---|-----|---------------|------------------|
| 1 | **Worker EventLoopGroup** *(или общий пул)* | `DefaultLoopResources` → `onServer` → `cacheNioServerLoops` | `ServerTransport.bind` → `childEventLoopGroup()` **до** `TransportConnector.bind` |
| 2 | **Boss EventLoopGroup** *(или тот же пул)* | `DefaultLoopResources` → `onServerSelect` → `cacheNioSelectLoops` | `TransportConnector.bind` → `eventLoopGroup()` |
| 3 | **EventLoop + Selector** | `MultiThreadIoEventLoopGroup` + `NioIoHandler` | в конструкторе группы; Selector — при старте каждого потока (`NioIoHandler.select`) |
| 4 | **Server Channel** | `TransportConnector.doInitAndRegister` | после п.2 |
| 5 | **Listening socket :8083** | `channel.bind(8083)` | на boss EventLoop |
| 6 | **Client Channel** | `ServerTransport.Acceptor.channelRead` | **не при boot** — только после `curl` |


---


`Selector` — это объект Java NIO, который позволяет **одному потоку следить сразу за многими сетевыми соединениями**.

**Мультиплексирование** — это именно такой подход: не выделять поток на каждый сокет, а ждать события от всех сокетов в одном месте и обрабатывать только те, у которых сейчас есть работа.

## Что ждёт `Selector`

Он отслеживает готовность канала к операции:

- `OP_ACCEPT` — сервер готов принять новое соединение.
- `OP_READ` — из соединения можно прочитать байты.
- `OP_WRITE` — в соединение можно записать байты.
- `OP_CONNECT` — неблокирующее подключение завершилось.

`Selector` не читает и не пишет данные. Он только сообщает: **«этот канал сейчас готов к чтению/записи»**.

## Что это в Netty

В NIO-транспорте Netty у каждого `NioEventLoop` есть свой `Selector`.

Event loop работает примерно так:

```text
ждёт события через selector.select()
    ↓
получает готовые каналы
    ↓
читает или записывает данные
    ↓
вызывает обработчики ChannelPipeline
    ↓
снова ждёт события
```

Поэтому один event-loop поток может обслуживать много TCP-соединений без модели «один поток на один socket».

## Главное

`Selector` — это **диспетчер готовности сетевых каналов**.

Он отвечает не на вопрос «пришло ли полное HTTP-сообщение?», а на более низкоуровневый вопрос: **«можно ли сейчас попытаться прочитать или записать байты, не блокируя поток?»**

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> “A multiplexor of SelectableChannel objects.”

RU:

> «Мультиплексор объектов `SelectableChannel`, то есть объект, который позволяет одному механизму отслеживать готовность нескольких каналов.»



---

## Breakpoint — только главное

| Вопрос | Класс (FQCN) | Метод |
|--------|--------------|-------|
| Начался bind? | `reactor.netty.transport.ServerTransport` | `bind` |
| **Создаётся worker-группа?** | `reactor.netty.resources.DefaultLoopResources` | `onServer` |
| **Создаётся boss-группа?** | `reactor.netty.resources.DefaultLoopResources` | `onServerSelect` |
| Конструктор группы (IDE) | `reactor.netty.resources.DefaultLoopResources` | `cacheNioServerLoops` / `cacheNioSelectLoops` |
| Server Channel | `reactor.netty.transport.TransportConnector` | `doInitAndRegister` |
| Порт слушает | `io.netty.channel.nio.AbstractNioChannel` | `doBeginRead` |


---

### Проверка (breakpoint) — §2

| # | Класс | Метод | Что увидеть при перезапуске |
|---|-------|-------|-----------------------------|
| 2a | `org.springframework.boot.reactor.netty.NettyWebServer` | `start()` | Spring входит в поднятие embedded-сервера |
| 2b | `org.springframework.boot.reactor.netty.NettyWebServer` | `startHttpServer()` *(package-private)* | внутри вызов `ServerTransport.bindNow()` |
| 2c | `reactor.netty.transport.ServerTransport` | `bindNow()` *(public)* | блокирующий bind; runtime-класс `reactor.netty.http.server.HttpServerBind`; поток `server` ждёт в `Mono.block()` |

**Lazy init:** 
 - общий пул потоков (`reactor.netty.resources.LoopResources` через `reactor.netty.http.HttpResources`) создаётся **при первом bind**, а не при старте JVM.

**Источник (LoopResources):** https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

**Цитата:**
> An EventLoopGroup selector with associated Channel factories.

**Перевод:**
> Селектор EventLoopGroup с фабриками Channel.

---

### 2.1 HttpServer и bindNow — где искать (не гадать)

**Частая путаница:** в Spring видишь `HttpServer`, в log agent — `ServerTransport#bindNow`, в таблице ниже — `HttpServerBind#bind`. Это **одна цепочка**, не три разных bind.

#### Наследование (кто от кого)

```text
reactor.netty.transport.ServerTransport          ← bindNow() объявлен ЗДЕСЬ
    ↑ extends
reactor.netty.http.server.HttpServer             ← абстрактный; bindNow унаследован
    ↑ extends
reactor.netty.http.server.HttpServerBind         ← runtime-объект при bind (конкретный класс)
```

**Источник:** https://projectreactor.io/docs/netty/release/api/reactor/netty/transport/ServerTransport.html

**Цитата:**
> Direct Known Subclasses: **HttpServer**, TcpServer  
> `public final DisposableServer bindNow()` — Starts the server in a blocking fashion…

**Перевод:**
> Прямые наследники: **HttpServer**, TcpServer.  
> `bindNow()` — блокирующий запуск сервера…

#### Цепочка вызовов (полные FQCN)

```text
org.springframework.boot.reactor.netty.NettyWebServer#start()
  └─ org.springframework.boot.reactor.netty.NettyWebServer#startHttpServer()
       └─ server.bindNow()                    ← в коде Spring переменная типа HttpServer
            └─ reactor.netty.transport.ServerTransport#bindNow()   ← метод объявлен здесь
                 └─ bind() → runtime: reactor.netty.http.server.HttpServerBind#bind()
                      └─ reactor.netty.transport.TransportConnector#bind()
                           └─ … → io.netty.channel.nio.AbstractNioChannel#doBeginRead()
```

**Код Spring Boot 4.0.5** (`NettyWebServer.startHttpServer()`):

```java
HttpServer server = this.httpServer;
// … handle, route, runOn …
return server.bindNow();   // JVM идёт в ServerTransport.bindNow()
```

#### Где лежит каждый класс (JAR)

| Что ищешь | Полный путь класса | Метод | JAR |
|-----------|-------------------|-------|-----|
| Spring стартует сервер | `org.springframework.boot.reactor.netty.NettyWebServer` | `start` | `spring-boot-reactor-netty-4.0.5.jar` |
| Spring доходит до bind | `org.springframework.boot.reactor.netty.NettyWebServer` | `startHttpServer` | тот же |
| **bindNow — ставь breakpoint сюда** | **`reactor.netty.transport.ServerTransport`** | **`bindNow`** | **`reactor-netty-core-1.3.4.jar`** |
| Следующий шаг bind | `reactor.netty.http.server.HttpServerBind` | `bind` | `reactor-netty-http-1.3.4.jar` |

#### Куда смотреть в IDE (без догадок)

| Ошибка | Правильно |
|--------|-----------|
| Ищешь `bindNow` в `HttpServer.java` — **не находишь** | Открой **`ServerTransport.java`** в `reactor-netty-core` (Download Sources) |
| Ставишь breakpoint на `reactor.netty.http.server.HttpServer#bindNow` | Ставь на **`reactor.netty.transport.ServerTransport#bindNow`** |
| Путаешь `HttpServer` и `HttpServerBind` | **`HttpServer`** — тип в Spring; **`HttpServerBind`** — **конкретный** runtime-класс при bind |
| В agent-log `ServerTransport#bindNow`, а в Spring `HttpServer` | Agent пишет **класс, где метод объявлен**; Spring вызывает через переменную `HttpServer` |

**Минимум для проверки:** три breakpoint по порядку — `NettyWebServer#start` → `NettyWebServer#startHttpServer` → **`ServerTransport#bindNow`**.

Подробная таблица всех шагов — [§3.4](#34-все-точки-проверки-block-0).

---

## 3. Хронология: что создаётся и когда

### 3.1 Схема инициализации — функциональные блоки

Ниже — **цветная схема** проверенной chronology: шаги 0–6, что создаётся на каждом этапе.

**Как читать:** сверху вниз — время. Каждый цветной блок = этап init. После последнего блока порт 8083 открыт, но HTTP-запросов ещё не было.

![Block 0 — хронология инициализации транспорта](images/block0-init-chronology.png)

| Цвет блока | Этап | Что **создаётся** | Breakpoint: класс → метод |
|------------|------|-------------------|---------------------------|
| ⏳ серый | До bind | Только Spring-контекст, **нет** listening socket | — |
| 🟢 зелёный | Шаг 1 | `reactor.netty.http.server.HttpServer`, `org.springframework.boot.reactor.netty.NettyWebServer` | `org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory` → `getWebServer` |
| 🔵 синий | Шаг 2 | Запуск bind | `org.springframework.boot.reactor.netty.NettyWebServer` → `start` / `startHttpServer`; `reactor.netty.transport.ServerTransport` → `bindNow` |
| 🟣 фиолетовый | Шаг 3 | Reactor Netty transport | `reactor.netty.http.server.HttpServerBind` → `bind`; `reactor.netty.transport.TransportConnector` → `bind` *(static)* |
| 🟠 оранжевый | Шаг 4 | Boss + Worker EventLoopGroup | `ServerTransportConfig` → `childEventLoopGroup` / `eventLoopGroup`; `DefaultLoopResources` → `onServer` / `onServerSelect` |
| 🔴 розовый | Шаг 5 | Server Channel, порт 8083, OP_ACCEPT | `reactor.netty.transport.TransportConnector` → `doInitAndRegister`; `io.netty.channel.nio.AbstractNioChannel` → `doBeginRead` |
| ✅ бирюзовый | Готово | Транспорт слушает; client Channel **ещё нет** | после `doBeginRead` — цепочка init завершена |

---

### 3.2 Состояние после init — что уже есть и чего нет

Схема **момента «Started ReactiveStudyApplication»** (`com.example.reactivestudy.ReactiveStudyApplication`): объекты на месте, красный блок — что появится только после первого curl.

![Block 0 — состояние транспорта после инициализации](images/block0-init-state-after.png)

---

### 3.3 Лента времени (текст)

```text
[1] org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory.getWebServer()
         → reactor.netty.http.server.HttpServer
         → org.springframework.boot.reactor.netty.NettyWebServer
         → org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
         BP: org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory → getWebServer

[2] org.springframework.boot.reactor.netty.NettyWebServer.start()
         → startHttpServer() → reactor.netty.transport.ServerTransport.bindNow()
         BP: org.springframework.boot.reactor.netty.NettyWebServer → start / startHttpServer
         BP: reactor.netty.transport.ServerTransport → bindNow
         (runtime: reactor.netty.http.server.HttpServerBind)

[3] reactor.netty.http.server.HttpServerBind.bind()
         → reactor.netty.transport.ServerTransport.bind()
         → reactor.netty.transport.TransportConnector.bind()  // static
         BP: reactor.netty.http.server.HttpServerBind → bind
         BP: reactor.netty.transport.TransportConnector → bind

[4] reactor.netty.transport.ServerTransportConfig.eventLoopGroup()
         → reactor.netty.resources.DefaultLoopResources.onServerSelect()  // acceptor
         BP: reactor.netty.transport.ServerTransportConfig → eventLoopGroup
         BP: reactor.netty.resources.DefaultLoopResources → onServerSelect

[4b] reactor.netty.transport.ServerTransportConfig.childEventLoopGroup()
         → reactor.netty.resources.DefaultLoopResources.onServer()  // worker
         BP: ServerTransportConfig → childEventLoopGroup
         BP: DefaultLoopResources → onServer
         (agent: вызывается **до** TransportConnector.bind)

[5] reactor.netty.transport.TransportConnector.doInitAndRegister()  // static, package-private
         → io.netty.channel.socket.nio.NioServerSocketChannel → io.netty.channel.Channel
         → bind(8083) → io.netty.channel.nio.AbstractNioChannel.doBeginRead() (OP_ACCEPT)
         BP: reactor.netty.transport.TransportConnector → doInitAndRegister
         BP: io.netty.channel.nio.AbstractNioChannel → doBeginRead

[6] Лог: "Netty started on port 8083"
```

### Таблица «объект → момент создания → breakpoint»

| Объект | Когда появляется | Уже работает после init? | Breakpoint: класс → метод |
|--------|------------------|---------------------------|---------------------------|
| `reactor.netty.http.server.HttpServer` / `org.springframework.boot.reactor.netty.NettyWebServer` | шаг [1]–[2] | да | `org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory` → `getWebServer`; `org.springframework.boot.reactor.netty.NettyWebServer` → `start` |
| `reactor.netty.resources.LoopResources` (`reactor.netty.http.HttpResources`) | шаг [4], первый bind | да (singleton) | `reactor.netty.http.HttpResources` → `get` *(опционально)* |
| **Boss / acceptor** `io.netty.channel.EventLoopGroup` | шаг [4] | да | `reactor.netty.transport.ServerTransportConfig` → `eventLoopGroup`; `reactor.netty.resources.DefaultLoopResources` → `onServerSelect` |
| **Worker** `io.netty.channel.EventLoopGroup` | шаг [4b], bind | да | `reactor.netty.transport.ServerTransportConfig` → `childEventLoopGroup`; `reactor.netty.resources.DefaultLoopResources` → `onServer` |
| **Selector** у каждого EventLoop | при старте потока | да | `io.netty.channel.nio.NioIoHandler` → `run` *(jstack после старта)* |
| **Server Channel** (`io.netty.channel.Channel`) | шаг [5] | да, `:8083` | `reactor.netty.transport.TransportConnector` → `doInitAndRegister` |
| **Client Channel** | **не при init** | нет до curl | `reactor.netty.transport.ServerTransport.Acceptor` → `channelRead` *(только curl)* |
| HTTP pipeline (`io.netty.handler.codec.http.HttpServerCodec` …) | после accept | нет до curl | `reactor.netty.http.server.HttpTrafficHandler` → `channelRead` *(блок 1+)* |

**Источник (onServerSelect / onServer):** https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

**Цитата:**
> `onServerSelect(boolean useNative)` — Callback for server select EventLoopGroup creation, this is the EventLoopGroup for the **acceptor channel**.
>
> `onServer(boolean useNative)` — Callback for server EventLoopGroup creation, this is the EventLoopGroup for the **child channel**.

**Перевод:**
> `onServerSelect` — callback создания EventLoopGroup для **acceptor channel** (серверный listening socket).
>
> `onServer` — callback создания EventLoopGroup для **child channel** (соединения клиентов).

---

### 3.4 Все точки проверки Block 0

Сводная таблица **в порядке вызова** (подтверждено **InitPathAgent**, 03.08.2026). Ставьте breakpoint **до** строки `Started ReactiveStudyApplication`.

| # | Шаг | Класс (полный путь) | Метод | Видимость | Agent |
|---|-----|---------------------|-------|-----------|-------|
| 1 | Spring — сборка | `org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory` | `getWebServer` | public | ✅ |
| 2 | Spring — старт | `org.springframework.boot.reactor.netty.NettyWebServer` | `start` | public | ✅ |
| 3 | Spring — bind | `org.springframework.boot.reactor.netty.NettyWebServer` | `startHttpServer` | package-private | ✅ |
| 4 | RN — bindNow | `reactor.netty.transport.ServerTransport` | `bindNow` | public | ✅ |
| 5 | RN — HttpServer | `reactor.netty.http.server.HttpServerBind` | `bind` | public | ✅ |
| 6 | RN — transport | `reactor.netty.transport.ServerTransport` | `bind` | public | ✅ |
| 7 | RN — worker config | `reactor.netty.transport.ServerTransportConfig` | `childEventLoopGroup` | final | ✅ |
| 8 | RN — worker pool | `reactor.netty.resources.DefaultLoopResources` | `onServer` | public *(класс package-private)* | ✅ |
| 9 | RN — connector | `reactor.netty.transport.TransportConnector` | `bind` | **public static** | ✅ |
| 10 | RN — boss config | `reactor.netty.transport.ServerTransportConfig` | `eventLoopGroup` | protected final | ✅ |
| 11 | RN — boss pool | `reactor.netty.resources.DefaultLoopResources` | `onServerSelect` | public | ✅ |
| 12 | RN — channel | `reactor.netty.transport.TransportConnector` | `doInitAndRegister` | **static**, package-private | ✅ |
| 13 | Netty — accept | `io.netty.channel.nio.AbstractNioChannel` | `doBeginRead` | protected | ✅ |
| 14 | Netty — selector | `io.netty.channel.nio.AbstractNioChannel` | `addAndSubmit` | private | ✅ |
| — | **не Block 0** | `reactor.netty.transport.ServerTransport$Acceptor` | `channelRead` | public | только **curl** |

**Negative test (не срабатывают при boot):**

| Класс | Метод | Почему |
|-------|-------|--------|
| `reactor.netty.http.server.HttpServer` | `bindNow` | **не ищи здесь** — метод **унаследован** от `reactor.netty.transport.ServerTransport`; breakpoint — на `ServerTransport#bindNow` ([§2.1](#21-httpserver-и-bindnow--где-искать-не-гадать)) |
| `io.netty.bootstrap.ServerBootstrap` | `doBind` | метода нет; RN не использует ServerBootstrap |
| `io.netty.bootstrap.AbstractBootstrap` | `doBind` | private; RN не вызывает |

**Запуск breakpoint (CMD / IntelliJ):**

```bat

cd D:\Project_infra\greeting-service-infra\reactive-study
gradlew.bat bootRun --args="--spring.profiles.active=local"
```

**Запуск InitPathAgent (автоматический лог `>>> ENTER class#method`):**

```bat

cd D:\Project_infra\greeting-service-infra\reactive-study\docs\block0-verify
run-with-agent.cmd
```

Перед запуском освободите порт **8083** (остановите предыдущий `reactive-study`). Лог: `docs/block0-verify/agent/block0-init-trace.log`.

Remote debug: `gradlew.bat bootRun --debug-jvm --args="--spring.profiles.active=local"` → порт `:5005`.

---

## 4. Boss и Worker — что это у вас на самом деле

### Концепция (как в учебниках)

| Роль | Задача | Какой socket / Channel |
|------|--------|-------------------------|
| **Boss (acceptor)** | `accept()` новых TCP | один **listening** socket → server `io.netty.channel.Channel` |
| **Worker** | `read()` / `write()` данных клиента | отдельный **connected** socket → child `io.netty.channel.Channel` на каждого клиента |

### Реализация в вашем стеке (RN 1.3.4 + Netty 4.2)

| Учебник Netty 4.1 | У вас в runtime |
|-------------------|-----------------|
| `io.netty.channel.nio.NioEventLoopGroup` (boss + worker) | `io.netty.channel.MultiThreadIoEventLoopGroup` + `io.netty.channel.nio.NioIoHandler` |
| `io.netty.bootstrap.ServerBootstrap.group(boss, worker)` | `reactor.netty.transport.TransportConnector` + `reactor.netty.transport.ServerTransport.Acceptor` |
| поток `io.netty.channel.nio.NioEventLoop.run()` | поток `io.netty.channel.nio.NioIoHandler.run()` на `io.netty.channel.SingleThreadIoEventLoop` |

**Модель boss/worker — верная.** Меняются **имена классов** и **код, который их создаёт** (не `io.netty.bootstrap.ServerBootstrap`, а Reactor Netty `reactor.netty.transport.TransportConnector`).

### Проверка (breakpoint) — §4

| Роль | Класс (полный путь) | Метод | Когда сработает |
|------|---------------------|-------|-----------------|
| Boss / acceptor | `reactor.netty.transport.ServerTransportConfig` | `eventLoopGroup` | при bind — запрос acceptor-группы |
| Boss / acceptor | `reactor.netty.resources.DefaultLoopResources` | `onServerSelect` | при bind — создаётся acceptor EventLoopGroup |
| Worker | `reactor.netty.transport.ServerTransportConfig` | `childEventLoopGroup` | при bind — worker EventLoopGroup |
| Worker | `reactor.netty.resources.DefaultLoopResources` | `onServer` | при bind — agent ✅ |
| Event loop (Netty 4.2) | `io.netty.channel.nio.NioIoHandler` | `run` | после старта — в jstack поток `reactor-http-nio-*` |
| Accept child *(не init)* | `reactor.netty.transport.ServerTransport.Acceptor` | `channelRead` | **только первый curl** — передача на worker |

После init в jstack видно, например:

- поток **`reactor-http-nio-1`** — acceptor event loop в `io.netty.channel.nio.NioIoHandler.select()` (ждёт `OP_ACCEPT`);
- поток **`server`** — Spring временно блокируется на `reactor.core.publisher.Mono.block()` до завершения bind (это не boss и не worker).

*(В jstack сразу после старта виден acceptor `reactor-http-nio-1`; worker-потоки могут не отображаться отдельно, пока нет client Channel.)*

---

## 5. Server socket и Channel — в какой момент

**Последовательность:**

1. **До bind** — объекта `io.netty.channel.Channel` для порта 8083 **нет**.
2. **`reactor.netty.transport.TransportConnector.doInitAndRegister`** — Netty создаёт `io.netty.channel.socket.nio.NioServerSocketChannel` (JDK NIO socket).
3. **Тот же шаг** — socket оборачивается в Netty **`io.netty.channel.Channel`** (server / parent channel).
4. **Register** — Channel привязывается к одному потоку **acceptor EventLoopGroup** (`reactor.netty.resources.DefaultLoopResources.onServerSelect()`).
5. **`channel.bind(8083)`** — ОС открывает порт; в логе Spring: `Netty started on port 8083`.
6. **`io.netty.channel.nio.AbstractNioChannel.doBeginRead()` / `addAndSubmit(OP_ACCEPT)`** — event loop начинает ждать **новые** TCP-подключения.

### Проверка (breakpoint) — §5

| # | Класс (полный путь) | Метод | Что увидеть |
|---|---------------------|-------|-------------|
| 1 | `reactor.netty.transport.TransportConnector` | `doInitAndRegister` | создание `io.netty.channel.socket.nio.NioServerSocketChannel`, register на acceptor |
| 2 | `io.netty.channel.AbstractChannel` | `bind` | привязка к `:8083` *(можно на server Channel)* |
| 3 | `io.netty.channel.nio.AbstractNioChannel` | `doBeginRead` | включение `OP_ACCEPT` — «жду connect» |

**Аналогия:** сначала строят кассу (server Channel + порт), потом включают табло «принимаем клиентов». Сами клиенты в очередь встанут только после первого `curl`.

**Источник (Channel):** https://netty.io/4.1/api/io/netty/channel/Channel.html

**Цитата:**
> A nexus to a network socket or a component which is capable of I/O operations such as read, write, connect, and bind.

**Перевод:**
> Связующее звено с сетевым сокетом или компонентом, способным выполнять операции I/O: read, write, connect, bind.

---

## 6. EventLoop и Selector — в какой момент

### EventLoopGroup (boss + worker)

Создаются **во время bind**:

- `ServerTransportConfig.childEventLoopGroup()` → `DefaultLoopResources.onServer()` — worker *(agent: до `TransportConnector.bind`)*
- `ServerTransportConfig.eventLoopGroup()` → `DefaultLoopResources.onServerSelect()` — acceptor

Число worker-потоков по умолчанию — **число CPU, но не меньше 4**.

**Источник:** https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

**Цитата:**
> Default worker thread count, fallback to available processor (but with a minimum value of 4).

**Перевод:**
> Число worker-потоков по умолчанию: доступные процессоры, но не меньше 4.

### Selector

**Selector не создаётся отдельно «до» EventLoop.**  
У каждого потока EventLoop при старте поднимается свой Selector (в Netty 4.2 — внутри `io.netty.channel.nio.NioIoHandler`).

| Момент | Selector |
|--------|----------|
| После init, до curl | На acceptor-потоке Selector следит **только за server Channel** (готовность к `accept`) |
| Worker Selectors | Уже **существуют** (потоки запущены), но **без клиентских Channel** в наборе ключей |
| После первого curl | Boss принимает socket → создаётся child Channel → регистрируется на **worker** Selector |

**Источник (Netty EventLoop):** https://netty.io/4.1/api/io/netty/channel/nio/NioEventLoop.html

**Цитата:**
> SingleThreadEventLoop implementation which register the Channel's to a Selector and so does the multi-plexing of these in the event loop.

**Перевод:**
> Однопоточная реализация EventLoop, регистрирующая Channel в Selector и мультиплексирующая их в event loop.

*(В Netty 4.2 та же роль у `io.netty.channel.SingleThreadIoEventLoop` + `io.netty.channel.nio.NioIoHandler` — проверено jstack.)*

### Проверка (breakpoint) — §6

| Что проверяем | Класс (полный путь) | Метод | Примечание |
|---------------|---------------------|-------|------------|
| Boss-группа | `reactor.netty.resources.DefaultLoopResources` | `onServerSelect` | срабатывает **один раз** при bind |
| Worker-группа | `reactor.netty.resources.DefaultLoopResources` | `onServer` | срабатывает при bind |
| Selector в работе | `io.netty.channel.nio.NioIoHandler` | `select` или `run(int)` | после старта — pause в jstack |
| Singleton пула | `reactor.netty.http.HttpResources` | `get` | *(опционально)* откуда берётся `LoopResources` |

---

## 7. Что готово после «Started ReactiveStudyApplication»

Когда `com.example.reactivestudy.ReactiveStudyApplication` полностью поднялось, **транспортный слой в состоянии «жду клиентов»**:

```text
┌─────────────────────────────────────────────────────────┐
│  Spring WebFlux / org.springframework.boot.reactor.netty.NettyWebServer │
│    reactor.netty.http.server.HttpServer настроен, handler подключён   │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│  reactor.netty.resources.LoopResources                  │
│    (синглтон reactor.netty.http.HttpResources)          │
│    • acceptor io.netty.channel.EventLoopGroup (boss)    │
│    • worker io.netty.channel.EventLoopGroup             │
└──────────────────────────┬──────────────────────────────┘
                           │
        ┌──────────────────┴──────────────────┐
        │  Acceptor EventLoop (1+ поток)       │
        │    Selector → server Channel :8083   │
        │    режим OP_ACCEPT                   │
        └──────────────────────────────────────┘

        ┌──────────────────────────────────────┐
        │  Worker EventLoop pool (N потоков)     │
        │    Selectors готовы, client Channel'ов │
        │    пока нет                              │
        └──────────────────────────────────────┘
```

**Checklist «что уже есть»:**

- [x] Порт **8083** слушает ОС (listening socket)
- [x] Netty **server Channel** (`io.netty.channel.Channel`) зарегистрирован на acceptor EventLoop
- [x] **Boss-** и **worker-** EventLoopGroup созданы (agent: `onServerSelect` + `onServer`)
- [x] У каждого потока есть **Selector** (event loop крутится)
- [ ] **Нет** ни одного client Channel (`io.netty.channel.Channel`)
- [ ] **Нет** HTTP-запроса, pipeline codec на соединении не работал

### Проверка (breakpoint) — §7

После лога `Started ReactiveStudyApplication` **точки §3.4 (#1–#10) больше не вызываются** до перезапуска. Для перепроверки состояния «всё готово, но curl ещё не было»:

| Что проверить | Как |
|---------------|-----|
| Порт 8083 слушает | `netstat -ano \| findstr :8083` или лог `Netty started on port 8083` |
| Event loop крутится | jstack: поток `reactor-http-nio-1` в `io.netty.channel.nio.NioIoHandler.run` |
| Init-цепочка прошла | при **следующем** перезапуске с breakpoint — снова #1→#10 из [§3.4](#34-все-точки-проверки-block-0) |

---

## 8. Чего ещё нет до первого curl

Это **не** часть Block 0, но снимает типичную путаницу:

| Событие | Когда | Breakpoint: класс → метод |
|---------|--------|---------------------------|
| `accept()` → новый `io.netty.channel.Channel` (client) | **Первый TCP** (curl / браузер) | `reactor.netty.transport.ServerTransport.Acceptor` → `channelRead` |
| Регистрация child Channel на worker | там же | внутри `channelRead` / register на worker EventLoop |
| `io.netty.handler.codec.http.HttpServerCodec`, `reactor.netty.http.server.HttpTrafficHandler`, WebFlux | на **child** Channel, после accept | `reactor.netty.http.server.HttpTrafficHandler` → `channelRead` |
| Чтение HTTP-байтов, `org.springframework.web.reactive.DispatcherHandler` | блок 1+ (путь запроса) | `org.springframework.web.reactive.DispatcherHandler` → `handle` |

### Проверка (breakpoint) — §8

| # | Класс (полный путь) | Метод | Когда |
|---|---------------------|-------|-------|
| 1 | `reactor.netty.transport.ServerTransport.Acceptor` | `channelRead` | первый TCP — **не** при boot |
| 2 | `reactor.netty.http.server.HttpTrafficHandler` | `channelRead` | первый HTTP-запрос (блок 1+) |
| 3 | `org.springframework.web.reactive.DispatcherHandler` | `handle` | маршрутизация WebFlux (блок 1+) |

**Итог:** init = «открыли дверь и сели ждать». Первый запрос = «клиент постучался, boss передал соединение worker».

---

## 9. Расхождение с «классическим Netty» из интернета

Много статей показывают:

```java
ServerBootstrap b = new io.netty.bootstrap.ServerBootstrap();
b.group(bossGroup, workerGroup)...
```

**В Reactor Netty 1.3.4 этот путь при bind не используется** — вместо него `reactor.netty.transport.TransportConnector`. 
 - Поэтому breakpoint на `io.netty.bootstrap.AbstractBootstrap.doBind()` **не срабатывает**, хотя **идея boss/worker остаётся**.

| Из интернета | В вашем приложении | Breakpoint для проверки |
|--------------|-------------------|-------------------------|
| Boss + Worker | ✅ да | `onServerSelect` + `onServer` — **agent ✅** |
| Server socket → Channel | ✅ да | `reactor.netty.transport.TransportConnector` → `doInitAndRegister` — **срабатывает** |
| Selector на EventLoop | ✅ да | jstack: `io.netty.channel.nio.NioIoHandler` → `run` |
| `io.netty.bootstrap.ServerBootstrap` | ❌ не в цепочке init | `io.netty.bootstrap.ServerBootstrap` → `doBind` — **не срабатывает** |
| `io.netty.channel.nio.NioEventLoopGroup` | ❌ заменён Netty 4.2 | конструктор `NioEventLoopGroup` — **не срабатывает** |
| `io.netty.bootstrap.AbstractBootstrap` | ❌ не в цепочке | `doBind` — **не срабатывает** |

### Проверка (breakpoint) — §9

| Ожидание | Класс (полный путь) | Метод | Результат при boot |
|----------|---------------------|-------|---------------------|
| Старый путь Netty | `io.netty.bootstrap.ServerBootstrap` | `doBind` | ❌ breakpoint не попадает |
| Старый путь Netty | `io.netty.bootstrap.AbstractBootstrap` | `doBind` | ❌ breakpoint не попадает |
| Старый boss/worker | `io.netty.channel.nio.NioEventLoopGroup` | `<init>` | ❌ breakpoint не попадает |
| Ваш реальный путь | `reactor.netty.transport.TransportConnector` | `bind` *(static)* | ✅ agent |

---

## Приложение A — breakpoint (краткая выжимка)

Полная таблица с порядком вызова и agent-логом — [§3.4](#34-все-точки-проверки-block-0).

| # | Класс (полный путь) | Метод | Block 0? |
|---|---------------------|-------|----------|
| 1 | `org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory` | `getWebServer` | да |
| 2 | `org.springframework.boot.reactor.netty.NettyWebServer` | `start` / `startHttpServer` | да |
| 3 | `reactor.netty.transport.ServerTransport` | `bindNow` | да |
| 4 | `reactor.netty.http.server.HttpServerBind` | `bind` | да |
| 5 | `reactor.netty.transport.ServerTransport` | `bind` | да |
| 6 | `reactor.netty.transport.ServerTransportConfig` | `childEventLoopGroup` | да |
| 7 | `reactor.netty.resources.DefaultLoopResources` | `onServer` | да |
| 8 | `reactor.netty.transport.TransportConnector` | `bind` *(static)* | да |
| 9 | `reactor.netty.transport.ServerTransportConfig` | `eventLoopGroup` | да |
| 10 | `reactor.netty.resources.DefaultLoopResources` | `onServerSelect` | да |
| 11 | `reactor.netty.transport.TransportConnector` | `doInitAndRegister` | да |
| 12 | `io.netty.channel.nio.AbstractNioChannel` | `doBeginRead` | да |
| 13 | `reactor.netty.transport.ServerTransport$Acceptor` | `channelRead` | **нет** — только curl |

Запуск — см. [§3.4](#34-все-точки-проверки-block-0).

---

## Приложение B — как проверялось

| Факт | Подтверждение |
|------|----------------|
| Сигнатуры методов | `docs/block0-verify/javap-verified.txt` (`javap_verify.py`) |
| Порядок вызовов при init | `docs/block0-verify/agent/block0-init-trace.log` (InitPathAgent) |
| Bind на 8083 | лог `Netty started on port 8083` |
| Spring ждёт bind | jstack: `NettyWebServer$1.run` → `Mono.block` |
| Acceptor event loop | jstack PID 17212: `reactor-http-nio-1` → `NioIoHandler.run` → `select` |
| Нет `ServerBootstrap` | javap: `ServerBootstrap#doBind` NOT FOUND; agent не логирует |
| Цепочка bind | agent: `getWebServer` → `start` → `bindNow` → `HttpServerBind.bind` → `childEventLoopGroup`/`onServer` → `TransportConnector.bind` → `onServerSelect` → `doInitAndRegister` → `doBeginRead` |

**InitPathAgent:** `docs/block0-verify/agent/InitPathAgent.java` — ASM bytecode agent, логирует `>>> ENTER class#method` + stack trace. Сборка: `build_agent.py` или `build-agent.cmd`. Запуск: `run-with-agent.cmd`.

**Версии:** Spring Boot 4.0.5, RN 1.3.4, Netty 4.2.12, Java 21.0.11.

---

## Одним абзацем

При инициализации Spring **один раз** вызывает `reactor.netty.transport.ServerTransport.bindNow()` (runtime: `HttpServerBind`): Reactor Netty создаёт **acceptor и worker EventLoopGroup**, **server Channel** на порту 8083 и включает **`OP_ACCEPT`** (`doBeginRead`). Клиентских Channel до первого `curl` нет.
