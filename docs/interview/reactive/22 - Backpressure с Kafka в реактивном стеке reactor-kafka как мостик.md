# Backpressure с Kafka в реактивном стеке reactor-kafka как мостик

## Содержание

- [Почему обычная Kafka не имеет встроенного backpressure](#pochemu-obychnaya-kafka-ne-imeet-backpressure)
- [Как reactor-kafka решает проблему](#kak-reactor-kafka-reshaet-problemu)
- [Отличие от реактивного драйвера БД](#otlichie-ot-r2dbc)
- [Бизнес-пример: обработка событий заказов](#biznes-primer-zakazy)
- [Важный нюанс: concatMap с prefetch](#nyuans-concatmap-prefetch)

---

<a name="pochemu-obychnaya-kafka-ne-imeet-backpressure"></a>
## Почему обычная Kafka не имеет встроенного backpressure

Стандартный `KafkaConsumer` работает по pull-модели: он сам вызывает `poll()` и получает пачку записей, ограниченную `max.poll.records`, `fetch.max.bytes` и `max.partition.fetch.bytes`, то есть консьюмер никогда не получает больше, чем сам запросил за один вызов.

https://www.skillveris.com/interview-questions/kafka/how-kafka-handles-backpressure-and-slow-consumers

**EN:**
> Kafka's pull-based model gives consumers control over their own consumption rate: a consumer only fetches as many records as it explicitly polls for, rather than having messages pushed to it.

**RU:**
> Pull-модель Kafka даёт консьюмеру контроль над собственной скоростью потребления: консьюмер получает ровно столько записей, сколько сам явно запросил через poll, а не столько, сколько ему «проталкивает» брокер.

Проблема в том, что после получения батча из `poll()` эти записи нужно обработать до следующего вызова `poll()` — если обработка асинхронная (как в реактивном стеке), консьюмер продолжает крутить цикл и накапливать записи в памяти EventLoop'а быстрее, чем downstream успевает их обрабатывать.

```java
// Классический (не реактивный) подход - вручную ограничиваем буфер,
// чтобы EventLoop не захлебнулся входящими записями из Kafka
Flux<ConsumerRecord<String, String>> rawStream = Flux.create(sink -> {
    // consumer.poll(...) кладёт записи в sink без учёта готовности downstream
});

rawStream
    .onBackpressureBuffer(1000, BufferOverflowStrategy.DROP_OLDEST)
    // buffer(1000) - ограничивает буфер 1000 элементами,
    // DROP_OLDEST - при переполнении выкидывает старые записи,
    // теряя данные, что для заказов недопустимо
    .subscribe(record -> processOrder(record));
```

**Вывод:** такой буфер решает проблему переполнения памяти механически, но ценой потери данных или искусственной задержки — сам Kafka-протокол при этом продолжает отдавать записи с той же скоростью, независимо от готовности приложения их обработать.

---

<a name="kak-reactor-kafka-reshaet-problemu"></a>
## Как reactor-kafka решает проблему

`KafkaReceiver.receive()` оборачивает нативный `KafkaConsumer` в `Flux<ReceiverRecord>` и напрямую связывает Reactive Streams `request(n)` с вызовом `pause()`/`resume()` на consumer'е.

https://projectreactor.io/docs/kafka/release/reference/

**EN:**
> The Reactor Kafka API benefits from non-blocking back-pressure provided by Reactor. For example, in a pipeline, where messages received from an external source are published to Kafka, back-pressure can be applied easily to the whole pipeline, limiting the number of messages in-flight and controlling memory usage.

**RU:**
> API Reactor Kafka использует неблокирующий backpressure, предоставляемый Reactor. Например, в пайплайне, где сообщения из внешнего источника публикуются в Kafka, backpressure можно легко применить ко всему пайплайну, ограничивая количество сообщений «в полёте» и контролируя использование памяти.

```java
ReceiverOptions<String, OrderEvent> options = ReceiverOptions
    .<String, OrderEvent>create(kafkaProps)
    .subscription(Collections.singleton("orders-topic"));

KafkaReceiver<String, OrderEvent> receiver = KafkaReceiver.create(options);

Flux<OrderEvent> orderEvents = receiver.receive()
    // receive() - создаёт Flux, где request(n) от downstream
    // транслируется в pause()/resume() на нативном KafkaConsumer
    .map(ReceiverRecord::value);
    // map - извлекаем полезную нагрузку (OrderEvent) из ReceiverRecord,
    // сама обёртка ReceiverRecord нужна была только для доступа
    // к offset и метаданным партиции
```

**Вывод:** когда downstream перестаёт запрашивать элементы (например, обработка заказа занимает много времени), reactor-kafka вызывает `pause()` на соответствующих партициях — `poll()` продолжает вызываться для heartbeat, но новые записи по этим партициям не возвращаются, пока downstream не восстановит спрос через `resume()`.

---

<a name="otlichie-ot-r2dbc"></a>
## Отличие от реактивного драйвера БД

| Аспект | R2DBC (например, Postgres) | reactor-kafka |
|---|---|---|
| Уровень backpressure | Прямо в протоколе — драйвер не читает следующую строку из TCP-сокета, пока нет request(n) | Эмулируется через pause/resume партиций, так как сам протокол Kafka-консьюмера pull-based |
| Где применяется | На уровне отдельного запроса или курсора | На уровне назначенных партиций конкретного KafkaConsumer |
| Гранулярность | Построчно, точный контроль | Побатчево (max.poll.records), pause действует на партицию целиком |

https://github.com/reactor/reactor-kafka/issues/108

**EN:**
> That appears to be far away from the reactor-kafka code, where back pressure is implemented by pausing the consumer; nothing is emitted from the consumer while it's paused.

**RU:**
> Это далеко от кода reactor-kafka, где backpressure реализован через постановку консьюмера на паузу; пока консьюмер на паузе, от него не эмитится ни одной записи.

---

<a name="biznes-primer-zakazy"></a>
## Бизнес-пример: обработка событий заказов

Представим сервис, который читает события заказов из топика Kafka и для каждого заказа делает реактивный запрос к БД через R2DBC, чтобы обновить статус склада. Без правильного backpressure высокая скорость поступления заказов из Kafka может перегрузить пул соединений к БД.

```java
receiver.receive()
    .map(ReceiverRecord::value)
    // map - конвертируем ReceiverRecord в чистый OrderEvent
    .concatMap(order ->
        warehouseRepository.decrementStock(order.getSku(), order.getQty())
            // concatMap - обрабатываем заказы строго по одному:
            // следующий заказ не начнёт обрабатываться,
            // пока текущий запрос к БД не завершится,
            // это гарантирует, что склад обновляется в правильном порядке
            .thenReturn(order),
        4
        // prefetch=4 - concatMap заранее запрашивает у receive()
        // до 4 событий заказов, чтобы не терять время на ожидание
        // между завершением одного запроса к БД и стартом следующего
    )
    .doOnNext(order -> log.info("Обработан заказ {}", order.getId()))
    .subscribe();
```

**Вывод:** пока `warehouseRepository.decrementStock()` выполняется (например, ждёт ответа от БД), `concatMap` не запрашивает следующий заказ активно для обработки, но за счёт prefetch=4 уже держит наготове до 4 событий в буфере — это значит, что как только текущий запрос завершится, следующий заказ обрабатывается без задержки на сетевой round-trip к Kafka. Если бы вместо `concatMap` использовался обычный `map` без ограничения скорости, а внутренний вызов к БД был бы блокирующим, EventLoop оказался бы заблокирован, и все остальные подключения приложения зависли бы.

---

<a name="nyuans-concatmap-prefetch"></a>
## Важный нюанс: concatMap с prefetch

При использовании `concatMap` вместе с `KafkaReceiver.receive()` необходимо явно указывать prefetch, иначе накопится слишком много незакоммиченных in-flight записей, ожидающих обработки.

https://github.com/reactor/reactor-kafka/issues/228

**EN:**
> You can't use manual commits if you go async, for the reason you state, unless you keep track of the offsets. Here's how to apply back-pressure: receiver.receive().concatMap(rr -> Mono.delay(...).then(), 10).subscribe();

**RU:**
> Нельзя использовать ручной commit при асинхронной обработке по указанной причине, если только вы не отслеживаете offset'ы самостоятельно. Вот как применить backpressure: receiver.receive().concatMap(rr -> Mono.delay(...).then(), 10).subscribe();

```java
receiver.receive()
    .concatMap(record ->
        processOrderAsync(record.value())
            // processOrderAsync - асинхронная бизнес-логика обработки заказа,
            // возвращает Mono<Void> после завершения
            .doOnSuccess(v -> record.receiverOffset().acknowledge())
            // acknowledge() - помечаем offset как обработанный,
            // но НЕ коммитим сразу в Kafka - копится в буфере коммитов
        , 10
        // prefetch=10 - ограничиваем количество in-flight записей,
        // которые могут быть "в обработке" одновременно без commit,
        // без этого лимита при медленной обработке накопится
        // неограниченное число offset'ов без подтверждения
    )
    .subscribe();
```

**Вывод:** ограничение prefetch напрямую связано с проблемой ручного commit при асинхронной обработке — если не отслеживать offset'ы самостоятельно и не ограничивать количество記 «висящих» в обработке записей, при сбое приложения можно потерять данные о том, какие сообщения были обработаны, а какие нет, поскольку commit в Kafka происходит по offset, а не по конкретному сообщению.
