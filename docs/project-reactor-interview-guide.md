# Project Reactor: руководство и вопросы для собеседования

> Краткое руководство по **Project Reactor** для Java-разработчиков, в том числе новичков.  
> Формат блока: **аналогия из жизни → рисунок → ответ → вопрос → источник → цитата**.

**Как усваивать материал:** в каждом разделе — **аналогия из жизни** и **PNG-рисунок** (не код диаграммы). Перегенерация: `python docs/Images-docs/gen_reactor_diagrams.py`.

---

## Оглавление

1. [Что такое Project Reactor](#1-что-такое-project-reactor)
2. [Что такое реактивное программирование](#2-что-такое-реактивное-программирование)
3. [Mono и Flux — в чём разница](#3-mono-и-flux--в-чём-разница)
4. [Backpressure (обратное давление)](#4-backpressure-обратное-давление)
5. [subscribe() и block() — в чём разница](#5-subscribe-и-block--в-чём-разница)
6. [map и flatMap — когда что использовать](#6-map-и-flatmap--когда-что-использовать)
7. [subscribeOn и publishOn](#7-subscribeon-и-publishon)
8. [Schedulers — какие бывают и зачем](#8-schedulers--какие-бывают-и-зачем)
9. [Cold и Hot publishers](#9-cold-и-hot-publishers)
10. [Обработка ошибок в Reactor](#10-обработка-ошибок-в-reactor)
11. [Retry — повтор при ошибке](#11-retry--повтор-при-ошибке)
12. [Как тестировать Reactor-код (StepVerifier)](#12-как-тестировать-reactor-код-stepverifier)
13. [Project Reactor и Spring WebFlux](#13-project-reactor-и-spring-webflux)
14. [Reactor vs RxJava — кратко](#14-reactor-vs-rxjava--кратко)
15. [Когда реактивный подход уместен, а когда нет](#15-когда-реактивный-подход-уместен-а-когда-нет)
16. [Disposable и отмена подписки](#16-disposable-и-отмена-подписки)
17. [Блокирующий код внутри реактивной цепочки](#17-блокирующий-код-внутри-реактивной-цепочки)
18. [Краткая шпаргалка по операторам](#18-краткая-шпаргалка-по-операторам)

---

## Введение

**Project Reactor** — библиотека для Java: вы описываете **цепочку шагов** над потоком данных, а не «вызвал метод — поток ждёт ответ».

> **Аналогия:** вы не носите каждую деталь по цеху — вы **навешиваете операции на конвейер** (`Mono` / `Flux`).

![Цепочка от PostgreSQL до JSON](./Images-docs/reactor-concept-intro.png)


| Тип | Сколько элементов | Пример |
|-----|-------------------|--------|
| `Mono<T>` | 0 или 1 | `findById`, один HTTP-ответ |
| `Flux<T>` | 0…N | `findAll`, SSE, список id |

**Стандартное форматирование цепочки** — каждый оператор с новой строки («лесенка»):

```java

return userRepository.findById(id)
    .map(User::email)
    .map(String::toUpperCase);
```

Зависимости Maven:

```xml

<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-core</artifactId>
</dependency>
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 1. Что такое Project Reactor

> **Аналогия из жизни:** Reactor — это **конвейер на фабрике**. Вы не таскаете каждую деталь руками до конца цеха, а **навешиваете на ленту** шаги: «прикрути → покрась → упакуй». Лента сама движется, когда её **включают** (`subscribe()` или Spring в WebFlux).

![§1 Project Reactor — конвейер](./Images-docs/reactor-concept-01.png)


**Ответ:**

1. Java-библиотека для **неблокирующего** кода: цепочка `Mono`/`Flux` + операторы (`map`, `flatMap`, …).
2. Два типа: `Mono` (0–1 элемент), `Flux` (0–N элементов).
3. Цепочка **ленивая** — сама по себе ничего не делает, пока нет `subscribe()` (в WebFlux подписывается Spring).
4. Реализует **Reactive Streams** (протокол подписчик ↔ источник, в том числе backpressure).
5. Основа Spring WebFlux, WebClient, R2DBC.

**Вопрос:** *What is Project Reactor and how does it relate to Reactive Streams?*

**Источник:** [Reactor 3 Reference Guide](https://projectreactor.io/docs/core/release/reference/#intro-reactor)

> **EN:** «Reactor is a fully non-blocking reactive programming foundation for the JVM … Flux (for [N] elements) and Mono (for [0|1] elements) … implements the Reactive Streams specification.»

> **RU:** «Reactor — неблокирующая основа для реактивного программирования на JVM … Flux и Mono … реализует спецификацию Reactive Streams.»

---

## 2. Что такое реактивное программирование

> **Аналогия из жизни:** Обычный код — вы **стоите у окна почты** и ждёте одно письмо, ничего другого не делая. Реактивный код — **подписка на уведомления**: пришло сообщение → обработали → ждёте следующее; пока ждёте, телефон может принять другие push.

![§2 Сигналы onNext / onError / onComplete](./Images-docs/reactor-concept-02.png)


**Ответ:**

1. Вы работаете с **потоком событий**, а не с одним готовым результатом.
2. Три сигнала: `onNext` (данные), `onError` (ошибка), `onComplete` (конец).
3. Поток не обязан простаивать в ожидании БД или сети — при неблокирующем I/O один поток обслуживает много задач.
4. Полезно: много одновременных соединений, стриминг (SSE, WebSocket).
5. Не panacea: простой CRUD на JDBC часто проще через обычный Spring MVC.

**Вопрос:** *What is reactive programming?*

**Источник:** [CLIMB — Spring Reactive Interview Questions](https://climbtheladder.com/spring-reactive-interview-questions/)

> **EN:** «Reactive programming is concerned with data streams and the propagation of change.»

> **RU:** «Реактивное программирование связано с потоками данных и распространением изменений.»

---

## 3. Mono и Flux — в чём разница

> **Аналогия из жизни:** **`Mono`** — **одна посылка** в день (может не прийти). **`Flux`** — **курьер с тележкой**: ноль, одна или много посылок подряд.

![§3 Mono vs Flux](./Images-docs/reactor-concept-03.png)


**Ответ:**

1. **`Mono<T>`** — 0 или 1 элемент. Примеры: `findById`, `save`, один ответ WebClient.
2. **`Flux<T>`** — 0…N элементов. Примеры: `findAll`, SSE, список id.
3. **Как выбрать:** «сколько элементов вернёт операция?» Один → `Mono`. Несколько → `Flux`.
4. Тип в сигнатуре метода сразу показывает намерение.
5. Между типами есть преобразования: `flux.next()` → `Mono`, `mono.flux()` → `Flux`.

**Пример:**

```java

Mono<User> one = userRepository.findById(1L);
Flux<User> many = userRepository.findAll();
```
**Вопрос:** *What is the difference between Mono and Flux in Project Reactor?*

**Источник:** [Reactor Core Features](https://projectreactor.io/docs/core/release/reference/coreFeatures.html)

> **EN:** «A Flux represents 0..N items, while a Mono represents a single-value-or-empty (0..1) result.»

> **RU:** «Flux — 0…N элементов, Mono — один элемент или пусто (0…1).»

---

## 4. Backpressure (обратное давление)

> **Аналогия из жизни:** Официант **не вываливает** на стол сразу все 50 блюд. Вы говорите: «принесите **три** — съели — принесите ещё три». Так клиент (подписчик) не захлёбывается, а кухня (источник) знает, сколько готовить.

![§4 Backpressure](./Images-docs/reactor-concept-04.png)


**Ответ:**

1. Источник может отдавать данные быстрее, чем подписчик их обрабатывает.
2. **Backpressure** — подписчик сам запрашивает порции: «дай 10 → обработал → дай ещё».
3. Запрос `Long.MAX_VALUE` = «отдай всё сразу» — backpressure выключен (так работает простой `subscribe()`).
4. На больших потоках используйте `limitRate(n)` или `onBackpressureBuffer` / `drop` / `latest`.
5. В WebFlux при стриминге backpressure идёт до Netty автоматически.

**Вопрос:** *What is backpressure and why is it important?*

**Источник:** [Reactor — Backpressure](https://projectreactor.io/docs/core/release/reference/#backpressure)

> **EN:** «Consumer pressure is propagated back to the source by sending a request to the upstream operator.»

> **RU:** «Потребитель сообщает источнику, сколько элементов нужно, через request к upstream.»

---

## 5. subscribe() и block() — в чём разница

> **Аналогия из жизни:** **`subscribe()`** — включили **Netflix** и занялись своими делами; сериал идёт **фоном**. **`block()`** — **замёрли перед экраном** до финала серии; ничего другого в этот момент не делаете.

![§5 subscribe vs block](./Images-docs/reactor-concept-05.png)


**Ответ:**

1. **`subscribe()`** — запускает цепочку **асинхронно**.
2. **`block()`** — **останавливает** текущий поток до результата. Только в тестах, `main`, на границе с imperative-кодом. **Не** в WebFlux-сервисе.
3. **WebFlux:** в контроллере **return Mono/Flux** — `subscribe()` не вызываете, подписывается Spring.
4. Простой `subscribe()` сразу запрашивает `Long.MAX_VALUE` элементов — на огромных потоках опасно для памяти.
5. Для одного элемента (`Mono`) или короткого `Flux.just(...)` это обычно не проблема.

**Пример (только тест / main):**

```java

String email = userRepository.findById(1L)
    .map(User::email)
```
    .block();   // OK в тесте, НЕ в WebFlux-сервисе

**WebFlux (правильно):**

```java

@GetMapping("/{id}/email")
public Mono<String> getEmail(@PathVariable Long id) {
    return userRepository.findById(id)
        .map(User::email);
}
```
**Вопрос:** *What is the difference between block() and subscribe()?*

**Источник:** [Reactor — Backpressure / subscribing](https://projectreactor.io/docs/core/release/reference/#backpressure)

> **EN:** «subscribe() and block(), blockFirst(), blockLast() immediately trigger an unbounded request of Long.MAX_VALUE.»

> **RU:** «subscribe() и block() сразу запрашивают неограниченное количество элементов (Long.MAX_VALUE).»

---

## 6. map и flatMap — когда что использовать

> **Аналогия из жизни:** На конвейере лежит **яблоко** (`User`).
> - **`map`** — вы **снимаете кожуру** на месте: яблоко → очищенное яблоко → дольки. Объект уже в руках.
> - **`flatMap`** — вам дали **закрытую коробку с наклейкой «внутри яблоко»** (`Mono<User>`). **`map`** положит **саму коробку** на ленту. **`flatMap`** **откроет** коробку и положит **яблоко**.

![§6 map vs flatMap — сигнатуры](./Images-docs/reactor-concept-06.png)


**Ответ — начните с сигнатур методов**

В Project Reactor у операторов **разная сигнатура**. Смотрите, **что возвращает ваша лямбда**:

### `map` — лямбда возвращает **обычный объект**

```java

// Flux.map / Mono.map — упрощённо:
.map(значение -> другоеЗначение)

// значение и другоеЗначение — String, User, DTO, Integer …
// НЕ Mono и НЕ Flux

userRepository.findById(id)
    .map(User::email)              // User → String
    .map(String::toUpperCase);     // String → String
// результат: Mono<String>
```

### `flatMap` — лямбда возвращает **Mono или Flux**

```java

// Flux.flatMap / Mono.flatMap — упрощённо:
.flatMap(значение -> Mono<...> или Flux<...>)

Flux.fromIterable(ids)
    .flatMap(userRepository::findById)   // id → Mono<User>
    .map(UserResponse::from);            // User → DTO (здесь уже map)
// результат: Flux<UserResponse>
```

### Одно правило

| Что пишете в лямбде после `->` | Оператор |
|--------------------------------|----------|
| `user.email()`, `UserResponse.from(u)`, `"hello"` | **`map`** |
| `userRepository.findById(id)`, `webClient.get()…bodyToMono(...)` | **`flatMap`** |

**Не путайте:** «можно ли вызвать БД в map» — можно, но если метод репозитория возвращает **`Mono<User>`**, в `map` вы кладёте в поток **сам Mono**, а не User. Reactor **не подписывается** на него автоматически. Нужен **`flatMap`**.

---

### Пример 1: `map` — данные уже есть, только преобразуем

`findById` уже вернул `User`. Дальше — поля в памяти:
```java

// UserService.java
return userRepository.findById(id)
    .map(User::email)
    .map(String::toUpperCase);
```
![Sequence: map после findById](./Images-docs/reactor-seq-map-email.png)

**На диаграмме:** один SQL → User в памяти → два `map` → JSON. БД больше не вызывается.

**Проверка:** `curl http://localhost:8081/api/users/1/email-upper` → `"ANN@EXAMPLE.COM"`

---

### Пример 2: `map` — ошибка, если репозиторий возвращает Mono

`findById` возвращает **`Mono<User>`**, не `User`:
```java

// ❌ Flux<Mono<User>> — в поток попали «коробки» Mono, не User
return Flux.fromIterable(ids)
    .map(userRepository::findById);
```
```java

// ❌ Mono<Flux<Order>> — заказы не загрузились
return userRepository.findById(id)
    .map(user -> orderRepository.findByUserId(user.id()));
```
**Правильно:**

```java

// ✅ UserService.getUserSummary
return userRepository.findById(id)
    .flatMap(user -> orderRepository.findByUserId(user.id())
        .collectList()
        .map(orders -> UserSummaryResponse.of(user, orders)));
```
| Строка | Оператор | Почему |
|--------|----------|--------|
| `findById` | — | вернул `Mono<User>` |
| `flatMap(… findByUserId …)` | **flatMap** | `findByUserId` → `Flux<Order>` |
| `map(orders -> …)` | **map** | DTO — обычный объект |

![Sequence: getUserSummary — flatMap + map](./Images-docs/reactor-seq-get-user-summary.png)

**Проверка:** `curl http://localhost:8081/api/users/1/summary`

---

### Пример 3: `map` vs `flatMap` на одном findById

**Ошибка (`map`):**

```java

// ReactorDemoService.java
return Flux.fromIterable(ids)
    .map(userRepository::findById);
```
![Sequence: map + findById — ошибка](./Images-docs/reactor-seq-map-wrong-db.png)

**SQL не уходит** — в потоке лежит объект `Mono`, а не `User`.

**Правильно (`flatMap`):**

```java

return Flux.fromIterable(ids)
    .flatMap(userRepository::findById)
    .map(UserResponse::from);
```
![Sequence: flatMap + findById — правильно](./Images-docs/reactor-seq-flatmap-db.png)

**Проверка:**

```bash

curl "http://localhost:8081/api/demo/reactor/compare?ids=1,2"
curl "http://localhost:8081/api/demo/reactor/users?ids=1,2"
```
---

### Пример 4: WebClient — тот же принцип

HTTP-вызов возвращает `Mono` → нужен `flatMap`:

```java

return Flux.fromIterable(orderIds)
    .flatMap(id -> webClient.get()
        .uri("/orders/{id}", id)
        .retrieve()
        .bodyToMono(Order.class));
```
---

### `flatMap` vs `concatMap` — порядок

`flatMap` — запросы могут идти **параллельно**, порядок ответов не гарантирован:
```java

Flux.fromIterable(ids)
    .flatMap(userRepository::findById);
```
`concatMap` — **строго по очереди** (1, потом 2, потом 3):
```java

Flux.fromIterable(ids)
    .concatMap(userRepository::findById);
```
![Sequence: flatMap vs concatMap](./Images-docs/reactor-seq-flatmap-vs-concatmap.png)

**Проверка в reactive-demo:**

```bash

curl "http://localhost:8081/api/demo/reactor/users?ids=1,2,3"
curl "http://localhost:8081/api/demo/reactor/users-concat?ids=1,2,3"
```
---

### Шпаргалка

| Ситуация | Оператор |
|----------|----------|
| Преобразовать поле, строку, DTO | `map` |
| Метод возвращает `Mono` или `Flux` | `flatMap` |
| `Flux` внутри `Mono` | `flatMapMany` |
| Нужен порядок как у id | `concatMap` |

**Вопрос:** *What is the difference between map and flatMap in Project Reactor?*

**Источник:** [Reactor — which operator](https://projectreactor.io/docs/core/release/reference/#which-operator)

> **EN:** «map applies a function returning a new element; flatMap applies a function that returns a Publisher, merging elements into a single output stream.»

> **RU:** «map возвращает новый элемент; flatMap — Publisher, элементы которого сливаются в один поток.»

---

## 7. subscribeOn и publishOn

> **Аналогия из жизни:** **`subscribeOn`** — **в каком цехе включают конвейер** (у источника). **`publishOn`** — **на какой ленте работают следующие станки** после развилки. Один заказ может начаться на складе, а упаковка — в другом зале.

![§7 subscribeOn / publishOn](./Images-docs/reactor-concept-07.png)


**Ответ:**

Оба переносят работу на другой **пул потоков** (`Scheduler`), но в **разные места** цепочки.

1. **`subscribeOn`** — где **подписываются** к источнику. Ставят у источника. Позиция в цепочке почти не важна.
2. **`publishOn`** — где выполняется всё **ниже** по цепочке. Позиция **важна**.

```java

Flux.just(1)
```
    .map(x -> x + 1)                         // поток A
    .publishOn(Schedulers.parallel())      // дальше — поток B
    .map(x -> x * 2)                         // поток B
    .subscribeOn(Schedulers.boundedElastic())
    .subscribe();

3. **`subscribeOn`** — блокирующий источник (legacy JDBC) на `boundedElastic`.
4. **`publishOn`** — тяжёлая обработка после получения данных.
5. В WebFlux контроллере обычно не нужны — если нет блокирующего кода.

**Вопрос:** *What is the difference between subscribeOn and publishOn?*

**Источник:** [Reactor — Schedulers](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html)

> **EN:** «publishOn applies in the middle of the subscriber chain … subscribeOn applies to the subscription process.»

> **RU:** «publishOn — в середине цепочки … subscribeOn — к процессу подписки.»

---

## 8. Schedulers — какие бывают и зачем

> **Аналогия из жизни:** **Scheduler** — **бригады рабочих**. `parallel()` — математики за столами (CPU). `boundedElastic()` — грузчики для тяжёлых коробок (JDBC, файлы). Нельзя просить математика **час стоять у закрытого сейфа** (`block()` на `parallel()`).

![§8 Schedulers](./Images-docs/reactor-concept-08.png)


**Ответ:**

`Scheduler` — пул потоков для выполнения вашего кода.
| Scheduler | Для чего | Нельзя |
|-----------|----------|--------|
| `immediate()` | Текущий поток | — |
| `parallel()` | CPU (вычисления) | `block()`, JDBC |
| `boundedElastic()` | Блокирующий I/O (JDBC, файлы) | Долгие CPU-циклы |
| `single()` | Один фоновый поток | `block()`, нагрузка |

```java

Mono.fromCallable(() -> jdbcTemplate.queryForObject(...))
    .subscribeOn(Schedulers.boundedElastic());
```
**Вопрос:** *What are Schedulers in Project Reactor?*

**Источник:** [Reactor — Schedulers](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html)

> **EN:** «boundedElastic is a handy way to give a blocking process its own thread … block() inside parallel() results in IllegalStateException.»

> **RU:** «boundedElastic — для блокирующего кода … block() в parallel() → IllegalStateException.»

---

## 9. Cold и Hot publishers

> **Аналогия из жизни:** **Cold** — **Netflix по запросу**: каждый зритель нажал Play → фильм **начался с начала** для него. **Hot** — **прямой эфир радио**: включился на 15-й минуте — **прошлое не перемотаешь**.

![§9 Cold vs Hot](./Images-docs/reactor-concept-09.png)


**Ответ:**

1. **Cold** — каждый `subscribe()` **запускает поток заново** (новый HTTP-запрос, новый SQL).
2. **Hot** — источник уже работает; опоздавший подписчик **не получает прошлое** (как радио).
3. `publish()`, `cache()`, `share()` — делают cold ближе к hot.
4. WebClient, R2DBC по умолчанию — **cold**.
5. На собеседовании: cold = «каждый subscribe — новый прогон»; hot = «один источник, прошлое не повторяется».

**Вопрос:** *Explain the difference between cold and hot publishers.*

**Источник:** [Reactor — Hot vs Cold](https://projectreactor.io/docs/core/release/reference/#intro-reactive)

> **EN:** «A Cold sequence starts anew for each Subscriber … A Hot sequence does not start from scratch for each Subscriber.»

> **RU:** «Cold стартует заново для каждого подписчика … Hot — нет.»

---

## 10. Обработка ошибок в Reactor

> **Аналогия из жизни:** Конвейер — **красная лампа** (`onError`). Пока не нажмёте «аварийный сценарий», лента **стоит**. `onErrorReturn` — подставить **заглушку**. `onErrorResume` — **переключить на запасной конвейер**.

![§10 Обработка ошибок](./Images-docs/reactor-concept-10.png)


**Ответ:**

Ошибка идёт по цепочке как `onError` — поток обрывается, пока не обработаете.

| Оператор | Когда |
|----------|-------|
| `onErrorReturn(x)` | Вернуть значение по умолчанию |
| `onErrorResume(fn)` | Переключиться на другой `Mono`/`Flux` |
| `onErrorMap(fn)` | Заменить тип исключения |
| `onErrorComplete()` | Проглотить ошибку, завершить пустым |

```java

return userRepository.findById(id)
    .map(UserResponse::from)
    .onErrorResume(e -> Mono.just(UserResponse.empty()));
```
«Не найдено» в R2DBC — часто `Mono.empty()`, для HTTP 404 используйте `switchIfEmpty`.

**Вопрос:** *How do you handle errors in Project Reactor?*

**Источник:** [Reactor — error handling](https://projectreactor.io/docs/core/release/reference/#error.handling)

> **EN:** «onErrorReturn, onErrorResume, and onErrorMap handle errors by returning a default value, switching streams, or transforming the error.»

> **RU:** «onErrorReturn, onErrorResume, onErrorMap — значение по умолчанию, другой поток или преобразование исключения.»

---

## 11. Retry — повтор при ошибке

> **Аналогия из жизни:** **`retry`** — **перезвонить**, если линия занята: не «дожимать трубку», а **набрать номер заново** (новая подписка на upstream).

![§11 Retry](./Images-docs/reactor-concept-11.png)


**Ответ:**

1. `retry` **заново подписывается** на upstream — новая попытка с нуля.
2. `retry(3)` — до 3 повторов; `retryWhen(Retry.backoff(...))` — с паузой.
3. Уместно: timeout, 503, разрыв сети, **идемпотентные** операции (GET).
4. Опасно: POST «создать заказ» без idempotency-key — дубликаты.
5. Ошибки 4xx (кроме 429) обычно не retry.

```java

return webClient.get()
    .uri("/data")
    .retrieve()
    .bodyToMono(Data.class)
    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)));
```
**Вопрос:** *How do you implement retry logic in Reactor?*

**Источник:** [Reactor — retry](https://projectreactor.io/docs/core/release/reference/#error.handling)

> **EN:** «It works by re-subscribing to the upstream Flux.»

> **RU:** «retry работает через повторную подписку на upstream.»

---

## 12. Как тестировать Reactor-код (StepVerifier)

> **Аналогия из жизни:** **StepVerifier** — **чек-лист курьера**: «ожидаю посылку "a" → ожидаю "b" → конец маршрута». Без чек-листа вы не знаете, приехало ли уже или ещё в пути.

![§12 StepVerifier](./Images-docs/reactor-concept-12.png)


**Ответ:**

Reactive-код асинхронный — обычный `assertEquals` сразу после `subscribe()` не сработает.

```java

StepVerifier.create(Flux.just("a", "b"))
    .expectNext("a")
    .expectNext("b")
    .verifyComplete();
```
1. `expectNext(value)` — ожидаем элемент.
2. `expectError(SomeException.class)` — ожидаем ошибку.
3. `verifyComplete()` / `verify()` — завершение проверки.
4. Для `delayElements`, `timeout` — `StepVerifier.withVirtualTime(...)`.
5. Зависимость: `reactor-test`.

**Вопрос:** *How do you test reactive streams with StepVerifier?*

**Источник:** [Reactor — Testing](https://projectreactor.io/docs/core/release/reference/#testing)

> **EN:** «Testing that a sequence follows a given scenario, step-by-step, with StepVerifier.»

> **RU:** «Проверка сценария по шагам с помощью StepVerifier.»

---

## 13. Project Reactor и Spring WebFlux

> **Аналогия из жизни:** **WebFlux** — **ресторан с одной умной кассой**: официант (контроллер) **не готовит сам**, а передаёт **заказ-цепочку** (`Mono`) на кухню (сервис → R2DBC). Касса **сама ждёт** готовность — вам не нужно стоять у плиты (`subscribe()` / `block()`).

![§13 WebFlux](./Images-docs/reactor-concept-13.png)


**Ответ:**

1. WebFlux построен на Reactor — везде `Mono`/`Flux`.
2. Контроллер **return Mono/Flux** — `subscribe()` не вызываете.
3. WebClient и R2DBC тоже возвращают `Mono`/`Flux` — цепочка без `block()`:

```java

return userRepository.findById(id)
    .flatMap(u -> paymentClient.getStatus(u.getPaymentId()));
```
4. MVC: поток на запрос, часто блокирует JDBC. WebFlux: мало потоков Netty — **если** нет `block()` в цепочке.
5. Простой CRUD на JDBC — чаще MVC + virtual threads (Java 21+).

**Вопрос:** *How does Project Reactor integrate with Spring WebFlux?*

**Источник:** [Baeldung — Reactor Core](https://www.baeldung.com/reactor-core)

> **EN:** «Spring WebFlux … reactive programming in Spring Boot.»

> **RU:** «Spring WebFlux … реактивное программирование в Spring Boot.»

---

## 14. Reactor vs RxJava — кратко

> **Аналогия из жизни:** Две марки **электроинструментов** с похожими насадками: **Reactor** — набор **в мастерской Spring**. **RxJava** — часто в **Android** и старых Java-проектах. Задача одна (крутить гайки), бренд и коробка разные.

![§14 Reactor vs RxJava](./Images-docs/reactor-concept-14.png)


**Ответ:**

1. Обе реализуют Reactive Streams.
2. `Mono` ≈ `Single`/`Maybe`; `Flux` ≈ `Observable`/`Flowable`.
3. RxJava — Android, старые проекты. Reactor — **стандарт Spring** (WebFlux, R2DBC).
4. Смешивать через адаптеры можно, но в новом Spring-коде лучше не смешивать.
5. На собеседовании: «API похожи, для Spring Boot — Reactor».

**Вопрос:** *How does Project Reactor differ from RxJava?*

**Источник:** [EasyInterview — Project Reactor](https://easyinterview.me/interview-questions/project-reactor)

> **EN:** «How does Project Reactor differ from RxJava?» (common interview question)

> **RU:** Частый вопрос на собеседованиях.

---

## 15. Когда реактивный подход уместен, а когда нет

> **Аналогия из жизни:** Reactive — **скоростной автобус с одной полосой** (мало потоков, много пассажиров, если никто не «застрял» в дверях). Обычный MVC + virtual threads — **такси на каждого** (проще, если поездок немного и без стриминга).

![§15 Когда reactive](./Images-docs/reactor-concept-15.png)


**Ответ:**

**Берите reactive, если:**

1. Стек неблокирующий: WebFlux + R2DBC/WebClient, без `block()`.
2. Нужна высокая конкурентность I/O или стриминг (SSE, WebSocket).
3. Команда готова к reactive-отладке.

**Не берите, если:**

1. Основной доступ — JDBC/JPA.
2. Простой CRUD — MVC + virtual threads часто проще.
3. Reactive «только в контроллере», а внутри `block()` — смысла нет.

**Вопрос:** *When should you use reactive programming?*

**Источник:** [kindatechnical — Reactive Questions](https://kindatechnical.com/reactive-processing/top-30-reactive-programming-interview-questions.html)

> **EN:** «Virtual threads solve thread scalability for 70-80% of web code. Reactive retains advantages for streaming and backpressure.»

> **RU:** «Виртуальные потоки решают масштабирование для большей части веб-кода. Reactive силён в стриминге и backpressure.»

---

## 16. Disposable и отмена подписки

> **Аналогия из жизни:** **`Disposable`** — **пульт от будильника**: подписка тикает (`Flux.interval`), пока не нажмёте **выключить** (`dispose()`).

![§16 Disposable](./Images-docs/reactor-concept-16.png)


**Ответ:**

1. `subscribe()` возвращает **`Disposable`** — «ручку» подписки.
2. `dispose()` — отмена: upstream получает cancel, ресурсы освобождаются.
3. Нужно явно: `Flux.interval`, WebSocket, shutdown приложения.
4. В WebFlux-контроллере Spring управляет подпиской сам.
5. Пример:

```java

Disposable sub = Flux.interval(Duration.ofSeconds(1))
    .subscribe(System.out::println);

sub.dispose();
```
**Вопрос:** *What is a Disposable?*

**Источник:** [EasyInterview — Subscription and Lifecycle](https://easyinterview.me/interview-questions/project-reactor)

> **EN:** «What is a Disposable and how do you manage subscriptions?»

> **RU:** Стандартный вопрос по жизненному циклу подписки.

---

## 17. Блокирующий код внутри реактивной цепочки

> **Аналогия из жизни:** Поток Netty — **единственная касса в супермаркете**. **`block()` / JDBC** — покупатель **5 минут ищет сдачу** — очередь встаёт. **`boundedElastic`** — **отдельная касса «медленные операции»**.

![§17 Блокирующий код](./Images-docs/reactor-concept-17.png)


**Ответ:**

1. JDBC, `Thread.sleep`, sync-код **занимают поток** — на Netty это останавливает другие запросы.
2. Не выполняйте блокировку на `parallel()`, `single()`, потоке Netty.
3. Legacy JDBC — оберните и перенесите:

```java

Mono.fromCallable(() -> jdbcTemplate.queryForObject(...))
    .subscribeOn(Schedulers.boundedElastic());
```
4. Лучше: R2DBC вместо JDBC, WebClient вместо sync HTTP.
5. `.block()` внутри `flatMap` в WebFlux — **нельзя**.

**Вопрос:** *How do you handle blocking operations in reactive code?*

**Источник:** [Reactor — Schedulers](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html)

> **EN:** «boundedElastic is made to help with legacy blocking code if it cannot be avoided.»

> **RU:** «boundedElastic — для legacy-блокирующего кода, если его нельзя убрать.»

---

## 18. Краткая шпаргалка по операторам

> **Аналогия из жизни:** Операторы — **надписи над станками на конвейере**: «перекрасить» (`map`), «открыть коробку и достать содержимое» (`flatMap`), «пропустить брак» (`filter`), «взять первые три» (`take`).

![§18 Шпаргалка операторов](./Images-docs/reactor-concept-18.png)


**Ответ:**

| Задача | Оператор |
|--------|----------|
| Преобразовать значение (лямбда → обычный объект) | `map` |
| Лямбда → `Mono`/`Flux` | `flatMap` |
| Порядок важнее скорости | `concatMap` |
| Отфильтровать | `filter` |
| Первые N | `take` |
| Два потока парами | `zip` |
| Два потока по готовности | `merge` |
| Два потока по очереди | `concat` |
| Ждать не дольше N | `timeout` |
| Отладка | `doOnNext`, `log()` |

**merge** — «перемешать по готовности»; **concat** — «строго один за другим».

**Вопрос:** *What are the most commonly used transformation operators?*

**Источник:** [Reactor — which operator](https://projectreactor.io/docs/core/release/reference/#which-operator)

> **EN:** «map … flatMap … filter … zip …»

> **RU:** Чаще всего спрашивают map, flatMap, filter, zip.

---

## Мини-пример (reactive-demo)

```java

// UserService.java
return userRepository.findById(id)
    .flatMap(user -> orderRepository.findByUserId(user.id())
        .collectList()
        .map(orders -> UserSummaryResponse.of(user, orders)));

// UserController.java — subscribe() не вызываем
@GetMapping("/{id}/summary")
public Mono<UserSummaryResponse> getUserSummary(@PathVariable Long id) {
    return userService.getUserSummary(id);
}
```
Живые примеры map/flatMap: модуль **`reactive-demo`**, порт **8081**, раздел 6 этого документа.

---

## Полезные ссылки

| Ресурс | URL |
|--------|-----|
| Reactor Reference Guide | https://projectreactor.io/docs/core/release/reference/ |
| Flux API | https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html |
| Baeldung — Reactor | https://www.baeldung.com/reactor-core |
| reactive-demo в проекте | `reactive-demo/README.md` |

---

*Документ для подготовки к собеседованиям. Визуализация — только PNG в `docs/Images-docs/` (генератор `gen_reactor_diagrams.py`). Правило: `.cursor/rules/reactor-docs-visual.mdc`.*
