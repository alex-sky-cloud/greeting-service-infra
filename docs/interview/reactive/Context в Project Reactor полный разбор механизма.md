# Context в Project Reactor: полный разбор механизма

## Оглавление

1. [Что такое Context и зачем он нужен](#1-chto-takoe-context)
2. [Почему не ThreadLocal](#2-pochemu-ne-threadlocal)
3. [Топология: данные текут вниз, подписка строится вверх](#3-topologiya)
4. [Модель "матрёшки": каждый оператор — свой Context](#4-model-matryoshki)
5. [N операторов и N элементов данных — не путать](#5-n-operatorov-i-n-elementov)
6. [Правило расположения contextWrite](#6-pravilo-contextwrite)
7. [Изоляция контекста внутри flatMap](#7-izolyaciya-flatmap)
8. [Чтение контекста: deferContextual и transformDeferredContextual](#8-chtenie-konteksta)
9. [Типичная ошибка](#9-tipichnaya-oshibka)
10. [Применение в Spring WebFlux](#10-webflux)
11. [Бизнес-пример: traceId для счёта](#11-biznes-primer)

---

<a id="1-chto-takoe-context"></a>
## 1. Что такое Context и зачем он нужен

`Context` — неизменяемое (immutable) key-value хранилище, привязанное не к потоку выполнения,
а к конкретной подписке (`Subscriber`) на `Mono`/`Flux`.

Источник: https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

**EN:**
> Since version 3.1.0, Reactor comes with an advanced feature that is somewhat comparable to
> ThreadLocal but can be applied to a Flux or a Mono instead of a Thread. This feature is
> called Context.

**RU:** Начиная с версии 3.1.0, в Reactor появилась возможность, сравнимая с ThreadLocal, но
применимая к Flux или Mono, а не к потоку. Называется Context.

`Context` живёт только в рамках конкретной подписки — от `subscribe()` до `onComplete`,
`onError` или `cancel`. После завершения подписки он не сохраняется глобально.

---

<a id="2-pochemu-ne-threadlocal"></a>
## 2. Почему не ThreadLocal

В реактивной цепочке выполнение может переключаться между физическими потоками через
`subscribeOn`/`publishOn`. Значение, положенное в `ThreadLocal` на потоке A, недоступно
после переключения на поток B — оно теряется или путается между параллельными подписками,
использующими общий пул потоков.

**Итог:**
- `ThreadLocal` привязан к потоку.
- `Context` привязан к подписке.
- Поток может меняться в любой момент, подписка остаётся одной и той же на всём протяжении
  цепочки — поэтому `Context` надёжен там, где `ThreadLocal` ненадёжен.

---

<a id="3-topologiya"></a>
## 3. Топология: данные текут вниз, подписка строится вверх

Нужно различать два независимых направления:

```
Фаза подписки (assembly → subscribe):     идёт СНИЗУ ВВЕРХ (от Subscriber к Publisher)
Фаза эмиссии данных (onNext/onComplete):  идёт СВЕРХУ ВНИЗ (от Publisher к Subscriber)
```

```java
Mono<String> invoiceMono =
    Mono.just("invoice-42")                              // (1) upstream: источник
        .flatMap(id -> Mono.deferContextual(
            ctx -> Mono.just(ctx.get("traceId") + ": " + id)
        ))                                                // (2) оператор преобразования
        .contextWrite(ctx -> ctx.put("traceId", "T-1"));  // (3) downstream-оператор

invoiceMono.subscribe(System.out::println);               // (4) конечный Subscriber
```

Источник: https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

**EN:** "In order to populate the Context, which can only be done at subscription time, you
need to use the contextWrite operator. contextWrite(ContextView) merges the ContextView you
provide and the Context from downstream (remember, the Context is propagated from the bottom
of the chain towards the top)."

**RU:** Чтобы заполнить Context, что можно сделать только во время подписки, нужно использовать
`contextWrite`. Он объединяет переданный ContextView с Context от downstream (Context
распространяется от низа цепочки к верху).

Порядок для примера выше:

1. `invoiceMono.subscribe(...)` создаёт конечный `Subscriber`.
2. `contextWrite` первым получает внутренний вызов `subscribe`, берёт видимый снизу Context
   (пустой), создаёт **новый** неизменяемый Context с `traceId = "T-1"` и передаёт его upstream.
3. Подписка доходит до `flatMap`, затем до `Mono.just("invoice-42")`.
4. Только после этого источник начинает посылать сигналы данных вниз по цепочке.

`contextWrite`, хотя написан **внизу** цепочки (ближе к `subscribe()`), применяется **первым**
по времени — потому что это фаза построения подписки, а не фаза эмиссии данных.

---

<a id="4-model-matryoshki"></a>
## 4. Модель "матрёшки": каждый оператор — свой Context

Ключевая деталь, снимающая путаницу: `Context` — это не единый глобальный объект-ссылка,
общий для всех операторов. Он **привязан к каждому Subscriber отдельно**.

Источник: https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

**EN:** "Actually, a Context is tied to each Subscriber in a chain. It uses the Subscription
propagation mechanism to make itself available to each operator, starting with the final
subscribe and moving up the chain."

**RU:** Context привязан к каждому Subscriber в цепочке. Он использует механизм распространения
Subscription, чтобы стать доступным каждому оператору, начиная с финального subscribe() и
двигаясь вверх по цепочке.

### Как строится "матрёшка"

При вызове `subscribe()` для каждого оператора в цепочке создаётся свой отдельный объект
`Subscriber`/`Subscription`. Context не мутируется — он **иммутабелен**, поэтому каждый
`contextWrite` не изменяет существующее хранилище, а создаёт **новый** объект (обёртку
вокруг старого), добавляя/перезаписывая ключ:

```
subscribe()
    │
    ▼  создаётся Subscriber #1 (contextWrite): Context.empty() → Context{traceId=T-1}  ← НОВЫЙ объект
    │
    ▼  создаётся Subscriber #2 (flatMap): держит ссылку на Context{traceId=T-1}
    │
    ▼  создаётся Subscriber #3 (источник Mono.just)
```

Каждый Subscriber хранит ссылку именно на тот `Context`, который был актуален в момент его
создания при подписке. Поэтому "путешествие" контекста — это не движение одного объекта по
цепочке в реальном времени при эмиссии данных, а **разовое построение цепочки ссылок** во
время подписки: каждый следующий (вышестоящий) Subscriber получает ссылку на Context,
дополненный всеми `contextWrite`, что находятся между ним и `subscribe()`.

### Почему нельзя сказать "одна и та же ссылка у всех"

Если бы Context был мутабельным общим хранилищем (как обычный `HashMap`), запись внутри
`flatMap` была бы видна во всей внешней цепочке. Но так как Context immutable, `contextWrite`
внутри вложенной (inner) последовательности `flatMap` создаёт **свой собственный, локальный**
Context, который не выходит за пределы этой inner-последовательности (подробнее в разделе 7).

---

<a id="5-n-operatorov-i-n-elementov"></a>
## 5. N операторов и N элементов данных — не путать

Частая путаница: "если Flux эмитит N элементов, значит создаётся N контекстов". Это неверно.
Речь о двух независимых величинах:

| Что | Когда создаётся | Сколько раз |
|---|---|---|
| Цепочка Subscriber/Subscription | При вызове subscribe() | Один раз, по числу **операторов** в коде |
| Context (и все его версии от contextWrite) | При вызове subscribe(), снизу вверх | Один раз на всю подписку |
| Элементы данных (onNext) | После onSubscribe/request | N раз, по числу элементов **потока** |

Даже если `Flux` эмитит 1000 элементов, Context создаётся **один раз** на этапе подписки.
Далее один и тот же набор Context-объектов (привязанных к своим Subscriber) используется
при обработке каждого из 1000 элементов — Context не пересобирается на onNext(1), onNext(2)
и так далее.

---

<a id="6-pravilo-contextwrite"></a>
## 6. Правило расположения contextWrite

Правило: `contextWrite` физически пишется **ниже** тех операторов, которые должны прочитать
значение — то есть максимально близко к `subscribe()`, а не к источнику.

Источник: https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

**EN:** "the general rule is to put contextWrite as close as possible to the end of the
reactive chain (as close as possible to the subscribe() call)."

**RU:** Общее правило — располагать contextWrite как можно ближе к концу реактивной цепочки
(как можно ближе к вызову subscribe()).

```java
// НЕПРАВИЛЬНО: запись выше чтения — flatMap не увидит значение
Mono<String> wrong = Mono.just("invoice-42")
    .contextWrite(ctx -> ctx.put("traceId", "T-1"))
    .flatMap(id -> Mono.deferContextual(ctx ->
        Mono.just(ctx.getOrDefault("traceId", "MISSING") + ": " + id)));
// Результат: "MISSING: invoice-42"

// ПРАВИЛЬНО: запись ниже чтения, ближе к subscribe()
Mono<String> correct = Mono.just("invoice-42")
    .flatMap(id -> Mono.deferContextual(ctx ->
        Mono.just(ctx.getOrDefault("traceId", "MISSING") + ": " + id)))
    .contextWrite(ctx -> ctx.put("traceId", "T-1"));
// Результат: "T-1: invoice-42"
```

Если контекст записывается несколько раз с одним и тем же ключом, оператор-читатель видит
значение, записанное **ближе всего "под" ним** (то есть тем contextWrite, что находится
между ним и subscribe()), а не самое верхнее.

Источник: https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

**EN:** "in the case of several attempts to write the same key to the Context, the relative
order of the writes matters, too. Operators that read the Context see the value that was set
closest to being under them."

**RU:** В случае нескольких попыток записать один и тот же ключ в Context, относительный
порядок записей также важен. Операторы, читающие Context, видят значение, установленное
ближе всего "под" ними.

```java
String key = "message";
Mono<String> r = Mono.deferContextual(ctx -> Mono.just("Hello " + ctx.get(key)))  // (3) видит "Reactor"
    .contextWrite(ctx -> ctx.put(key, "Reactor"))                                  // (2)
    .flatMap(s -> Mono.deferContextual(ctx -> Mono.just(s + " " + ctx.get(key))))  // (4) видит "World"
    .contextWrite(ctx -> ctx.put(key, "World"));                                   // (1)
// Результат: "Hello Reactor World"
```

---

<a id="7-izolyaciya-flatmap"></a>
## 7. Изоляция контекста внутри flatMap

### 7.1. Что значит "внешняя цепочка" и "внутренняя (inner) последовательность"

`flatMap` (как и `flatMapMany`, `concatMap` и другие подобные операторы) создаёт **внутреннюю
(inner) последовательность** — отдельный Publisher, возвращаемый из лямбды, на который
`flatMap` подписывается самостоятельно, как на маленький вложенный граф:

```java
Mono<String> result = Mono.just("Hello")                       // ВНЕШНЯЯ (основная) цепочка
    .flatMap(s -> {
        // Всё внутри этой лямбды — ВНУТРЕННЯЯ (inner) последовательность.
        // flatMap подписывается на неё отдельно от внешней цепочки.
        Mono<String> inner = Mono.deferContextual(ctx ->
                Mono.just(s + " " + ctx.get("key")))
            .contextWrite(ctx -> ctx.put("key", "Reactor"));    // запись ТОЛЬКО для inner
        return inner;
    })
    .contextWrite(ctx -> ctx.put("key", "World"));              // запись для ВНЕШНЕЙ цепочки
```

"Внешняя цепочка" — операторы, написанные в основном коде метода (то, что видно на верхнем
уровне, не внутри лямбды). "Внутренняя последовательность" — Publisher, который создаётся и
возвращается *внутри* лямбды `flatMap`.

### 7.2. Изоляция работает только в одну сторону: изнутри наружу

Ключевое уточнение: `contextWrite`, стоящий *внутри* лямбды flatMap, не распространяется
наружу — во внешнюю цепочку. Но Context, записанный *снаружи* (ниже по внешней цепочке,
ближе к subscribe()), свободно **проникает внутрь** flatMap — потому что при подписке inner
Publisher наследует Context внешнего окружения как отправную точку.

```java
// Читаем ВНУТРИ flatMap значение, записанное СНАРУЖИ (ниже по внешней цепочке)
Mono<String> result = Mono.just("Hello")
    .flatMap(s -> Mono.deferContextual(ctx ->
        Mono.just(s + " " + ctx.get("key"))))   // читаем внутри лямбды
    .contextWrite(ctx -> ctx.put("key", "World")); // пишем снаружи

result.subscribe(System.out::println);
// Результат: "Hello World" — значение ВИДНО внутри flatMap
```

Это работает, потому что внутренняя последовательность не оторвана от внешнего Context —
она наследует его как базу. А вот *добавление* нового `contextWrite` внутри лямбды остаётся
локальным для inner-последовательности и не просачивается обратно наружу — именно это и
называется изоляцией.

Источник: https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

**EN:** "This contextWrite does not impact anything outside of its flatMap... it is not
visible or propagated through the main sequence. Propagation and immutability isolate the
Context in operators that create intermediate inner sequences such as flatMap."

**RU:** Эта запись contextWrite не влияет ни на что за пределами своего flatMap... она не
видна и не распространяется через основную последовательность. Распространение и
неизменяемость изолируют Context в операторах, создающих промежуточные внутренние
последовательности, такие как flatMap.

### 7.3. Полный пример с двумя flatMap

```java
Mono<String> r = Mono.just("Hello")
    .flatMap(s -> Mono.deferContextual(ctx -> Mono.just(s + " " + ctx.get(key))))
    .flatMap(s -> Mono.deferContextual(ctx -> Mono.just(s + " " + ctx.get(key)))
        .contextWrite(ctx -> ctx.put(key, "Reactor")))  // виден ТОЛЬКО внутри этого flatMap
    .contextWrite(ctx -> ctx.put(key, "World"));
// Результат: "Hello World Reactor" (а НЕ "Hello Reactor World")
```

Это прямое доказательство того, что Context не "проталкивается" вместе с данными onNext —
каждый оператор запрашивает Context у своего собственного downstream Subscriber (то есть
у ссылки, зафиксированной при подписке), а не читает общий "текущий" снапшот, плывущий по
потоку вместе с данными. Если бы Context двигался вместе с данными в реальном времени, второй
flatMap увидел бы "Reactor" (последний write перед ним по ходу данных), а не "World".

### 7.4. Терминологическая поправка: "передаётся" vs "запрашивается"

Формулировка "Context передаётся вверх по цепочке" — распространённое, но неточное упрощение.
Технически каждый оператор **запрашивает** Context у своего непосредственного нижестоящего
(downstream) Subscriber. Разница принципиальная: слово "передаётся" наводит на ложную модель,
будто Context движется вместе с сигналами onNext в реальном времени по мере эмиссии данных.
На самом деле весь набор версий Context строится **один раз**, во время фазы подписки
(см. раздел 3), и каждый Subscriber в цепочке уже держит готовую ссылку на "свою" версию
Context ещё до начала эмиссии данных. Когда дальше происходит onNext, оператор просто
обращается к уже вычисленной ссылке — не запрашивает и не получает что-то заново "с высоты".

---

<a id="8-chtenie-konteksta"></a>
## 8. Чтение контекста

### Mono.deferContextual

Читает Context лениво, в момент подписки на возвращаемый Publisher:

```java
Mono<String> readTrace = Mono.deferContextual(ctx ->
    Mono.just(ctx.get("traceId"))
);
```

### ContextView.getOrDefault

Безопасное чтение: если ключа нет — возвращает значение по умолчанию, а не бросает
`NoSuchElementException` (в отличие от `ctx.get(key)`):

```java
String trace = ctx.getOrDefault("traceId", "unknown");
```

### transformDeferredContextual

Доступ к контексту вместе с исходным Mono/Flux:

```java
Mono<String> transformed = Mono.just("payload")
    .transformDeferredContextual((mono, ctx) ->
        mono.map(payload -> payload + " for user " + ctx.get("userId"))
    );
```

---

<a id="9-tipichnaya-oshibka"></a>
## 9. Типичная ошибка

Самая частая ошибка — ставить `contextWrite()` ближе к источнику, интуитивно ожидая, что
"запись должна идти первой, поэтому она в начале цепочки":

```java
Mono.just("start")
    .contextWrite(ctx -> ctx.put("userId", "u-123"))
    // ОШИБКА: contextWrite стоит ВЫШЕ deferContextual — значение не увидит его
    .flatMap(v -> Mono.deferContextual(ctx ->
        Mono.just("Order for user: " + ctx.getOrDefault("userId", "unknown"))))
    .subscribe(System.out::println);
// ВЫВЕДЕТ: "Order for user: unknown"
```

Правильная версия — запись ниже чтения:

```java
Mono.deferContextual(ctx ->
        Mono.just("Order for user: " + ctx.getOrDefault("userId", "unknown")))
    .flatMap(Mono::just)
    .contextWrite(ctx -> ctx.put("userId", "u-123"))
    .subscribe(System.out::println);
// ВЫВЕДЕТ: "Order for user: u-123"
```

---

<a id="10-webflux"></a>
## 10. Применение в Spring WebFlux

Входящий HTTP-запрос в WebFlux сам не вызывает `.subscribe()` — это делает серверная
инфраструктура (Reactor Netty + HttpHandler). Естественная граница приложения для установки
данных запроса в Context — `WebFilter`, потому что он оборачивает всю дальнейшую цепочку
обработки запроса.

Источник: https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-context.html

**EN:** "The Reactor Context needs to be populated at the end of a reactive chain in order to
apply to all operations."

**RU:** Reactor Context нужно заполнять в конце реактивной цепочки, чтобы он применился ко
всем операциям.

```java
@Component
final class TraceIdWebFilter implements WebFilter {

    private static final String TRACE_ID = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incomingTraceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        String traceId = incomingTraceId != null ? incomingTraceId : UUID.randomUUID().toString();

        return chain.filter(exchange)
            .contextWrite(ctx -> ctx.put(TRACE_ID, traceId));
    }
}
```

Не следует вызывать `.subscribe()` вручную внутри `WebFilter`, контроллера или сервиса —
это создаёт отдельную, неконтролируемую подписку, оторванную от жизненного цикла HTTP-ответа
и Context исходного запроса. Приложение возвращает `Mono`/`Flux`, подписку делает фреймворк.

---

<a id="11-biznes-primer"></a>
## 11. Бизнес-пример: traceId для счёта

API `GET /invoices/{id}`: запрос проходит через `TraceIdWebFilter`, контроллер вызывает
R2DBC-репозиторий и сервис конвертации валют через `WebClient`. Требование: все логи и
исходящий HTTP-запрос содержат один и тот же `traceId`, даже при переключении потоков.

```java
@RestController
final class InvoiceController {

    @GetMapping("/invoices/{id}")
    Mono<InvoiceResponse> getInvoice(@PathVariable UUID id) {
        return invoiceRepository.findById(id)
            .flatMap(invoice -> currencyClient.convert(invoice.amount(), invoice.currency())
                .flatMap(convertedAmount -> Mono.deferContextual(ctx -> {
                    String traceId = ctx.get("traceId");
                    log.info("traceId={}, invoiceId={}, convertedAmount={}",
                        traceId, invoice.id(), convertedAmount);
                    return Mono.just(new InvoiceResponse(invoice.id(), convertedAmount, traceId));
                }))
            );
        // subscribe() не вызывается — Mono возвращается WebFlux-инфраструктуре
    }
}
```

**Как это работает:**

1. `TraceIdWebFilter` извлекает `X-Trace-Id` либо создаёт новый и возвращает
   `chain.filter(exchange).contextWrite(...)`.
2. Серверная инфраструктура подписывается на итоговый `Mono<Void>` обработки запроса.
3. Во время фазы подписки `contextWrite` фильтра добавляет `traceId` и передаёт обновлённый
   Context upstream — к маршрутизации, контроллеру и пользовательской цепочке.
4. Репозиторий получает счёт, `flatMap` запускает `currencyClient.convert`, а
   `deferContextual` читает уже установленный `traceId` при подписке на свой inner Publisher.
5. Данные и итоговый HTTP-ответ идут вниз по цепочке; `traceId` остаётся привязанным к одной
   подписке, а не к конкретному Thread.
