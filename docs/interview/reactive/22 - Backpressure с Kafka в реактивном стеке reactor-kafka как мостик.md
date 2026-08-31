# Backpressure с Kafka в реактивном стеке reactor-kafka как мостик

## Содержание

- [Question–Answer](#question-answer)
- [Почему обычная Kafka не имеет встроенного backpressure](#pochemu-obychnaya-kafka-ne-imeet-backpressure)
- [Как reactor-kafka решает проблему](#kak-reactor-kafka-reshaet-problemu)
- [Отличие от реактивного драйвера БД](#otlichie-ot-r2dbc)
- [Бизнес-пример: обработка событий заказов](#biznes-primer-zakazy)
- [Важный нюанс: concatMap с prefetch](#nyuans-concatmap-prefetch)
- [Дополнительные пояснения](#dopolnitelnye-poyasneniya)

---

<a name="question-answer"></a>
## Question–Answer

**Вопрос.** В исходном тексте обычный нереактивный консьюмер описан через «пул-модель» и одновременно через запрос `poll`. Чем отличаются `pull`, `poll()` и `pool`? Как reactor-kafka на самом деле регулирует backpressure, если `receive()` отдал, например, 10 заказов, а дальше `flatMap`/`concatMap` пишет их в БД? Верно ли, что при «синхронной» записи в БД реактивный Kafka-консьюмер сам ждёт и больше не делает запрос?

**Ответ.** Это три разных слова. `pull` — модель «консьюмер сам вытягивает данные у брокера». `poll()` — метод Java-клиента, которым консьюмер спрашивает брокер: «есть ли сейчас записи?». `pool` — пул потоков или пул соединений, к Kafka-модели потребления не относится. Backpressure в reactor-kafka — это не «консьюмер завис на записи в БД». Пока обработка реактивная, поток консьюмера продолжает вызывать `poll()` для heartbeat. Если downstream больше не делает `request(n)`, reactor-kafka вызывает `pause()`: `poll()` идёт дальше, но по поставленным на паузу партициям новые записи не возвращаются. Если запись в БД блокирующая (JDBC на том же потоке), это уже не backpressure, а блокировка потока: `poll()` может не вызываться вовремя, и группу могут перебалансировать.

---

<a name="pochemu-obychnaya-kafka-ne-imeet-backpressure"></a>
## Почему обычная Kafka не имеет встроенного backpressure

### Три слова, которые легко перепутать

| Слово | Произношение | Что это | Роль |
|---|---|---|---|
| `pull` | «пулл», вытягивание | Модель доставки | Консьюмер сам забирает данные у брокера, брокер их не проталкивает |
| `poll()` | «полл», опрос | Метод `KafkaConsumer` | Один вызов: «брокер, отдай готовые записи, но не больше лимитов» |
| `pool` | «пул» | Пул ресурсов | Пул потоков, пул JDBC-соединений. К модели Kafka не относится |

Обычный `KafkaConsumer` работает по модели `pull`: приложение само решает, когда вытянуть очередную порцию. Технический способ вытянуть порцию — вызвать `poll()`. Это не «пул-модель» и не пул потоков.

https://kafka.apache.org/43/design/design/

**EN:**

> An initial question we considered is whether consumers should pull data from brokers or brokers should push data to the consumer. In this respect Kafka follows a more traditional design, shared by most messaging systems, where data is pushed to the broker from the producer and pulled from the broker by the consumer.

**RU:**

> Сначала мы спрашивали себя: консьюмеры должны вытягивать данные у брокеров, или брокеры должны проталкивать данные консьюмеру. Kafka идёт по более традиционному пути: продюсер проталкивает данные брокеру, а консьюмер вытягивает их у брокера.

Один вызов `poll()` возвращает пачку, ограниченную `max.poll.records`, `fetch.max.bytes` и `max.partition.fetch.bytes`. Это ограничение размера одной порции, а не Reactive Streams `request(n)`. Пока код крутит цикл `while (true) { poll(); }`, он снова и снова вытягивает данные, даже если предыдущая пачка ещё не обработана.

https://kafka.apache.org/37/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html

**EN:**

> The poll API is designed to ensure consumer liveness. As long as you continue to call poll, the consumer will stay in the group and continue to receive messages from the partitions it was assigned.

**RU:**

> API `poll` нужен ещё и для того, чтобы консьюмер считался живым. Пока вы продолжаете вызывать `poll`, консьюмер остаётся в группе и получает сообщения с назначенных партиций.

Проблема реактивного стека не в том, что Kafka «не умеет pull». Проблема в том, что протокол консьюмера не понимает сигнал `request(n)` от Reactor. Если обернуть `poll()` в `Flux.create` и класть записи в sink, не глядя на спрос downstream, записи начнут копиться в памяти быстрее, чем их успевает обработать запись в БД.

```java
// Так делать нельзя: poll() крутится сам по себе и не смотрит на request(n)
Flux<ConsumerRecord<String, String>> rawStream = Flux.create(sink -> {
    while (true) {
        ConsumerRecords<String, String> batch = consumer.poll(Duration.ofMillis(100));
        // poll() - опрашиваем брокера и забираем готовую пачку
        batch.forEach(sink::next);
        // next - кладём запись в Flux, даже если downstream ещё занят записью заказа в склад
    }
});

rawStream
    .onBackpressureBuffer(1000, BufferOverflowStrategy.DROP_OLDEST)
    // buffer(1000) - аварийный буфер в памяти приложения, это не пауза Kafka
    // DROP_OLDEST - при переполнении выкидывает старые заказы, для склада это потеря данных
    .subscribe(record -> processOrder(record));
```

**Вывод:** `pull` даёт консьюмеру контроль «когда спросить». Сам по себе он не связывает этот вопрос со скоростью БД. Буфер Reactor только прячет переполнение в памяти приложения. Брокер при этом не тормозит продюсеров: непрочитанные заказы остаются в топике как consumer lag.

---

<a name="kak-reactor-kafka-reshaet-problemu"></a>
## Как reactor-kafka решает проблему

`KafkaReceiver` — это мостик. Снаружи вы видите `Flux<ReceiverRecord>` и обычный Reactive Streams `request(n)`. Внутри остаётся тот же небезопасный для многопоточности `KafkaConsumer`: все `poll()`, `pause()` и `resume()` идут в одном `ConsumerEventLoop` на отдельном планировщике reactor-kafka, а не на Netty event loop.

https://projectreactor.io/docs/kafka/release/reference/

**EN:**

> Reactor Kafka API enables messages to be published to Kafka and consumed from Kafka using functional APIs with non-blocking back-pressure and very low overheads.

**RU:**

> API Reactor Kafka позволяет публиковать сообщения в Kafka и читать их из Kafka функциональными методами с неблокирующим backpressure и очень небольшими накладными расходами.

Важно: цитата выше говорит о неблокирующем backpressure Reactor. Для консьюмера это реализовано не остановкой `poll()`, а паузой партиций.

https://kafka.apache.org/37/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html

**EN:**

> Kafka supports dynamic controlling of consumption flows by using pause(Collection) and resume(Collection) to pause the consumption on the specified assigned partitions and resume the consumption on the specified paused partitions respectively in the future poll(Duration) calls.

**RU:**

> Kafka позволяет динамически управлять потоком потребления через `pause` и `resume`: потребление указанных партиций приостанавливается, а в следующих вызовах `poll` эти партиции снова начинают отдавать записи только после `resume`.

Тот же javadoc прямо рекомендует этот приём, если обработка идёт в другом потоке: продолжать вызывать `poll()`, но `pause()` партиции, пока предыдущие записи не обработаны.

https://github.com/reactor/reactor-kafka/blob/main/src/main/java/reactor/kafka/receiver/internals/ConsumerEventLoop.java

В `PollEvent.run()` логика такая:

1. Смотрим счётчик `requested` — это накопленный спрос downstream.
2. Если `requested > 0`, при необходимости вызываем `consumer.resume(...)`.
3. Если `requested == 0`, вызываем `consumer.pause(consumer.assignment())` и пишем в лог `Paused - back pressure`.
4. Затем всё равно вызываем `consumer.poll(pollTimeout)`.
5. Если пачка не пустая, уменьшаем `requested` и эмитим её в sink.

https://github.com/reactor/reactor-kafka/issues/108

**EN:**

> That appears to be far away from the reactor-kafka code, where back pressure is implemented by pausing the consumer; nothing is emitted from the receiver if there are no available requests in the ConsumerEventLoop.

**RU:**

> Это далеко от кода reactor-kafka: backpressure там сделан через паузу консьюмера; если в `ConsumerEventLoop` нет доступных запросов, receiver ничего не эмитит.

### По шагам: 10 заказов и запись в БД

Берём сервис склада интернет-магазина. Топик `orders-topic` присылает события «списать товар». Дальше `concatMap` или `flatMap` пишет в PostgreSQL.

```java
receiver.receive()
    // receive() - Flux заказов; спрос request(n) доходит до ConsumerEventLoop
    .concatMap(record ->
        warehouseRepository.decrementStock(record.value().getSku(), record.value().getQty())
            // decrementStock - реактивный R2DBC Mono: поток не блокируется, пока БД думает
            .thenReturn(record),
        4
        // prefetch=4 - заранее держим до 4 заказов, но в работу берём их по одному
    )
    .subscribe();
```

Шаги, когда БД занята, а обработка **неблокирующая**:

1. `subscribe()` запускает консьюмер. Downstream делает `request`, в `ConsumerEventLoop` растёт `requested`.
2. Цикл `PollEvent` видит `requested > 0`, делает `resume()` при необходимости и вызывает `poll()`.
3. `poll()` возвращает, например, 10 заказов. Они уходят в `Flux`. Это уже вытянутые записи: они в памяти консьюмера, а не «ещё на брокере в этом запросе».
4. `concatMap(..., 4)` начинает первый `Mono` записи в склад и держит ещё несколько заказов как prefetch.
5. Пока `Mono` не завершился, `concatMap` не берёт следующий заказ в работу. Когда локальный спрос удовлетворён, вверх перестаёт идти новый `request(n)`.
6. `requested` падает до 0. Следующий `PollEvent` вызывает `pause()` и сразу после этого снова `poll()`.
7. `poll()` на паузе не приносит новые заказы с этих партиций, но обслуживает членство в группе: heartbeat, rebalance, commit.
8. PostgreSQL отвечает, `Mono` завершается, `concatMap` запрашивает следующий заказ. `onRequest` увеличивает `requested`, будит консьюмер через `wakeup()`, затем `resume()`.
9. Следующий `poll()` снова может вернуть записи.

Что здесь **не** происходит: Kafka-консьюмер не «стоит на JDBC-вызове и поэтому не делает poll». Он как раз продолжает делать `poll()`, только с паузой на fetch.

### Чем это не является: блокирующая запись в БД

Если вместо R2DBC вызвать блокирующий JDBC внутри `flatMap` на том же потоке, картина другая.

```java
receiver.receive()
    .flatMap(record -> {
        jdbcWarehouseDao.decrementStock(record.value());
        // jdbcWarehouseDao - блокирует текущий поток, пока Postgres не ответит
        return Mono.just(record);
    })
    .subscribe();
```

Тогда поток `ConsumerEventLoop` или тот поток, куда вы переключили цепочку, занят ожиданием сокета БД. `poll()` в это время может не вызываться. Это не `pause()` и не backpressure Reactor. Это блокировка. По документации `KafkaConsumer` если долго не вызывать `poll()`, сработает `max.poll.interval.ms`, консьюмера сочтут мёртвым и отберут партиции.

**Вывод:** reactor-kafka связывает `request(n)` с `pause()`/`resume()`, а не с остановкой цикла `poll()`. Пауза работает только если обработка не блокирует поток консьюмера. Для склада это значит: писать остатки через R2DBC/`Mono`, а не через JDBC на потоке reactor-kafka.

---

<a name="otlichie-ot-r2dbc"></a>
## Отличие от реактивного драйвера БД

| Аспект | R2DBC PostgreSQL | reactor-kafka |
|---|---|---|
| Есть ли `request(n)` в самом протоколе источника | У потока результата запроса спрос Reactor может остановить чтение следующих строк | У Kafka-консьюмера нет Reactive Streams. Спрос эмулируется через `pause`/`resume` |
| Что происходит при отсутствии спроса | Драйвер не забирает следующую порцию результата | `poll()` продолжается, но поставленные на паузу партиции не отдают новые записи |
| Где действует | Один запрос или курсор на соединении | Назначенные партиции данного `KafkaConsumer` |
| Гранулярность | Ближе к строкам результата | Побатчево: `poll()` может вернуть до `max.poll.records` сразу |
| Где крутится цикл | Netty event loop / пул R2DBC | Отдельный `eventScheduler` reactor-kafka, не Netty event loop |

**Вывод:** R2DBC ближе к «настоящему» реактивному спросу на уровне чтения из соединения. Kafka остаётся `pull`+`poll()`, а reactor-kafka добавляет сверху тормоз `pause()`, чтобы не вытягивать новую пачку, пока склад не переварил уже взятые заказы.

---

<a name="biznes-primer-zakazy"></a>
## Бизнес-пример: обработка событий заказов

Сервис склада читает `OrderEvent` из `orders-topic` и для каждого заказа уменьшает остаток через R2DBC. Без связи спроса с `pause()` быстрый топик заказов раздует число одновременных запросов к PostgreSQL.

```java
ReceiverOptions<String, OrderEvent> options = ReceiverOptions
    .<String, OrderEvent>create(kafkaProps)
    .subscription(Collections.singleton("orders-topic"));
    // subscription - этот receiver читает только топик заказов

KafkaReceiver<String, OrderEvent> receiver = KafkaReceiver.create(options);

receiver.receive()
    .concatMap(record ->
        warehouseRepository.decrementStock(record.value().getSku(), record.value().getQty())
            // concatMap - следующий заказ не стартует, пока текущий Mono склада не завершится
            .doOnSuccess(v -> record.receiverOffset().acknowledge())
            // acknowledge - помечаем offset обработанным после успешного списания
            .thenReturn(record.value()),
        4
        // prefetch=4 - заранее берём до 4 событий, чтобы не простаивать между ответами БД
    )
    .doOnNext(order -> log.info("Обработан заказ {}", order.getId()))
    .subscribe();
```

Пока `decrementStock()` ждёт Postgres, EventLoop приложения свободен: он не сидит на блокирующем `executeUpdate`. Свободен и поток `ConsumerEventLoop`: он продолжает `poll()` с `pause()`, если спрос уже исчерпан. Когда ответ БД приходит, `concatMap` запрашивает следующий заказ, reactor-kafka делает `resume()`, и только тогда `poll()` снова приносит записи.

Если вместо `concatMap` поставить `map` и внутри вызвать блокирующий JDBC, поток обработки остановится на сокете БД. Это не пауза Kafka. Остальные реактивные подписки на этом же потоке тоже встанут.

**Вывод:** для склада правильный тормоз — реактивная запись плюс ограниченный спрос `concatMap`/`flatMap`. Тогда Kafka перестаёт вытягивать новые заказы через `pause()`, а не через зависание потока.

---

<a name="nyuans-concatmap-prefetch"></a>
## Важный нюанс: concatMap с prefetch

У `concatMap(mapper)` значение prefetch по умолчанию — 32. Это не «безлимит», но для Kafka часто слишком много: десятки незакоммиченных заказов уже вытянуты `poll()`, лежат в памяти и ждут БД. При падении процесса их можно обработать повторно.

https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#concatMap-java.util.function.Function-int-

https://github.com/reactor/reactor-kafka/issues/228

**EN:**

> You can't use manual commits if you go async, for the reason you state, unless you keep track of the offsets. Here's how to apply back-pressure: receiver.receive().concatMap(rr -> Mono.delay(...).then(), 10).subscribe();

**RU:**

> При асинхронной обработке нельзя просто так делать ручной commit по указанной причине, если вы сами не отслеживаете offset'ы. Вот как включить backpressure: `receiver.receive().concatMap(rr -> Mono.delay(...).then(), 10).subscribe();`

```java
receiver.receive()
    .concatMap(record ->
        processOrderAsync(record.value())
            // processOrderAsync - Mono списания со склада, без блокировки потока
            .doOnSuccess(v -> record.receiverOffset().acknowledge())
            // acknowledge - offset попадёт в пачку коммитов только после успеха
        , 10
        // prefetch=10 - не больше 10 заказов одновременно «в полёте» без commit
    )
    .subscribe();
```

`flatMap` здесь опаснее `concatMap`, если не ограничить concurrency. `flatMap(mapper)` по умолчанию держит до 256 внутренних издателей сразу. Для склада это 256 параллельных записей в PostgreSQL и большой спрос к `receive()`, то есть `pause()` наступит гораздо позже.

Ещё одна граница: даже при маленьком prefetch один `poll()` может вернуть пачку до `max.poll.records`. Эти записи уже вытянуты. `pause()` не отменяет их, а только останавливает следующие fetch. Поэтому `max.poll.records` тоже нужно согласовать с тем, сколько заказов склад реально переваривает за раз.

**Вывод:** prefetch и `max.poll.records` задают, сколько заказов уже взято у Kafka до паузы. `pause()` тормозит следующую вытяжку, а не откатывает уже полученную пачку. Ручной commit при асинхронной обработке требует либо `acknowledge()` после успеха, либо собственной дисциплины offset'ов.

---

<a name="dopolnitelnye-poyasneniya"></a>
## Дополнительные пояснения

В исходном тексте `pull` и `poll()` были смешаны так, что читалось как «пул-модель» и «запрос пол». Это разные термины: модель вытягивания, метод опроса и пул ресурсов.

Формулировка «обычная Kafka не имеет backpressure» уточнена. У консьюмера есть контроль скорости через то, как часто вызывать `poll()`, но нет сигнала Reactive Streams. Без мостика этот контроль не связан со скоростью записи в БД.

Цикл `poll()` нельзя путать с Netty event loop. У reactor-kafka свой `eventScheduler`. Пауза не останавливает этот цикл: в исходнике `ConsumerEventLoop.PollEvent` сначала `pause()`/`resume()`, затем `poll()`.

Сценарий «получил 10 элементов, дальше запись в БД, Kafka ждёт» разведён на два случая. Реактивная БД: спрос падает, `pause()`, `poll()` живой, новых записей нет. Блокирующая БД: это не ожидание Kafka, а занятый поток; так backpressure не работает.

Неофициальная ссылка про pull-модель заменена на дизайн Apache Kafka. Для `pause()`/`resume()` использован javadoc `KafkaConsumer`, для мостика — reference reactor-kafka и `ConsumerEventLoop`.
