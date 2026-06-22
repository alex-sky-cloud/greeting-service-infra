# Реактивный код: где ждёт запрос и где хранится состояние

> Отдельное руководство к [project-reactor-interview-guide.md](./project-reactor-interview-guide.md), §2.1.  
> **Формат:** аналогия → PNG → ответ → пример → источник → цитата EN/RU.

**Перегенерация PNG:** `python docs/Images-docs/gen_reactor_diagrams.py`.

---

## Оглавление

1. [Что происходит, когда приходит запрос](#1-что-происходит-когда-приходит-запрос)
2. [Где хранится состояние: императив vs реактив](#2-где-хранится-состояние-императив-vs-реактив)
3. [Пример: WebClient и удалённый сервер](#3-пример-webclient-и-удалённый-сервер)
4. [Где технически «паркуются» задачи](#4-где-технически-паркуются-задачи)
5. [Сравнение с Virtual Threads (Java 21+)](#5-сравнение-с-virtual-threads-java-21)
6. [Итог](#6-итог)

---

## 1. Что происходит, когда приходит запрос

> **Аналогия:** вы не **строите дом сразу** — вы оставляете **чертёж** (Mono/Flux). Стройка начинается только когда заказчик говорит «начинай» (`subscribe()`). Пока ждёте цемент с завода (БД, HTTP) — **бригада уехала на другой объект** (поток свободен), а **план работ лежит в офисе** (Subscription в heap).

![Когда приходит HTTP-запрос](./Images-docs/reactive-state-01-request-flow.png)

**Ответ — по шагам:**

1. **Создаётся декларативная цепочка** (`Mono`/`Flux` + `map`/`flatMap`/…) — это **описание** вычисления, не готовый результат и не «уже выполненный код».
2. При **`subscribe()`** (в WebFlux это делает **Spring**, не вы в контроллере) цепочка **запускается** и выполняет ту часть, которая не ждёт внешний мир.
3. Когда нужно **ждать внешний вызов** (БД, удалённый сервер) — поток **освобождается**, JVM возвращается в **event loop** (Netty).
4. **Состояние задачи** (куда приходить дальше, что уже получено) хранится в объектах в **куче** — в первую очередь в **`Subscription`** и связанных подписчиках/операторах.
5. Когда внешний сервис отвечает — **callback** от Reactor/Netty **возобновляет** цепочку на свободном потоке event loop (или на потоке, выбранном `publishOn`).

**Вопрос:** *Why doesn't a reactive thread block while waiting for I/O?*

**Источник:** [Reactor Reference — Introduction](https://projectreactor.io/docs/core/release/reference/#intro-reactor) · [Spring WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html)

> **EN:** «Reactor is a fully non-blocking reactive programming foundation for the JVM.» / «Spring WebFlux … non-blocking I/O.»

> **RU:** «Reactor — полностью неблокирующая основа для JVM.» / «WebFlux … неблокирующий ввод-вывод.»

---

## 2. Где хранится состояние: императив vs реактив

> **Аналогия:** **императивно** — мастер **стоит у станка** с блокнотом в руке (стек потока). **Реактивно** — заявка **лежит в канцелярии** (heap), мастер **уехал**, вернётся по звонку (callback).

![Стек потока vs heap](./Images-docs/reactive-state-02-stack-vs-heap.png)

### Императивный подход (Servlet + Tomcat)

```
Request → Thread → блокируется на БД → стек потока хранит всё состояние
```

- Поток **заблокирован** и занимает ресурс (~**1 MB** стека на поток в типичной JVM).
- Состояние = **локальные переменные и frames** в стеке вызовов.
- 100 запросов ждут БД → **100 потоков заняты** → 101-й ждёт в **очереди Tomcat**.

### Реактивный подход (WebFlux + Reactor)

```
Request → Event Loop → Mono/Flux (описание) → subscribe() →
  часть выполняется → ждёт БД → поток в event loop →
  состояние в Subscription (heap) → callback возобновляет цепочку
```

| Компонент | Что хранит | Где |
|-----------|------------|-----|
| **Subscription** | связь подписчик ↔ источник, запросы `request(n)`, буферы | **heap** |
| **Mono / Flux** | описание цепочки операторов (lazy) | **heap** |
| **Subscriber** | уже полученные данные, обработчик следующего шага | **heap** |
| **Context** (Reactor) | trace ID, tenant и др. между операторами | **heap** |
| **Стек вызовов** | нет «глубокого» стека ожидания — **continuation** | — |

В Reactor **нет стека вызовов в привычном смысле** для ожидания I/O: цепочка операторов выполняется как **state machine** — на каждом шаге знаем, **куда перейти после** `onNext` / `onComplete` / `onError`.

**Источник:** [Reactive Streams — Subscription](https://www.reactive-streams.org/reactive-stacks) · [Reactor — Core Features](https://projectreactor.io/docs/core/release/reference/coreFeatures.html)

> **EN:** «Subscription represents a one-to-one lifecycle of a Subscriber subscribing to a Publisher.»

> **RU:** «Subscription — связь «один подписчик ↔ один источник» на время подписки.»

---

## 3. Пример: WebClient и удалённый сервер

> **Аналогия:** вы **отправили курьера** (HTTP-запрос через Netty) и **не стоите у двери** — занимаетесь другими делами. Курьер **звонит**, когда посылка пришла (`onNext`).

![Sequence: WebClient](./Images-docs/reactive-state-03-webclient-sequence.png)

```java

@GetMapping("/user/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    return webClient.get()
        .uri("/user/{id}", id)
        .retrieve()
        .bodyToMono(User.class)       // ждём ответ от сервера (неблокирующе)
        .map(user -> enrich(user));   // продолжится после onNext
}
```

**Что происходит:**

1. Контроллер возвращает **`Mono<User>`** — описание пайплайна, не готовый `User`.
2. **WebFlux** подписывается на `Mono` (**`subscribe()`** внутри фреймворка).
3. `webClient.get()` запускает **неблокирующий** HTTP через Netty.
4. Netty регистрирует **callback** и **сразу** отдаёт поток event loop.
5. Состояние «ждём ответ, потом `map(enrich)`» — в **Subscription** / подписчике в **heap**.
6. Удалённый сервер ответил → Netty → callback → **`onNext(User)`** → `map` → JSON клиенту.

**Ключевое:** поток **не блокируется** на HTTP; состояние задачи = **объекты в куче**, а не стек заблокированного thread.

**Источник:** [Spring WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html) · [Reactor Netty HTTP Client](https://projectreactor.io/docs/netty/release/reference/index.html#http-client)

> **EN:** «WebClient … non-blocking, reactive client to perform HTTP requests.»

> **RU:** «WebClient — неблокирующий реактивный клиент для HTTP-запросов.»

---

## 4. Где технически «паркуются» задачи

**Ответ:** очереди **есть**, но это **не** очередь «100 потоков ждут БД».

| Место | Что там |
|-------|---------|
| **Netty Event Loop** | очередь **коротких задач** и **callback'ов** |
| **Буферы операторов** | `onBackpressureBuffer()` — элементы при избытке |
| **Backpressure** | `request(n)` — подписчик регулирует темп |
| **`Schedulers.boundedElastic()`** | пул для **блокирующего** legacy-кода (если без выбора) |
| **Reactor Subscription** | основное **состояние цепочки** (heap) |
| **Reactor Context** | проброшенные метаданные (trace ID, tenant) |

**Императивная «очередь»** — запросы на **свободный поток** Tomcat.  
**Реактивная «парковка»** — **subscription + буферы + task queue event loop**.

**Источник:** [Reactor — Schedulers](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html) · [Reactor — Backpressure](https://projectreactor.io/docs/core/release/reference/#backpressure)

> **EN:** «boundedElastic is made to help with legacy blocking code if it cannot be avoided.»

> **RU:** «boundedElastic помогает с legacy блокирующим кодом, если его нельзя избежать.»

---

## 5. Сравнение с Virtual Threads (Java 21+)

![WebFlux vs Virtual Threads](./Images-docs/reactive-state-04-virtual-threads.png)

Оба подхода позволяют **не держать OS-поток** всё время ожидания I/O — но механизм разный.

| | **WebFlux + Reactor** | **Virtual Threads (Java 21+)** |
|---|------------------------|------------------------------|
| **Состояние при ожидании** | `Subscription`, операторы (heap) | стек **виртуального** потока (тоже heap) |
| **Стиль кода** | `Mono`/`Flux`, callback/continuation | обычный императивный Java |
| **API** | R2DBC, WebClient, реактивные клиенты | JDBC, `RestTemplate` — блокирующий вид OK |
| **Потоки** | мало потоков **event loop** | **carrier threads** + планировщик JVM |
| **Парковка** | callback Reactor/Netty | JVM **демонтирует** VT с carrier при блокирующем I/O |

**Virtual Threads:**

1. VT вызывает блокирующий I/O → JVM **сохраняет** состояние VT в куче.
2. VT **снимается** с carrier thread.
3. Carrier thread выполняет **другой** VT.
4. I/O завершился → VT **монтируется** на свободный carrier.

Это **cooperative scheduling** на уровне JVM, но **синтаксис** остаётся привычным императивным.

**Источник:** [JEP 444: Virtual Threads](https://openjdk.org/jeps/444) · [Spring Boot 3.2+ Virtual Threads](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads)

> **EN:** «Virtual threads are lightweight threads that dramatically reduce the effort of writing, maintaining, and observing high-throughput concurrent applications.»

> **RU:** «Виртуальные потоки — лёгкие потоки для высоконагруженных приложений с меньшими затратами на потоки ОС.»

---

## 6. Итог

В реактивном подходе (WebFlux + Reactor):

1. **Задача «паркуется»** в объектах **`Subscription`** и цепочки операторов в **heap**.
2. **Состояние** = описание пайплайна + данные подписчика + Context — **не** стек заблокированного thread.
3. **Поток освобождается** и возвращается в **event loop**.
4. **Продолжение** — через **callback**, когда БД/HTTP ответили.
5. **Очереди** — у event loop и операторов (backpressure), **не** «100 JVM-потоков ждут БД».

> **Магия масштаба:** много одновременных соединений на **десятках** потоков event loop, потому что ожидание I/O **не привязано** к стеку OS-потока.

**Связанные разделы:** [§2.1 в interview-guide](./project-reactor-interview-guide.md#21-императивный-и-реактивный-код--кто-ждёт-и-где) · [§5 subscribe vs block](./project-reactor-interview-guide.md#5-subscribe-и-block--в-чём-разница) · [§22 Context](./project-reactor-interview-guide.md#22-context--mdc-и-traceid-между-потоками)

---

*Сигнатуры и термины — [Reactor Reference](https://projectreactor.io/docs/core/release/reference/), [Reactive Streams](https://www.reactive-streams.org/). PNG: `docs/Images-docs/reactive-state-*.png`.*
