# Объяснение, что такое Reactor Context и почему не используем ThreadLocal, для передачи реактивного контекста

## Оглавление

1. [Что такое Context в Reactor](#1-что-такое-context-в-reactor)
2. [Почему ThreadLocal не работает в реактивном коде](#2-почему-threadlocal-не-работает-в-реактивном-коде)
3. [Как Context решает эту проблему](#3-как-context-решает-эту-проблему)
4. [Разбор ключевых операторов](#4-разбор-ключевых-операторов)
5. [Бизнес-кейс: traceId в микросервисе](#5-бизнес-кейс-traceid-в-микросервисе)
6. [Почему contextWrite должен быть в конце цепочки](#6-почему-contextwrite-должен-быть-в-конце-цепочки)

---

## 1. Что такое Context в Reactor

`Context` — это неизменяемое (immutable) key-value хранилище, которое привязано не к потоку выполнения, а к конкретному `Subscriber` (то есть к конкретной подписке на `Mono`/`Flux`).

Официальная документация Project Reactor описывает это так:

"Since version 3.1.0, Reactor comes with an advanced feature that is somewhat comparable to ThreadLocal but can be applied to a Flux or a Mono instead of a Thread. This feature is called Context."
(https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html)

**RU**: 

"Начиная с версии 3.1.0, в Reactor появилась продвинутая возможность, в некотором смысле сравнимая с **ThreadLocal**, но применимая к **Flux** или **Mono**, а не к потоку. Эта возможность называется **Context**."

И далее, про механизм привязки:

"Actually, a Context is tied to each Subscriber in a chain. It uses the Subscription propagation mechanism to make itself available to each operator, starting with the final subscribe and moving up the chain."
(https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html)

**RU**: 

> "На самом деле, **Context** _привязан_ к каждому **Subscriber** в цепочке. 
> Он использует механизм распространения **Subscription**, чтобы стать доступным каждому оператору, начиная с финального **subscribe()** и двигаясь вверх по цепочке."

То есть **Context** передаётся не "по потоку выполнения", а **по цепочке подписки** — снизу вверх, независимо от того, на каком физическом **Thread** исполняется каждый оператор.

---

```java

  Mono<String> invoiceMono =
                Mono.just("invoice-42")                         // Publisher
                        .flatMap(
                                id -> Mono.deferContextual( // оператор
                                        ctx ->  Mono.just(ctx.get("traceId") + ": " + id) 
                                )
                        )
                        .contextWrite(
                                ctx -> ctx.put("traceId", "T-1")
                        );

        invoiceMono.subscribe(                               // Subscriber
                value -> System.out.println(value)
        );
```

- `Publisher` — это интерфейс `org.reactivestreams.Publisher`; 
  - `Mono` и `Flux` являются его реализациями. 
    - В примере `invoiceMono` — это объект `Mono`, то есть `Publisher`.
    
- `Subscriber` — интерфейс `org.reactivestreams.Subscriber`. 
  - Вызов `subscribe(value -> ...)` создаёт конечный `Subscriber` за вас.
  
- `Subscription` — интерфейс `org.reactivestreams.Subscription`. 
  - Reactor создаёт его при подписке и передаёт подписчику, через `onSubscribe(Subscription subscription)`. 
    - Через него вызываются `request(n)` и `cancel()`.
    
- `CoreSubscriber` — Reactor-интерфейс, расширяющий `Subscriber`. 
  - Именно у него есть метод `currentContext()`.

`Context` физически доступен через `CoreSubscriber.currentContext()`. 
 - Прикладной код обычно не получает этот внутренний `CoreSubscriber` напрямую, а читает контекст безопасным оператором:


```java
Mono.deferContextual(ctx -> {
String traceId = ctx.get("traceId"); // ContextView
    return Mono.just(traceId);
});
```

 - То есть в вашем примере `ctx` внутри `deferContextual` — это представление `Context` текущего внутреннего `CoreSubscriber`.

**Источник:** https://projectreactor.io/docs/core/release/api/reactor/core/CoreSubscriber.html

`CoreSubscriber` — это `Subscriber`, понимающий `Context`; его метод `currentContext()` получает контекст от downstream-операторов или конечного `Subscriber`.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> Документация Reactor указывает, что `Context` связан с каждым `Subscriber` в цепочке, а данные контекста передаются по механизму `Subscription` от конечной подписки вверх по цепочке.


```textmate

Mono.just(...) / flatMap(...) / contextWrite(...)
                    ↑
                Publisher-цепочка

subscribe(value -> ...)
        ↑
конечный Subscriber
```

---

## 2. Почему ThreadLocal не работает в реактивном коде

В обычном (императивном, блокирующем) коде один запрос от начала до конца обрабатывается одним потоком — поэтому `ThreadLocal` работает надёжно: положил значение в начале, прочитал его в конце того же потока.

В Reactor это не так: один и тот же логический поток данных (одна подписка) может в разные моменты времени исполняться на РАЗНЫХ физических потоках — например, после `subscribeOn` или `publishOn`. Значение, положенное в `ThreadLocal` на потоке A, будет недоступно, если выполнение переехало на поток B.

Официальная документация формулирует эту проблему так:

"This arrangement is especially hard for developers that use features dependent on the threading model being more 'stable,' such as ThreadLocal. As it lets you associate data with a thread, it becomes tricky to use in a reactive context. As a result, libraries that rely on ThreadLocal at least introduce new challenges when used with Reactor. At worst, they work badly or even fail. Using the MDC of Logback to store and log correlation IDs is a prime example of such a situation."
(https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html)

Перевод: "Эта ситуация особенно сложна для разработчиков, использующих возможности, зависящие от более 'стабильной' модели потоков, такие как ThreadLocal. Поскольку он позволяет ассоциировать данные с потоком, его становится сложно использовать в реактивном контексте. В результате библиотеки, полагающиеся на ThreadLocal, как минимум создают новые сложности при использовании с Reactor. В худшем случае они работают неправильно или вообще выходят из строя. Использование MDC из Logback для хранения и логирования correlation ID — характерный пример такой ситуации."

**Итог:** ThreadLocal привязан к потоку. Context привязан к подписке. В реактивном коде поток может меняться в любой момент, а подписка остаётся одной и той же на всём протяжении цепочки — поэтому Context надёжен там, где ThreadLocal ненадёжен.

---

## 3. Как Context решает эту проблему

Пример из документации Project Reactor:

```java
String key = "message";
Mono<String> r = Mono.just("Hello")
    .flatMap(s -> Mono.deferContextual(ctx ->
         Mono.just(s + " " + ctx.get(key))))
    .contextWrite(ctx -> ctx.put(key, "World"));

StepVerifier.create(r)
            .expectNext("Hello World")
            .verifyComplete();
```

Ключевое правило из документации про порядок записи и чтения:

"In your chain of operators, the relative positions of where you write to the Context and where you read from it matters. The Context is immutable and its content can only be seen by operators above it."
(https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html)

Перевод: "В цепочке операторов важно относительное расположение мест записи в Context и чтения из него. Context неизменяем, и его содержимое видно только операторам, находящимся выше него (относительно точки записи)."

---

## 4. Разбор ключевых операторов

Перед тем как переходить к полному примеру, разберём три метода по отдельности.

### Mono.deferContextual(ContextView -> ...)

Позволяет "заглянуть" в текущий Context изнутри цепочки операторов. Даёт доступ к `ContextView` (объект для чтения контекста) в момент подписки:

```java
// Пример: просто читаем значение из контекста и оборачиваем его в Mono
Mono<String> readTrace = Mono.deferContextual(ctx ->
    Mono.just(ctx.get("traceId")) // ctx.get(key) достаёт значение по ключу
);
```

### ContextView.getOrDefault(key, defaultValue)

Безопасное чтение значения из контекста: если ключа нет — возвращается `defaultValue`, а не бросается исключение (в отличие от `ctx.get(key)`, который бросает `NoSuchElementException`, если ключа нет):

```java
// Если "traceId" не был записан ранее по цепочке — вернётся "unknown"
String trace = ctx.getOrDefault("traceId", "unknown");
```

### contextWrite(Function<Context, Context>)

Метод, которым в контекст ЗАПИСЫВАЕТСЯ новое значение. Должен стоять НИЖЕ по цепочке, чем место, где значение читается — потому что Context передаётся от contextWrite вверх, к операторам, которые находятся выше него:

```java
// ctx.put(key, value) добавляет пару в контекст и возвращает НОВЫЙ (иммутабельный) Context
Mono<String> writeTrace = someMono.contextWrite(ctx -> ctx.put("traceId", "abc-123"));
```

---

## 5. Бизнес-кейс: traceId в микросервисе

### Откуда берётся traceId

`TRACE_ID_KEY` — это НЕ сам traceId, а просто имя ключа (строка "traceId"), под которым значение хранится в контексте. Это как имя переменной, а не её значение:

```java
static final String TRACE_ID_KEY = "traceId"; // это просто ярлык-ключ, а не значение
```

Сам traceId (значение) — уникальный идентификатор конкретного запроса. Он либо приходит извне (HTTP-заголовок X-Trace-Id от клиента или другого микросервиса), либо генерируется на входе в систему, если запрос "первый" в цепочке вызовов:

```java
// Пример метода, где traceId реально появляется
Mono<Response> handleRequest(Request req) {
    String traceId = req.getHeader("X-Trace-Id") != null
        ? req.getHeader("X-Trace-Id")   // пришёл от клиента/другого сервиса
        : UUID.randomUUID().toString(); // либо генерируем новый, если это первая точка входа
    // ...
}
```

### Полный пример по шагам

```java
static final String TRACE_ID_KEY = "traceId"; // имя ключа для хранения в Context

Mono<Response> handleRequest(Request req) {
    // Шаг A: получаем реальное значение traceId — либо из входящего запроса, либо генерируем новое
    String traceId = req.getHeader("X-Trace-Id") != null
        ? req.getHeader("X-Trace-Id")
        : UUID.randomUUID().toString();

    return processOrder(req)
        // Шаг B: обрабатываем заказ (обычная бизнес-логика)
        .flatMap(order -> paymentService.charge(order))
        // Шаг C: paymentService.charge() может внутри использовать subscribeOn
        //         и переключить дальнейшее выполнение на другой поток —
        //         именно поэтому мы не можем просто передать traceId как обычную Java-переменную,
        //         она "потеряется" при смене потока (как и было бы с ThreadLocal)

        .flatMap(chargeResult -> Mono.deferContextual(ctx -> {
            // Шаг D: deferContextual даёт нам доступ к ContextView (ctx) —
            //         объекту, из которого можно прочитать значения, записанные ниже по цепочке
            String traceIdFromContext = ctx.getOrDefault(TRACE_ID_KEY, "unknown");
            // getOrDefault(key, fallback): если по ключу TRACE_ID_KEY ничего не записано — вернёт "unknown",
            // а не бросит исключение

            log.info("traceId={}, chargeResult={}", traceIdFromContext, chargeResult);
            return Mono.just(chargeResult);
        }))

        // Шаг E: здесь мы ЗАПИСЫВАЕМ traceId (то самое значение из шага A) в Context.
        //        ctx.put(key, value) — записывает пару ключ-значение и возвращает новый Context
        .contextWrite(ctx -> ctx.put(TRACE_ID_KEY, traceId));
        // Именно значение "traceId" из шага A попадает в контекст здесь,
        // и становится доступным через ctx.getOrDefault(...) в шаге D, несмотря на смену потока в шаге C
}
```

### Итог по цепочке значений

1. `traceId` (переменная) — реальное значение, взятое из заголовка запроса или сгенерированное `UUID.randomUUID()`.
2. `TRACE_ID_KEY` — просто строка `"traceId"`, имя ключа, под которым это значение кладётся в Context.
3. `.contextWrite(ctx -> ctx.put(TRACE_ID_KEY, traceId))` — кладёт значение traceId под ключом TRACE_ID_KEY в контекст этой конкретной подписки.
4. `ctx.getOrDefault(TRACE_ID_KEY, "unknown")` — достаёт то же самое значение обратно по тому же ключу, в любой точке цепочки выше, даже если между записью и чтением сменился поток выполнения.

---

## 6. Почему contextWrite должен быть в конце цепочки

`contextWrite` действует только на операторы, находящиеся ВЫШЕ него по цепочке (то есть ближе к источнику данных). Если поставить его в начале цепочки, а читать Context в конце — значение не будет видно.

```java
// НЕПРАВИЛЬНО: contextWrite стоит выше, чем deferContextual — значение НЕ будет видно
Mono.just("data")
    .contextWrite(ctx -> ctx.put("key", "value")) // записали здесь
    .flatMap(s -> Mono.deferContextual(ctx -> Mono.just(ctx.getOrDefault("key", "MISSING")))); // но здесь его уже "не видно" — вернётся MISSING
```

```java
// ПРАВИЛЬНО: contextWrite стоит ниже, значение видно операторам выше
Mono.just("data")
    .flatMap(s -> Mono.deferContextual(ctx -> Mono.just(ctx.getOrDefault("key", "MISSING")))) // здесь значение УЖЕ доступно
    .contextWrite(ctx -> ctx.put("key", "value")); // запись здесь распространяется вверх по цепочке
```

Почему это работает именно так: Context привязан к подписке и передаётся снизу вверх при построении подписки (не при выполнении данных, а на этапе subscribe) — поэтому запись должна физически находиться "ниже" в коде, чтобы попасть во все операторы выше неё.
