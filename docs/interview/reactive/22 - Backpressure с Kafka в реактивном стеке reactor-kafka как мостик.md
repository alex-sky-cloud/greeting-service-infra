# Backpressure с Kafka в реактивном стеке reactor-kafka как мостик

## Содержание

- [Сначала: кто с кем взаимодействует](#сначала-кто-с-кем-взаимодействует)
- [Что происходит при обычной обработке](#что-происходит-при-обычной-обработке)
- [Что такое backpressure здесь](#что-такое-backpressure-здесь)
- [Что значит блокирующая БД](#что-значит-блокирующая-бд)
- [Главное различие](#главное-различие)
- [Почему обычная Kafka не имеет встроенного backpressure](#pochemu-obychnaya-kafka-ne-imeet-backpressure)
- [Как reactor-kafka решает проблему](#kak-reactor-kafka-reshaet-problemu)
- [Отличие от реактивного драйвера БД](#otlichie-ot-r2dbc)
- [Бизнес-пример: обработка событий заказов](#biznes-primer-zakazy)
- [Важный нюанс: concatMap с prefetch](#nyuans-concatmap-prefetch)
- [Дополнительные пояснения](#dopolnitelnye-poyasneniya)

---

## Сначала: кто с кем взаимодействует

В `reactor-kafka` есть три стороны:

1. **Kafka broker** хранит записи.
2. **Kafka consumer** регулярно вызывает `poll()` и получает очередную порцию записей из назначенных ему партиций.
3. **Ваш обработчик** получает эти записи и, например, сохраняет их в БД.

Схема в упрощённом виде такая:

```text
Kafka broker
     │
     │ poll()
     ▼
reactor-kafka / Kafka consumer
     │
     │ передаёт записи
     ▼
ваш код: обработка → запись в БД
```

`poll()` — это вызов Kafka-клиента, который получает записи. Консьюмер должен регулярно возвращаться к этому вызову: если он слишком долго не вызывает `poll()`, Kafka считает, что он не справляется, и может передать его партиции другому консьюмеру группы.

**Источник:** https://kafka.apache.org/37/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html

EN:

> The poll API is designed to ensure consumer liveness. As long as you continue to call poll, the consumer will stay in the group and continue to receive messages from the partitions it was assigned.

RU:

> API `poll()` предназначен для контроля работоспособности консьюмера. Пока консьюмер продолжает вызывать `poll()`, он остаётся в группе и получает сообщения из назначенных ему партиций.

## Что происходит при обычной обработке

Представим, что Kafka отдаёт записи, а приложение сохраняет их в PostgreSQL через **R2DBC**, то есть неблокирующе:

```java
receiver.receive()
// Поток записей из Kafka: каждая запись — это ConsumerRecord с ключом, значением и метаданными
    .concatMap(record ->
        // concatMap(..., concurrency=1 по умолчанию): обрабатывает записи строго последовательно
        // — следующая запись не начнёт обрабатываться, пока не завершится предыдущая
        databaseClient
        // Формируем SQL-запрос: вставляем id и status в таблицу orders
        .sql("insert into orders(id, status) values (:id, :status)")
// Подставляем ключ записи Kafka как id
            .bind("id", record.key())
        // Подставляем поле status из значения записи Kafka
        .bind("status", record.value().status())
        .fetch()
// Выполняем запрос и получаем количество обновлённых строк (для INSERT обычно 1)
            .rowsUpdated()
// После успешной вставки в БД подтверждаем offset в Kafka
// acknowledge() сообщает брокеру, что запись обработана и можно сдвигать consumer offset
            .then(Mono.fromRunnable(record.receiverOffset()::acknowledge))
        )
        // Запускаем поток: без subscribe() конвейер не начнёт работать
        .subscribe();

```

Здесь запись в БД может выполняться некоторое время, но поток Kafka-консьюмера не стоит в ожидании. Он запускает асинхронную операцию и может продолжать работу: reactor-kafka может регулярно вызывать `poll()`.

Важно: «запись в БД ещё не завершилась` и «поток, который вызывает `poll()`, завис` — не одно и то же.

## Что такое backpressure здесь

**Backpressure** — это ситуация, когда обработчик временно не готов принять следующую запись, потому что ещё занят предыдущими.

Например, `concatMap` обрабатывает записи строго по одной:

```text
Kafka отдала записи: A, B, C

Приложение:
- сохраняет A в БД;
- пока A не сохранена, B не передаётся в обработчик;
- пока B не обработана, C также ждёт.
```

То есть приложение не говорит Kafka: «я сломался` или «я завис`. Оно говорит: «сейчас у меня есть место только для одной записи; следующую отдай, когда я закончу текущую`.

В терминах Reactive Streams это выражается через `request(n)`:

- `request(1)` означает: «я готов принять одну запись`;
- пока эта запись обрабатывается, новый запрос на следующую может не поступать;
- когда обработка завершена, появляется запрос на следующую запись.

`request(n)` — не вызов, который обычно нужно писать вручную в бизнес-коде. Его обычно выполняет Reactor-оператор, например `concatMap`, `flatMap` или `limitRate`, чтобы ограничить число одновременно обрабатываемых записей.

Когда reactor-kafka видит, что обработчик пока не запрашивает новые записи, он может вызвать `pause()` для партиций. Это означает: «временно не отдавай новые записи из этих партиций`. При этом сам consumer loop продолжает выполнять `poll()`.

**Источник:** https://github.com/reactor/reactor-kafka/blob/main/src/main/java/reactor/kafka/receiver/internals/ConsumerEventLoop.java

EN:

> `consumer.pause(consumer.assignment());`  
> `log.debug("Paused - back pressure");`  
> `records = consumer.poll(pollTimeout);`

RU:

> При backpressure reactor-kafka вызывает `pause()` для назначенных партиций, пишет в лог `Paused - back pressure`, а затем всё равно выполняет `poll()`.

Иными словами, при backpressure происходит следующее:

```text
Обработчик ещё занят
        │
        ▼
reactor-kafka временно pause() партиции
        │
        ▼
poll() продолжает вызываться
        │
        ▼
новые записи с paused-партиций пока не передаются обработчику
```

Это нормальный управляемый режим замедления. Консьюмер не перестаёт работать и не должен выпадать из consumer group только потому, что обработка стала медленнее.

## Что значит блокирующая БД

Теперь другой сценарий: используется JDBC.

```java
receiver.receive()
    .doOnNext(record ->
        jdbcTemplate.update(
            "insert into orders(id, status) values (?, ?)",
            record.key(),
            record.value().status()
        )
    )
    .subscribe();
```

`jdbcTemplate.update(...)` — синхронный вызов. Пока PostgreSQL не ответит, поток, который выполняет этот код, стоит и ждёт.

Если этот код выполняется на том же потоке, который должен вызывать Kafka `poll()`, получается такая последовательность:

```text
1. consumer получил записи через poll()
2. начал jdbcTemplate.update(...)
3. БД отвечает долго
4. поток ждёт БД
5. poll() в это время не вызывается
6. Kafka ждёт следующий poll()
7. max.poll.interval.ms истекает
8. Kafka исключает consumer из группы и запускает rebalance
```

Фраза «консьюмер завис на INSERT в БД` должна означать именно это:

> Не Kafka зависла и не backpressure сработал. Поток приложения синхронно ждёт завершения SQL-запроса, поэтому не возвращается к вызову `poll()`.

**Источник:** https://kafka.apache.org/37/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html

EN:

> It is also possible that the consumer could encounter a "livelock" situation where it is continuing to send heartbeats, but no progress is being made. [...] Basically if you don't call poll at least as frequently as the configured max interval, then the client will proactively leave the group so that another consumer can take over its partitions.

RU:

> Консьюмер может попасть в ситуацию, когда heartbeat продолжает отправляться, но обработки нет. Если `poll()` не вызывается с частотой, заданной максимальным интервалом, клиент сам покинет группу, чтобы другой консьюмер мог забрать его партиции.

## Главное различие

| Ситуация | Что происходит с обработкой | Что происходит с `poll()` | Последствие |
|---|---|---|---|
| Backpressure | Обработчик временно не берёт новые записи, пока занят предыдущими | `poll()` продолжает выполняться | Партиции временно ставятся на `pause()`, новые записи ждут в Kafka |
| JDBC на consumer-потоке | Поток синхронно ждёт завершения SQL-запроса | `poll()` не выполняется, пока JDBC не вернёт управление | Может истечь `max.poll.interval.ms`, после чего начнётся rebalance |

Корректная краткая формулировка для документа:

> **Backpressure в reactor-kafka** — это управляемое ограничение скорости получения записей: когда обработчик ещё занят, reactor-kafka временно приостанавливает выдачу новых записей из партиций через `pause()`, но продолжает вызывать `poll()`.
>
> **Блокирующий JDBC-вызов на потоке Kafka-консьюмера** — другая проблема: поток ждёт ответа БД и не может вызвать следующий `poll()`. Если это длится дольше `max.poll.interval.ms`, Kafka исключает консьюмер из группы и перераспределяет его партиции.

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

# Как reactor-kafka управляет `requested` и как связаны pause, resume и poll

- [Кто меняет `requested`](#кто-меняет-requested)
- [Как связаны requested, pause и poll](#как-связаны-requested-pause-и-poll)
- [Полный цикл PollEvent](#полный-цикл-pollevent)

## Кто меняет `requested`

`requested` — это счётчик спроса от downstream (Reactive Streams).

- Увеличивает downstream через `request(n)` (например, `concatMap`, `flatMap`).
- Уменьшает `ConsumerEventLoop` на 1 после эмитa одной пачки записей.

```java
receiver.receive()
    .concatMap(record -> process(record))
    .subscribe();
```

`concatMap` сам вызывает `request(n)` upstream, когда готов обработать следующую запись.

**Источник:** https://github.com/reactor/reactor-kafka/blob/main/src/main/java/reactor/kafka/receiver/internals/ConsumerEventLoop.java

EN:

```java
void onRequest(long toAdd) {
    Operators.addCap(REQUESTED, this, toAdd);
    if (pollEvent.isPaused()) {
        consumer.wakeup();
    }
    pollEvent.schedule();
}
```

RU:

`onRequest(long toAdd)` добавляет `toAdd` к `requested`, будит consumer и планирует следующий `PollEvent`.

**Источник:** https://github.com/reactor/reactor-kafka/blob/main/src/main/java/reactor/kafka/receiver/internals/ConsumerEventLoop.java

EN:

```java
if (!records.isEmpty()) {
    this.commitBatch.addUncommitted(records);
    r = Operators.produced(REQUESTED, ConsumerEventLoop.this, 1);
    log.debug("Emitting {} records, requested now {}", records.count(), r);
    sink.emitNext(records, ConsumerEventLoop.this);
}
```

RU:

Если `poll()` вернул записи — уменьшает `requested` на 1 и эмитит пачку в sink.

## Как связаны requested, pause и poll

В начале `PollEvent.run()`:

1. Читает `requested`.
2. Если `requested > 0` → `resume()` партиций.
3. Если `requested == 0` → `pause()` партиций с логом `Paused - back pressure`.
4. В любом случае вызывает `consumer.poll(timeout)`.
5. Если `poll()` вернул записи → эмитит пачку и уменьшает `requested` на 1.

**Источник:** https://github.com/reactor/reactor-kafka/blob/main/src/main/java/reactor/kafka/receiver/internals/ConsumerEventLoop.java

EN:

```java
if (r > 0) {
    if (!awaitingTransaction.get()) {
        if (pausedByUs.getAndSet(false)) {
            consumer.resume(toResume);
        }
    }
} else if (checkAndSetPausedByUs()) {
    consumer.pause(consumer.assignment());
    log.debug("Paused - back pressure");
}

records = consumer.poll(pollTimeout);

if (!records.isEmpty()) {
    sink.emitNext(records, ConsumerEventLoop.this);
}
```

RU:

Если `r > 0` и не ждём транзакцию — снимает паузу (`resume()`).  
Если `r == 0` — ставит паузу (`pause()`) с логом `Paused - back pressure`.  
Затем вызывает `poll()` и, если есть записи, эмитит их.

## Полный цикл PollEvent

**Термины:**

- **Downstream** — ваш код после `receiver.receive()`: операторы `concatMap`, `flatMap`, `subscribe` и т.п.
- **`request(n)`** — сигнал от downstream: «я готов обработать ещё `n` пачек записей из Kafka`.
- **`requested`** — внутренний счётчик в `ConsumerEventLoop`. Показывает, сколько пачек downstream ещё готов принять.
- **Пачка** — один `ConsumerRecords<K,V>`, который вернул `poll()`. Может содержать от 0 до многих записей Kafka.
- **Эмит пачки** — передача этой пачки из `ConsumerEventLoop` в downstream (в ваш `Flux`).

**Цикл:**

1. Downstream (например, `concatMap`) вызывает `request(n)`.  
   `n` выбирает оператор: сколько пачек он сейчас готов обработать без перегрузки.  
   `ConsumerEventLoop` делает `requested += n`.

2. `PollEvent.run()`:
   - если `requested > 0` → `resume()` партиций;
   - если `requested == 0` → `pause()` партиций;
   - в любом случае `consumer.poll(timeout)`;
   - если `poll()` вернул непустую пачку → эмитит её в downstream и `requested -= 1`.

3. Downstream обрабатывает пачку (например, сохраняет записи в БД).

4. Когда downstream обработал часть данных и готов к следующей пачке, он снова вызывает `request(n)` → цикл повторяется.

**Итог:**

- Backpressure = `pause()` партиций + `poll()` продолжается + нет эмитов при `requested == 0`.
- Блокирующий JDBC на том же потоке ломает цикл: `poll()` не вызывается вовремя → rebalance.

### По шагам: 10 заказов и запись в БД

Берём сервис склада интернет-магазина. 
- Топик `orders-topic` присылает события «списать товар». 
- Дальше `concatMap` или `flatMap` пишет в PostgreSQL.

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
| Есть ли `request(n)` в самом протоколе источника | Да. Спрос от Reactor останавливает чтение следующих строк результата из соединения. | Нет. У Kafka-консьюмера нет Reactive Streams. Спрос эмулируется через `pause()`/`resume()` партиций. |
| Что происходит при отсутствии спроса | Драйвер не забирает следующую порцию строк из БД. | `poll()` продолжается, но с поставленных на паузу партиций новые записи не возвращаются. |
| Где действует | На одном соединении / курсоре в рамках одного запроса. | На всех партициях, назначенных данному `KafkaConsumer`. |
| Единица управления | Отдельные строки результата запроса. | Пачка записей, которую вернул один вызов `poll()` (до `max.poll.records` записей). |
| Где работает цикл | Netty event loop / пул R2DBC. | Отдельный `eventScheduler` reactor-kafka, не Netty event loop. |

**Вывод:** 
 - R2DBC управляет спросом на уровне строк результата внутри соединения. 
 - Kafka остаётся `pull`+`poll()`, а reactor-kafka добавляет сверху `pause()` партиций, чтобы не вытягивать новую пачку, пока обработаны не все предыдущие.
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
