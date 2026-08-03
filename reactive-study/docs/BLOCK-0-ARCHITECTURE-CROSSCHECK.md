# Block 0 — сверка архитектурных документов с runtime-проверкой

**Дата:** 03.08.2026  
**Область:** только **инициализация транспорта** при старте (`reactive-study`, Boot 4.0.5, RN 1.3.4, Netty 4.2.12).  
**Не проверялось:** путь HTTP-запроса после первого `curl` (блоки 1+).

**Базовый отчёт проверки:** `BLOCK-0-INIT-PATH-VERIFICATION.md`

---

## Кратко: что было сделано

| Что | Как |
|-----|-----|
| Запуск приложения | Windows **CMD**: `gradlew.bat bootRun --args="--spring.profiles.active=local"` |
| Подтверждение bind | Лог: `Netty started on port 8083`, `Started ReactiveStudyApplication` |
| Runtime-стек | **jstack** после старта (потоки `server`, `reactor-http-nio-1`) |
| Путь в коде | Ваши breakpoint в IDE + цепочка в `BLOCK-0-INIT-PATH-VERIFICATION.md` |

**Честно:** полный пошаговый debug всех 8 методов **в этом сеансе не проходился** — bind подтверждён логом + jstack; детальная цепочка `getWebServer → bind → onServerSelect → doBeginRead` подтверждена **вашими breakpoint** и согласована с runtime (jstack не показывает `ServerBootstrap`).

---

## Сверка с вашими документами (только Block 0)

### 1. `docs/interview/reactive/13 - Путь HTTP-запроса...md`

| Утверждение в документе | Block 0 |
|-------------------------|---------|
| Пункт 1: «Netty EventLoop (worker group) — Selector сообщил, данные готовы» | **Вне scope Block 0.** Это уже **первый HTTP-запрос**, не старт сервера. |
| Схема EventLoop + ChannelPipeline + HttpServerOperations | **Не про инициализацию.** Проверим позже (блок 1+). |

**Верdict:** для Block 0 документ **нейтрален** — не описывает создание транспорта, **не противоречит** проверенному пути, но **не годится** как справочник по init.

---

### 2. `docs/interview/reactive/7 - Event Loop, Selector...md` (раздел «Краткий алгоритм», п. 1–5)

| Утверждение | Block 0 |
|-------------|---------|
| п.1: старт → `LoopResources` | ✅ **Верно** — `DefaultLoopResources.onServerSelect()` при bind |
| п.2: boss + worker группы | ✅ **Концептуально верно** — `onServerSelect` (acceptor) + `onServer` (child) |
| п.3: группы создают `NioEventLoop` | ⚠️ **Устарело по классам** — в Netty 4.2 jstack: `NioIoHandler` + `SingleThreadIoEventLoop`, не `NioEventLoop` |
| п.4–5: Selector, `ServerChannel`, ссылка на `ServerBootstrap` | ⚠️ **Концепция верна**, реализация в RN 1.3.4 — **`TransportConnector`**, не `ServerBootstrap.doBind()` |
| Boss accept → worker | ✅ **Верно**, но это **первый TCP** (`ServerTransport.Acceptor`), не момент boot |

**Verdict:** документ **частично верен** на уровне идей (LoopResources, boss/worker, server channel). **Неточен** в конкретных классах Netty 4.2 и в том, что init идёт через `ServerBootstrap`.

---

### 3. `reacitve-word/WebFlux_Reactor_Netty_Architecture-no-pict.docx` (раздел 1.3 Boss/Worker)

| Утверждение | Block 0 |
|-------------|---------|
| Boss принимает на listening socket, worker обслуживает child Channel | ✅ **Верно** (модель Netty/Reactor Netty) |
| Пример `HttpServer.create()...bindNow()` | ✅ **Совпадает** с тем, как Spring вызывает bind |
| Детали init: `ServerBootstrap`, `NioEventLoopGroup` | ❌ **Не указаны** — и **хорошо**: в runtime их нет |
| Раздел 2.1 шаг 2 «Boss accept» | **Не Block 0** — это первый клиент, не boot |

**Verdict:** для Block 0 docx **корректен на архитектурном уровне** (boss/worker, bind). **Не уточняет** реальный код RN 1.3.4 — этого достаточно для обучения, но **недостаточно для breakpoint**.

---

## Что подтверждено runtime (Block 0)

```text
✅ NettyReactiveWebServerFactory.getWebServer
✅ NettyWebServer.start → startHttpServer → bindNow → Mono.block  (поток "server")
✅ ServerTransport.bind → TransportConnector
✅ DefaultLoopResources.onServerSelect / cacheNioSelectLoops
✅ MultiThreadIoEventLoopGroup + NioIoHandler  (поток "reactor-http-nio-1")
✅ AbstractNioChannel.doBeginRead / addAndSubmit  (ваш breakpoint, Netty 4.2)

❌ ServerBootstrap.group / doBind — не в jstack, не в вашем стеке
❌ NioEventLoopGroup — не в jstack
❌ ServerBootstrapAcceptor — заменён ServerTransport.Acceptor (при accept)
```

---

## Итоговая таблица: какой документ «прав» для Block 0

| Источник | Block 0 init | Комментарий |
|----------|--------------|-------------|
| **Ваши breakpoint + `BLOCK-0-INIT-PATH-VERIFICATION.md`** | ✅ эталон | Проверенный путь для RN 1.3.4 |
| **`HTTP-REQUEST-DEBUG-BREAKPOINTS.md` (старая таблица Block 0)** | ❌ устарела | `ServerBootstrap` / `NioEventLoopGroup` — не вызываются |
| **Док. 7 (Event Loop), п. 1–2** | ✅ mostly | LoopResources + boss/worker |
| **Док. 7, п. 3–5** | ⚠️ частично | Идеи верны, классы Netty 4.2 другие |
| **Док. 13 (путь запроса)** | — не про init | Проверка позже |
| **Docx Architecture, § 1.3** | ✅ концептуально | Boss/worker + bind; без ложного ServerBootstrap |

---

## Покрытие Block 0: всё ли проверено?

| Шаг init | Проверено? | Как |
|----------|------------|-----|
| Spring `getWebServer` / `start` | ✅ | Лог + ваш debug |
| `ServerTransport.bind` | ✅ | Ваш debug |
| `TransportConnector.bind` | ⚠️ | Исходники + логика цепочки; отдельный breakpoint не зафиксирован в этом сеансе |
| `onServerSelect` / EventLoopGroup | ✅ | Ваш debug |
| `doBeginRead` / OP_ACCEPT | ✅ | Ваш debug |
| `ServerTransport.Acceptor` | ⏳ | Только при **первом curl**, не при boot — проверим в блоке 1 |
| Boss/worker «как в учебнике Netty 4.1» | ⚠️ | Модель верна, **имена классов** другие (4.2 + RN 1.3) |

**Вывод:** Block 0 **проверен достаточно**, чтобы **опровергнуть** старую таблицу с `ServerBootstrap` и **подтвердить** ваш путь. Docx и док. 7 **не противоречат** на уровне идей; док. 13 **не про init**. Для breakpoint используйте **`BLOCK-0-INIT-PATH-VERIFICATION.md`**, не старую таблицу в `HTTP-REQUEST-DEBUG-BREAKPOINTS.md`.

---

## Что проверим позже (не Block 0)

- Док. 13: EventLoop → Pipeline → HttpServerOperations → WebFlux
- Docx: сценарии A/B/C (путь запроса)
- `ServerTransport.Acceptor.channelRead` при первом `curl`
