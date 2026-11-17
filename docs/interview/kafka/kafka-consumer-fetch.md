# Kafka Consumer: как poll() получает данные и что ограничивают fetch-параметры

Термины: **клиент** — библиотека `kafka-clients` (объект `KafkaConsumer`) внутри вашего Java-приложения; **брокер** — сервер Kafka; **Fetch-запрос** — сетевой запрос клиента к брокеру на получение записей.

## Оглавление

- [1. Проверка исходного утверждения](#s1)
- [2. Параметры fetch](#s2)
- [3. Мягкие лимиты](#s3)
- [4. Куда попадают полученные данные](#s4)
- [5. Как связаны poll() и сетевой запрос](#s5)
- [6. Пример: лаг и max.partition.fetch.bytes](#s6)

<a id="s1"></a>
## 1. Проверка исходного утверждения

- Верно: consumer работает по pull-модели, `poll()` отдаёт приложению записи, полученные от брокера.
- Верно: `fetch.max.bytes` ограничивает объём данных одного Fetch-запроса (суммарно по всем партициям).
- **Неверно:** `max.partition.fetch.bytes` — это лимит на **одну партицию**, а не суммарный по нескольким.

<a id="s2"></a>
## 2. Параметры fetch

**Утверждение.** `max.partition.fetch.bytes` (по умолчанию 1 МБ) ограничивает данные от одной партиции в ответе брокера. Данные читаются пачками (batch) записей.

- Источник: https://kafka.apache.org/20/generated/consumer_config.html

EN:

> The maximum amount of data per-partition the server will return. Records are fetched in batches by the consumer.

RU:

> Максимальный объём данных на одну партицию, который вернёт сервер. Consumer получает записи пачками (batch).

**Утверждение.** `fetch.max.bytes` (по умолчанию 50 МБ) — лимит на весь Fetch-запрос, поэтому он по умолчанию больше, чем лимит на партицию.

- Источник: https://kafka.apache.org/20/generated/consumer_config.html

EN:

> The maximum amount of data the server should return for a fetch request.

RU:

> Максимальный объём данных, который сервер должен вернуть на один Fetch-запрос.

**Утверждение.** Клиент передаёт в запросе оба лимита на разных уровнях: `fetch.max.bytes`, `fetch.min.bytes`, `fetch.max.wait.ms` — на весь запрос, `max.partition.fetch.bytes` — на каждую партицию.

- Источник: https://krivaltsevich.com/kafka-internals/17-consumer-client.html

EN:

> So `fetch.max.wait.ms`, `fetch.min.bytes`, and `fetch.max.bytes` are all request-level; `max.partition.fetch.bytes` is per-partition.

RU:

> Таким образом, `fetch.max.wait.ms`, `fetch.min.bytes` и `fetch.max.bytes` действуют на уровне запроса, а `max.partition.fetch.bytes` — на уровне партиции.

**Утверждение.** `max.poll.records` (по умолчанию 500) ограничивает число записей, которые `poll()` отдаёт приложению за один вызов. Это другой лимит: он не меняет объём данных, получаемых по сети.

- Источник: https://krivaltsevich.com/kafka-internals/17-consumer-client.html

EN:

> `max.poll.records` caps how many records each `poll()` returns but does not change the wire fetch, the broker still ships up to `max.partition.fetch.bytes` per partition; leftover records stay buffered for the next poll.

RU:

> `max.poll.records` ограничивает, сколько записей возвращает каждый `poll()`, но не меняет сетевой fetch: брокер по-прежнему отправляет до `max.partition.fetch.bytes` на партицию; оставшиеся записи остаются в буфере до следующего `poll()`.

<a id="s3"></a>
## 3. Мягкие лимиты

**Утверждение.** Оба лимита (`max.partition.fetch.bytes` и `fetch.max.bytes`) не абсолютные. Если первый batch в первой непустой партиции больше лимита, брокер всё равно его вернёт, иначе consumer не смог бы продвинуться дальше этого batch. Поэтому ответ может превысить заданные значения.

- Источник: https://kafka.apache.org/20/generated/consumer_config.html

EN:

> If the first record batch in the first non-empty partition of the fetch is larger than this limit, the batch will still be returned to ensure that the consumer can make progress.

RU:

> Если первый batch записей в первой непустой партиции fetch больше этого лимита, batch всё равно будет возвращён, чтобы consumer мог продвигаться.

- Источник: https://kafka.apache.org/20/generated/consumer_config.html

EN:

> As such, this is not a absolute maximum.

RU:

> Таким образом, это не абсолютный максимум.

Максимальный размер batch, который принимает брокер, задаётся отдельно (`message.max.bytes` на брокере, `max.message.bytes` на топике). К вашим двум параметрам он не относится.

<a id="s4"></a>
## 4. Куда попадают полученные данные

**Утверждение.** Ответ брокера клиент режет на части по партициям. Каждая часть оборачивается в объект `CompletedFetch` — это уже полученные по сети записи одной партиции, которые приложению ещё не отданы. В очереди не больше одного `CompletedFetch` на партицию.

- Источник: https://krivaltsevich.com/kafka-internals/17-consumer-client.html

EN:

> On a successful response, `AbstractFetch.handleFetchSuccess()` wraps each partition's data in a `CompletedFetch` and calls `fetchBuffer.add(completedFetch)`. [...] There is at most one `CompletedFetch` per partition in the queue.

RU:

> При успешном ответе `AbstractFetch.handleFetchSuccess()` оборачивает данные каждой партиции в `CompletedFetch` и вызывает `fetchBuffer.add(completedFetch)`. [...] В очереди не более одного `CompletedFetch` на партицию.

**Утверждение.** Буфер называется `FetchBuffer` и устроен как `ConcurrentLinkedQueue` (потокобезопасная очередь). Это обычный Java-объект, он лежит в куче JVM (heap) приложения, а не в стеке. Про кучу в источнике не сказано, это общее свойство Java: в стеке находятся только локальные переменные и ссылки.

- Источник: https://krivaltsevich.com/kafka-internals/17-consumer-client.html

EN:

> `FetchBuffer` is a `ConcurrentLinkedQueue` guarded by a `ReentrantLock` with a `Condition`

RU:

> `FetchBuffer` — это `ConcurrentLinkedQueue`, защищённая `ReentrantLock` с `Condition`.

<a id="s5"></a>
## 5. Как связаны poll() и сетевой запрос

`poll()` вызывает ваш код (приложение). Он отдаёт записи из буфера обратно вашему коду. Сетевой запрос и выдача записей из `poll()` не обязаны происходить одновременно.

**Утверждение.** В классическом consumer `poll()` сначала смотрит в буфер: если там есть данные, возвращает их сразу, без ожидания сети. Если буфер пуст, отправляет Fetch и ждёт ответ в этом же потоке.

- Источник: https://krivaltsevich.com/kafka-internals/17-consumer-client.html

EN:

> `fetcher.collectFetch()` returns buffered data immediately if present; otherwise `sendFetches()` issues new fetch RPCs and `client.poll(pollTimer, () -> !fetcher.hasAvailableFetches())` blocks for responses.

RU:

> `fetcher.collectFetch()` сразу возвращает данные из буфера, если они есть; иначе `sendFetches()` отправляет новые Fetch-запросы, а `client.poll(...)` блокируется в ожидании ответов.

**Утверждение.** Если `poll()` получил непустой результат, то прямо перед возвратом записей приложению клиент отправляет следующий Fetch. Пока приложение обрабатывает текущие записи, следующая порция уже в пути.

- Источник: https://krivaltsevich.com/kafka-internals/17-consumer-client.html

EN:

> If the fetch is non-empty, pipeline: `sendFetches()` + `client.transmitSends()` before returning, so the next batch is already in flight while the user processes records.

RU:

> Если fetch непустой, работает конвейер: `sendFetches()` + `client.transmitSends()` до возврата, поэтому следующая порция уже в пути, пока пользователь обрабатывает записи.

**Утверждение.** В новый Fetch не включаются партиции, которые уже лежат в буфере. Иначе брокер исключил бы их из своей fetch-сессии, и это грозило бы её вытеснением.

- Источник: https://krivaltsevich.com/kafka-internals/17-consumer-client.html

EN:

> It deliberately skips a node if (a) there is already an inflight fetch to it, or (b) it currently hosts buffered partitions, fetching other partitions from a node with buffered data would drop the buffered ones from the broker's fetch session and risk session eviction

RU:

> Узел намеренно пропускается, если (а) к нему уже идёт fetch или (б) на нём есть партиции в буфере: запрос других партиций с узла, где есть буферизованные данные, исключил бы буферизованные из fetch-сессии брокера и создал риск вытеснения сессии.

**Утверждение.** В новом (async) consumer сетевым обменом занимается отдельный фоновый поток, который кладёт ответы в `FetchBuffer`. Поток приложения в сети не блокируется.

- Источник: https://krivaltsevich.com/kafka-internals/17-consumer-client.html

EN:

> Async engine: the application thread never blocks on network I/O; it exchanges events with the background loop.

RU:

> Async-движок: поток приложения никогда не блокируется на сетевом вводе-выводе; он обменивается событиями с фоновым циклом.

Про соединения: клиент, насколько мне известно, держит постоянное TCP-соединение с каждым брокером, а не открывает сокет на каждый запрос. Найденные источники этого не подтверждают.

<a id="s6"></a>
## 6. Пример: лаг и max.partition.fetch.bytes

Расчёт мой, не из документации. Допустим, брокер отдаёт максимум и накопленный лаг (непрочитанные записи) равен 1 ГБ.

- 1 партиция, `max.partition.fetch.bytes` = 1 МБ: около 1000 Fetch-запросов.
- 1 партиция, 10 МБ: около 100 запросов. Увеличивать `fetch.max.bytes` (50 МБ) бессмысленно: с одной партиции больше её лимита не придёт.
- 100 партиций: лимит запроса (50 МБ) делится между ними, и первым ограничивает уже `fetch.max.bytes`.
