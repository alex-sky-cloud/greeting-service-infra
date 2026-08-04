# Spring bean lifecycle и запуск Netty transport

**Короткий ответ:** если перечисляют фазы жизни **одного bean** (создание → DI → `@PostConstruct` → …), то **Netty transport туда не попадает**. Реальный bind порта 8083 происходит **позже** — когда Spring уже поднял все singleton-beans и вызывает **`SmartLifecycle.start()`** (в Boot это `WebServerStartStopLifecycle` → `NettyWebServer.start()`).

Эталон по той же теме (не дублируем): [`К какой фазе Spring bean Life - отнести запуск Netty.md`](К%20какой%20фазе%20Spring%20bean%20Life%20-%20отнести%20запуск%20Netty.md).

Доказательство из нашего приложения: `block0-verify/agent/block0-init-trace.log`, `boot-with-agent.log`.

---

## Оглавление

- [1. Для кого этот текст](#1-для-кого-этот-текст)
- [2. Две разные «линейки» — не путать](#2-две-разные-линейки--не-путать)
- [3. Фазы, как на уроке (таблица)](#3-фазы-как-на-уроке-таблица)
- [4. Два действия с Netty — не одно и то же](#4-два-действия-с-netty--не-одно-и-то-же)
  - [4.1 HttpServer и bindNow — как связаны (куда смотреть)](#41-httpserver-и-bindnow--как-связаны-куда-смотреть)
- [5. Как это видно в log (доказательство)](#5-как-это-видно-в-log-доказательство)
- [6. Одна фраза, чтобы запомнить](#6-одна-фраза-чтобы-запомнить)

---

## 1. Для кого этот текст

Представь: ты учишь Spring и спрашиваешь — *«Netty же bean? Значит, transport поднимается на `@PostConstruct`?»*

**Нет.** Embedded-сервер в Spring Boot устроен иначе:

- объект сервера **готовят** заранее;
- **слушать порт** начинают только на отдельной стадии — **lifecycle start**.

Ниже — та же мысль, что в эталоне, но разложена «от простого к точному».

---

## 2. Две разные «линейки» — не путать

| Линейка | О чём | Пример |
|---------|--------|--------|
| **A. Жизнь одного bean** | создали → влили зависимости → init-callbacks | Flyway, `@Service`, `@Controller` |
| **B. Запуск приложения** | все beans готовы → Spring **стартует** infrastructure | **Netty bind на 8083** |

Netty transport относится к **линейке B**, фаза **`SmartLifecycle.start()`**.

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

**Цитата:**
> When the context is refreshed (after all objects have been instantiated and initialized), that callback is invoked. At that point … If `true`, that object is **started** at that point.

**Перевод:**
> Когда контекст завершил подготовку и все объекты созданы и инициализированы, для `SmartLifecycle` с `isAutoStartup() == true` вызывается **start**.

---

## 3. Фазы, как на уроке (таблица)

Упрощённая шкала — **удобно отвечать на вопрос «на какой фазе Netty?»**

| № | Фаза | Простыми словами | Netty transport |
|---|------|------------------|-----------------|
| 1 | Создание bean | Spring вызвал конструктор | **Нет** |
| 2 | DI | Подставили `@Autowired`, `@Value`, properties | **Нет** |
| 3 | Init bean | `@PostConstruct` → `afterPropertiesSet()` → `init-method` | **Нет** |
| 4 | Все singleton готовы | Обычные beans инициализированы (Flyway уже отработал) | **Ещё нет bind** |
| 5 | **Lifecycle start** | Spring вызывает **`SmartLifecycle.start()`** | **Да — здесь bind, event loop, порт 8083** |
| 6 | Работа | Можно слать `curl` | Да |
| 7 | Остановка | `Lifecycle.stop()`, потом destroy beans | Transport закрывается |

**Где ты мог ошибиться:** искать Netty в **строках 1–3**. Там только init **отдельных** beans. Transport — **строка 5**.

---

## 4. Два действия с Netty — не одно и то же

Частая путаница: «transport уже создали в `getWebServer` — значит, порт открыт?»

| Действие | Что это | Порт 8083 слушает? |
|----------|---------|-------------------|
| `NettyReactiveWebServerFactory.getWebServer()` | Собрали **`NettyWebServer`** (paused) | **Нет** |
| `NettyWebServer.start()` → `startHttpServer()` → **`ServerTransport.bindNow()`** | **Запуск** transport (см. [§4.1](#41-httpserver-и-bindnow--как-связаны-куда-смотреть)) | **Да** |

**Источник:** https://docs.spring.io/spring-boot/4.1.0/api/java/org/springframework/boot/web/server/reactive/ReactiveWebServerFactory.html

**Цитата:**
> Clients should not be able to connect to the returned server until **`WebServer.start()`** is called.

**Перевод:**
> Клиент не должен иметь возможности подключиться, пока не вызван **`WebServer.start()`**.

- **Подготовка** (`getWebServer`) — раньше, в hook контекста `onRefresh()`; это **не** фазы 1–3 из таблицы §3.
- **Запуск transport** — **фаза 5** (`SmartLifecycle.start()`).

---

### 4.1 HttpServer и bindNow — как связаны (куда смотреть)

Если видишь в таблице выше `bindNow()` и не понимаешь, при чём тут `HttpServer` — это **не три разных класса**, а **одна цепочка**.

#### Наследование

```text
reactor.netty.transport.ServerTransport          ← bindNow() объявлен ЗДЕСЬ
    ↑ extends
reactor.netty.http.server.HttpServer             ← Spring хранит server с этим типом
    ↑ extends (runtime)
reactor.netty.http.server.HttpServerBind         ← конкретный объект при bind
```

**Источник:** https://projectreactor.io/docs/netty/release/api/reactor/netty/transport/ServerTransport.html

**Цитата:**
> Direct Known Subclasses: **HttpServer**, TcpServer  
> `public final DisposableServer bindNow()`

**Перевод:**
> Прямые наследники: **HttpServer**, TcpServer. Метод `bindNow()` объявлен в `ServerTransport`.

#### Полная цепочка от lifecycle до bind

```text
WebServerStartStopLifecycle.start()                    ← фаза 5 (SmartLifecycle)
  └─ org.springframework.boot.reactor.netty.NettyWebServer#start()
       └─ org.springframework.boot.reactor.netty.NettyWebServer#startHttpServer()
            └─ server.bindNow()                         ← в Spring: переменная HttpServer
                 └─ reactor.netty.transport.ServerTransport#bindNow()   ← метод здесь
                      └─ reactor.netty.http.server.HttpServerBind#bind()
```

Spring Boot вызывает `server.bindNow()` на объекте типа `HttpServer`; JVM выполняет **`ServerTransport.bindNow()`**, потому что `HttpServer extends ServerTransport`.

#### Куда ставить breakpoint (чтобы не блуждать)

| Вопрос | Класс (полный путь) | Метод | JAR |
|--------|---------------------|-------|-----|
| Spring начал поднимать сервер? | `org.springframework.boot.reactor.netty.NettyWebServer` | `start` | `spring-boot-reactor-netty-4.0.5.jar` |
| Spring дошёл до bind? | `org.springframework.boot.reactor.netty.NettyWebServer` | `startHttpServer` | тот же |
| **Reactor Netty открыл порт?** | **`reactor.netty.transport.ServerTransport`** | **`bindNow`** | **`reactor-netty-core-1.3.4.jar`** |

**Не ищи `bindNow` в `HttpServer.java`** — там его нет. Открой **Sources** для `reactor-netty-core` → `ServerTransport.java`.

Подробнее по всей init-цепочке: [`BLOCK-0-INIT-PATH-VERIFICATION.md`](BLOCK-0-INIT-PATH-VERIFICATION.md) → [§2.1](BLOCK-0-INIT-PATH-VERIFICATION.md#21-httpserver-и-bindnow--где-искать-не-гадать).

---

## 5. Как это видно в log (доказательство)

Модуль `reactive-study`, Spring Boot 4.0.5, profile `local`.

### 5.1 Boot-лог — порядок по времени

Файл: `docs/block0-verify/agent/boot-with-agent.log`

```text
Flyway ... Schema is up to date     ← фазы 1–3 у beans (в т.ч. Flyway)
Netty started on port 8083          ← фаза 5 (lifecycle start)
Started ReactiveStudyApplication
```

**Вывод ученика:** Flyway успел init **до** bind. Значит bind — **не** `@PostConstruct`, а **после** init beans.

### 5.2 Trace — bind идёт через SmartLifecycle

Файл: `docs/block0-verify/agent/block0-init-trace.log`

```text
>>> ENTER NettyWebServer#start
    at WebServerStartStopLifecycle.start(...)
    at DefaultLifecycleProcessor.onRefresh(...)
```

**Вывод:** `start()` вызван из **lifecycle**, не из init-callback bean.

### 5.3 Trace — getWebServer без bind

```text
>>> ENTER NettyReactiveWebServerFactory#getWebServer
    at ReactiveWebServerApplicationContext.onRefresh(...)
```

В stack **нет** `@PostConstruct` — это **подготовка** сервера, не фаза 3.

### 5.4 Breakpoint (если проверяешь в IDE)

| Вопрос | Класс (полный путь) | Метод | Фаза из §3 |
|--------|---------------------|-------|------------|
| Где собрали WebServer? | `org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory` | `getWebServer` | до фазы 5 (paused) |
| Lifecycle вызвал start? | `org.springframework.boot.reactor.netty.NettyWebServer` | `start` | **5** |
| Spring дошёл до bind? | `org.springframework.boot.reactor.netty.NettyWebServer` | `startHttpServer` | **5** |
| **Reactor Netty bind (не HttpServer!)** | **`reactor.netty.transport.ServerTransport`** | **`bindNow`** | **5** — см. [§4.1](#41-httpserver-и-bindnow--как-связаны-куда-смотреть) |

Agent-log пишет `>>> ENTER reactor.netty.transport.ServerTransport#bindNow` — это **нормально**: метод объявлен в `ServerTransport`, хотя Spring вызывает его через `HttpServer`.

---

## 6. Одна фраза, чтобы запомнить

> **Init bean (фазы 1–3) — это про отдельные `@Service` и Flyway. Netty transport — фаза 5: `SmartLifecycle.start()` → `NettyWebServer.start()` → `startHttpServer()` → `reactor.netty.transport.ServerTransport.bindNow()` (не ищи bindNow в HttpServer).**

Если на экзамене просят «lifecycle **bean**» — ответ: **не init-callback; startup lifecycle после полной инициализации beans.**
