# Backpressure в Reactor: разбор интервью

## Оглавление

- [Что такое backpressure](#chto-takoe-backpressure)
- [Механизм request n](#mehanizm-request-n)
- [limitRate разбивка запроса а не потеря элементов](#limitrate-razbivka-zaprosa-a-ne-poterya-elementov)
- [Стратегии onBackpressureXxx](#strategii-onbackpressurexxx)
  - [onBackpressureBuffer](#onbackpressurebuffer)
  - [onBackpressureDrop](#onbackpressuredrop)
  - [onBackpressureLatest](#onbackpressurelatest)
- [Pull vs Push режимы](#pull-vs-push-rezhimy)
- [Backpressure с неполностью реактивным источником](#backpressure-s-nepolnostyu-reaktivnym-istochnikom)
- [Итоговая сравнительная таблица](#itogovaya-sravnitelnaya-tablitsa)

<a id="chto-takoe-backpressure"></a>
## Что такое backpressure

**Backpressure** — механизм, при котором медленный подписчик (downstream) сигнализирует быстрому издателю (upstream), сколько именно элементов он готов принять, чтобы не быть перегруженным. Практический пример: сервис получает поток заказов из очереди сообщений быстрее, чем успевает записывать их в БД — без backpressure очередь заказов в памяти неограниченно растёт и приводит к `OutOfMemoryError`.

<a id="mehanizm-request-n"></a>
## Механизм request(n)

**В основе backpressure лежит вызов `Subscription.request(n)` — подписчик явно сообщает издателю число элементов, которые он готов обработать.** 
- **Издатель** не имеет права слать больше, чем было запрошено, а суммарный накопленный спрос (cumulative demand) не должен превышать `Long.MAX_VALUE`.

Источник: https://www.reactive-streams.org/reactive-streams-1.0.0-javadoc/org/reactivestreams/Subscription.html

EN:

> "No events will be sent by a Publisher until demand is signaled via this method. It can be called however often and whenever needed—but the outstanding cumulative demand must never exceed Long.MAX_VALUE. An outstanding cumulative demand of Long.MAX_VALUE may be treated by the Publisher as 'effectively unbounded'."

RU:

> "Издатель не будет отправлять события, пока спрос не будет заявлен через этот метод. 
> Его можно вызывать сколько угодно раз и когда угодно — но суммарный невыполненный спрос никогда не должен превышать Long.MAX_VALUE. 
> Невыполненный суммарный спрос, равный Long.MAX_VALUE, может интерпретироваться издателем как 'фактически неограниченный'."

<a id="limitrate-razbivka-zaprosa-a-ne-poterya-elementov"></a>
## limitRate: разбивка запроса, а не потеря элементов

`limitRate(n)` — это не стратегия отбрасывания данных, а способ разбить один большой запрос (`request(Long.MAX_VALUE)`) на серию мелких запросов по n элементов. Источник физически не производит элемент, пока downstream не запросил следующую порцию — поэтому ничего не теряется, просто регулируется темп.

Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html

EN:

> "limitRate(int prefetchRate) — Ensure that backpressure signals from downstream subscribers are split into batches capped at the provided prefetchRate rather than being fully propagated upstream (i.e. prefetching)."

RU:

> "limitRate(int prefetchRate) — гарантирует, что сигналы backpressure от downstream-подписчиков разбиваются на пачки, ограниченные указанным prefetchRate, вместо того чтобы полностью передаваться вверх по цепочке (то есть выполняется предзагрузка/prefetching)."

**Бизнес-кейс:** 
 - сервис читает большой файл с транзакциями из S3 и записывает каждую строку в БД. 
 - Если не ограничить темп, `read()` вычитает весь файл в память разом, а `save()` в БД не успевает за этим потоком:

```java
@Service
class TransactionImportService {

    private final TransactionRepository transactionRepository;

    Flux<Void> importTransactions(Flux<Transaction> fileStream) {
        return fileStream
            .limitRate(50) // запрашиваем у файлового источника не более 50 строк за раз
            .flatMap(transaction ->
                Mono.fromCallable(() -> transactionRepository.save(transaction)) // запись в БД — медленная операция
                    .subscribeOn(Schedulers.boundedElastic())
            )
            .thenMany(Flux.empty());
    }
}
```

---
`subscribeOn`  не нужен, если `save` реактивный.

## Почему subscribeOn был лишним в примере

Реактивный вызов (например, через R2DBC-репозиторий) уже не блокирует поток — он регистрирует **callback** и возвращает управление немедленно, без ожидания ответа от БД, поэтому оборачивать его в `Mono.fromCallable(...).subscribeOn(boundedElastic())` не нужно.

Источник: https://docs.spring.io/spring-data/r2dbc/docs/1.3.12/reference/html/

EN:

> "R2DBC is an API specification initiative that declares a reactive API to be implemented by driver vendors to access their relational databases."

RU:

> "R2DBC — это инициатива по спецификации API, которая описывает реактивный API, реализуемый производителями драйверов для доступа к реляционным базам данных."

`subscribeOn(Schedulers.boundedElastic())` нужен только тогда, когда под капотом вызывается по-настоящему **блокирующий** код — JDBC, блокирующий HTTP-клиент, файловый I/O — то есть когда поток физически зависает и ждёт ответа синхронно. 
- Комьюнити-практика прямо описывает этот паттерн именно как способ изолировать блокирующий вызов, а не как обязательный шаг для любого вызова в БД.

Источник: https://www.linkedin.com/posts/ajanoni_spring-webflux-reactiveprogramming-activity-7391252358451826689-wgeD

EN:

> "If you can't migrate to R2DBC (the real reactive solution) yet, you must isolate that blocking JPA/JDBC call. The Classic Escape: Send the blocking work to a dedicated pool using Schedulers.boundedElastic()."

RU:

> "Если вы пока не можете перейти на R2DBC (настоящее реактивное решение), вы должны изолировать этот блокирующий JPA/JDBC-вызов. 
>
> Классический выход: отправить блокирующую работу в отдельный пул через Schedulers.boundedElastic()."

## пример с реактивным кодом

Если `TransactionRepository` — это реактивный R2DBC-репозиторий (`ReactiveCrudRepository`), код должен быть таким, без лишнего `subscribeOn`:

```java
@Service
class TransactionImportService {

    private final TransactionRepository transactionRepository; // R2DBC-репозиторий, реактивный

    Flux<Void> importTransactions(Flux<Transaction> fileStream) {
        return fileStream
            .limitRate(50) // запрашиваем у файлового источника не более 50 строк за раз
            .flatMap(transaction ->
                transactionRepository.save(transaction) // уже реактивный вызов — не блокирует поток
            )
            .thenMany(Flux.empty());
    }
}
```

`subscribeOn(Schedulers.boundedElastic())` возвращается в код только если `transactionRepository` — обычный блокирующий JPA/JDBC-репозиторий, обёрнутый в `Mono.fromCallable(...)`, как было в исходном примере до этого уточнения.


---


<a id="strategii-onbackpressurexxx"></a>
## Стратегии onBackpressureXxx

Если **downstream** всё-таки запросил слишком много (например, `Long.MAX_VALUE`), а источник продолжает генерировать элементы быстрее, чем они обрабатываются, применяются операторы `onBackpressureXxx` — они решают, что делать с уже произведённым избытком.

<a id="onbackpressurebuffer"></a>
### onBackpressureBuffer

**Копит** непринятые элементы **во внутренней очереди**, не теряя их, пока **downstream** не заберёт их позже. 
- Бизнес-кейс:
   - сервис отправляет заказы в Kafka, и если брокер временно тормозит, заказы важно не потерять, а придержать в буфере:

```java
@Service
class OrderEventPublisher {

    private final KafkaSender<String, OrderEvent> kafkaSender;

    Flux<Void> publishOrders(Flux<OrderEvent> orderEvents) {
        return orderEvents
            .onBackpressureBuffer(1000) // копим до 1000 неотправленных заказов, не теряя их
            .flatMap(event -> kafkaSender.send(Mono.just(toSenderRecord(event))).then());
    }
}
```

Риск переполнения буфера приводит к `OverflowException` — если брокер Kafka недоступен слишком долго, накопленные заказы придётся сбрасывать в dead-letter очередь вручную.

<a id="onbackpressuredrop"></a>
### onBackpressureDrop

Отбрасывает избыточные элементы без накопления — подходит для данных, потеря части которых некритична. 
 - Бизнес-кейс: 
   - сервис собирает метрики нагрузки с серверов раз в секунду, и если writer в Prometheus не успевает — лишние промежуточные значения можно просто выбросить:

```java
@Service
class MetricsCollector {

    private final MetricsWriter metricsWriter;

    Flux<Void> collectServerMetrics(Flux<ServerMetric> metricStream) {
        return metricStream
            .onBackpressureDrop(dropped -> log.warn("dropped metric: {}", dropped)) // лишние метрики просто теряются
            .flatMap(metric -> metricsWriter.write(metric));
    }
}
```

<a id="onbackpressurelatest"></a>
### onBackpressureLatest

Сохраняет только самое последнее значение среди непринятых элементов, отбрасывая промежуточные. 
- Бизнес-кейс: 
   - dashboard поддержки отображает текущий статус обработки заявки — важен только последний статус, а не вся история промежуточных обновлений:

```java
@Service
class TicketStatusDashboard {

    private final DashboardRenderer dashboardRenderer;

    Flux<Void> renderTicketStatus(Flux<TicketStatus> statusUpdates) {
        return statusUpdates
            .onBackpressureLatest() // держим только последний статус заявки, промежуточные не важны
            .flatMap(status -> dashboardRenderer.update(status));
    }
}
```

<a id="pull-vs-push-rezhimy"></a>
## Pull vs Push режимы

Reactive Streams **динамически** переключается между **pull**-режимом (когда downstream ограничивает спрос через `request(n)`) и push-режимом (когда downstream постоянно запрашивает с запасом и издатель просто шлёт данные по готовности).

Источник: https://medium.com/@knoldus/backpressure-in-akka-stream-294e5f045e07

EN:

> "Reactive Streams — whenever we come across these words, there are two things that come to our mind. The first is asynchronous stream processing, and the second is non-blocking backpressure."

RU:

> "Reactive Streams — когда мы встречаем эти слова, на ум приходят две вещи. 
> Первая — асинхронная обработка потоков, вторая — неблокирующий backpressure."

<a id="backpressure-s-nepolnostyu-reaktivnym-istochnikom"></a>
## Backpressure с неполностью реактивным источником

Если источник данных — блокирующий REST-клиент, JDBC-курсор или внешний сервис, который не понимает `request(n)`, **backpressure** нужно эмулировать вручную: 
 - через **пагинацию** (следующая страница запрашивается только по требованию) или через явную приостановку источника, как это делают с Kafka-consumer'ами при перегруженном downstream.

Источник: https://github.com/reactor/reactor-kafka/issues/190

EN:

> "I have a reactive kafka consumer which consumes data from a topic and pushes it to an endpoint. However, these endpoints are flaky..."

RU:

> "У меня есть реактивный kafka-консьюмер, который потребляет данные из топика и отправляет их в эндпоинт. Однако эти эндпоинты нестабильны..."

 - Бизнес-кейс: 
   - сервис вычитывает историю платежей клиента из внешнего платёжного API, который отдаёт данные страницами. 
   - Вместо того чтобы забрать все страницы сразу, следующая страница запрашивается только когда downstream готов её обработать:

```java
@Service
class PaymentHistoryService {

    private final PaymentGatewayClient paymentGatewayClient; // блокирующий REST-клиент

    Flux<PaymentRecord> fetchPaymentHistory(String customerId) {
        return Flux.defer(() -> Mono.fromCallable(() -> paymentGatewayClient.fetchPage(customerId, 0)))
            .expand(page -> page.hasNext()
                ? Mono.fromCallable(() -> paymentGatewayClient.fetchPage(customerId, page.nextPageNumber()))
                : Mono.empty())
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapIterable(PaymentPage::records);
    }
}
```
---


## Почему там customerId и как он передаётся

`customerId` — обычный параметр метода `fetchPaymentHistory`, который замыкается (захватывается) лямбдами через **closure** — то есть каждая лямбда внутри `Flux.defer` и `.expand(...)` видит его напрямую, без необходимости передавать явно. 
- Первый вызов всегда идёт на страницу `0`, потому что это стартовая точка пагинации — почти все REST API с пагинацией начинают нумерацию страниц с нуля.

## Как работает expand и откуда берётся следующая страница

`expand` рекурсивно применяет функцию к каждому элементу и эмитит все промежуточные результаты, используя обход в ширину (breadth-first) — то есть каждая новая страница, которую возвращает функция, сама подставляется в ту же функцию, пока не встретится условие остановки.

Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html

EN:

> "Recursively expand elements into a graph and emit all the resulting element using a breadth-first traversal strategy."

RU:

> "Рекурсивно раскрывает элементы в граф и эмитит все получившиеся элементы, используя стратегию обхода в ширину (breadth-first)."



```java
/**
 * Представляет одну страницу истории платежей, полученную от внешнего платёжного API.
 */
record PaymentPage(
    int pageNumber,               // номер ТЕКУЩЕЙ страницы, которую мы только что получили
    boolean hasNext,              // флаг из ответа API: есть ли следующая страница
    List<PaymentRecord> records   // сами записи платежей на этой странице
) {
    // API возвращает hasNext=false, когда достигнута последняя страница —
    // именно на этом флаге останавливается expand()
    int nextPageNumber() {
        return pageNumber + 1;
    }
}

@Service
class PaymentHistoryService {

    private final PaymentGatewayClient paymentGatewayClient; // блокирующий REST-клиент

    /**
     * Загружает всю историю платежей клиента постранично.
     *
     * Механика:
     * 1) Flux.defer откладывает вызов до момента подписки — иначе первая страница
     *    была бы запрошена ещё на этапе СБОРКИ цепочки, а не при подписке.
     * 2) fetchPage(customerId, 0) — стартовая страница; customerId захватывается
     *    из параметра метода через closure, а 0 — это номер первой страницы API.
     * 3) expand(...) вызывается для КАЖДОЙ полученной страницы: если page.hasNext()
     *    вернул true — запрашивается следующая страница (pageNumber + 1),
     *    если false — Mono.empty() останавливает рекурсию для этой ветки.
     * 4) subscribeOn(boundedElastic) снимает блокирующий вызов REST-клиента
     *    с event-loop потока.
     * 5) flatMapIterable разворачивает список records на каждой странице
     *    в отдельные элементы Flux<PaymentRecord>.
     */
    Flux<PaymentRecord> fetchPaymentHistory(String customerId) {
        return Flux.defer(() ->
                Mono.fromCallable(() -> paymentGatewayClient.fetchPage(customerId, 0)))
            .expand(page -> page.hasNext()
                ? Mono.fromCallable(() ->
                    paymentGatewayClient.fetchPage(customerId, page.nextPageNumber()))
                : Mono.empty())
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapIterable(PaymentPage::records);
    }
}
```


## Пошаговая трассировка на конкретных данных

Допустим, у клиента 3 страницы платежей:

- Шаг 1: `fetchPage(customerId, 0)` → возвращает `PaymentPage(pageNumber=0, hasNext=true, records=[...])`.
- Шаг 2: `expand` видит `hasNext=true` → вызывает `fetchPage(customerId, 1)` (`nextPageNumber()` вернул `0+1`) → `PaymentPage(pageNumber=1, hasNext=true, records=[...])`.
- Шаг 3: `expand` снова видит `hasNext=true` → `fetchPage(customerId, 2)` → `PaymentPage(pageNumber=2, hasNext=false, records=[...])`.
- Шаг 4: `expand` видит `hasNext=false` → возвращает `Mono.empty()` → рекурсия останавливается.
- Итог: `flatMapIterable` разворачивает записи всех трёх страниц в единый `Flux<PaymentRecord>`.

Именно так `expand` реализует backpressure-совместимую пагинацию: 
 - следующая страница запрашивается только тогда, когда предыдущая обработана и её `hasNext` проверен, а не все страницы сразу.

---


<a id="itogovaya-sravnitelnaya-tablitsa"></a>
## Итоговая сравнительная таблица

| Способ | Бизнес-пример                                                          | Что происходит с избытком |
|---|------------------------------------------------------------------------|---|
| Ручной `request(n)` | Обработчик заявок берёт ровно столько заявок, сколько может обработать | Источник не производит лишнего |
| `limitRate(n)` | Импорт транзакций из файла порциями по 50 строк                        | То же — источник не производит лишнего |
| `onBackpressureBuffer` | Отправка заказов в Kafka с буфером на случай задержки брокера          | Хранится до переполнения, потом ошибка |
| `onBackpressureDrop` | Сбор серверных метрик — лишние промежуточные значения не критичны      | Теряется без сохранения |
| `onBackpressureLatest` | Dashboard статуса заявки — важен только последний статус               | Все промежуточные значения теряются |
