# Hot Publishers и Cold Publishers в Project Reactor, различия

## Оглавление

- [Базовые паттерны: publish и replay](#base)
- [1. Разница между Cold и Hot](#1-%D1%80%D0%B0%D0%B7%D0%BD%D0%B8%D1%86%D0%B0-%D0%BC%D0%B5%D0%B6%D0%B4%D1%83-cold-%D0%B8-hot)
- [2. Две базы: publish() и replay()](#2-%D0%B4%D0%B2%D0%B5-%D0%B1%D0%B0%D0%B7%D1%8B-publish-%D0%B8-replay)
- [3. Стратегии подключения](#3-%D1%81%D1%82%D1%80%D0%B0%D1%82%D0%B5%D0%B3%D0%B8%D0%B8-%D0%BF%D0%BE%D0%B4%D0%BA%D0%BB%D1%8E%D1%87%D0%B5%D0%BD%D0%B8%D1%8F)
- [4. share() и cache() — готовые комбинации](#4-share-%D0%B8-cache--%D0%B3%D0%BE%D1%82%D0%BE%D0%B2%D1%8B%D0%B5-%D0%BA%D0%BE%D0%BC%D0%B1%D0%B8%D0%BD%D0%B0%D1%86%D0%B8%D0%B8)
- [5. Итоговая таблица](#5-%D0%B8%D1%82%D0%BE%D0%B3%D0%BE%D0%B2%D0%B0%D1%8F-%D1%82%D0%B0%D0%B1%D0%BB%D0%B8%D1%86%D0%B0)

***

<a id="base"></a>
## Базовые паттерны: publish и replay

`ConnectableFlux` — это специальный тип Flux, который позволяет нескольким подписчикам собраться вместе, прежде чем запустится подписка на источник и начнётся генерация данных.

- Источник: https://projectreactor.io/docs/core/release/reference/advancedFeatures/advanced-broadcast-multiple-subscribers-connectableflux.html

EN:

> "Two main patterns are covered in the Flux API that return a ConnectableFlux: publish and replay. publish dynamically tries to respect the demand from its various subscribers, in terms of backpressure, by forwarding these requests to the source... replay buffers data seen through the first subscription, up to configurable limits (in time and buffer size). It replays the data to subsequent subscribers."

RU:

> "Два основных паттерна покрыты в API Flux, которые возвращают ConnectableFlux: 
> **publish** и **replay**.
> 
> **publish** динамически старается учитывать спрос различных подписчиков в терминах **backpressure**, перенаправляя эти запросы к источнику... 
> 
> **replay** буферизует данные, увиденные при первой подписке, до настраиваемых пределов (по времени и размеру буфера). Он воспроизводит эти данные последующим подписчикам."

Это ключевое различие: 
- `publish` **не хранит историю** и просто транслирует "здесь и сейчас" с учётом **backpressure**, а 
- `replay` буферизует прошедшие элементы и отдаёт их целиком каждому новому подписчику.

---

## `publish`, `share`, `cache`

- `publish()` создаёт `ConnectableFlux`. Он **не запускает** _upstream_ без `connect()`, `autoConnect(...)` или `refCount(...)`.
- `share()` — алиас `publish().refCount(1)`.
- `cache()` сохраняет сигналы и воспроизводит их поздним подписчикам; кэш можно ограничить числом элементов или TTL.


Источник: https://www.javadocs.dev/io.projectreactor/reactor-core/3.6.18/reactor/core/publisher/Flux.html

> “When all subscribers have cancelled it will cancel the source Flux. This is an alias for publish(). ConnectableFlux.refCount().”

RU:

> «Когда все подписчики отменили подписку, исходный `Flux` отменяется. Это **алиас** для `publish().refCount()`.»

---


## `autoConnect` и `refCount`

| Оператор | Запуск upstream | Отписка downstream |
| :-- | :-- | :-- |
| `connect()` | Явный вызов `connect()` | Не отключает автоматически |
| `autoConnect(n)` | После $n$ подписчиков | Не отменяет upstream |
| `refCount(n)` | После $n$ подписчиков | Отменяет upstream, когда подписчиков стало меньше $n$ |
| `refCount(n, gracePeriod)` | После $n$ подписчиков | Ждёт `gracePeriod`; отменяет, если порог $n$ не восстановился |

`refCount()` без аргумента означает `refCount(1)`: первая подписка запускает upstream, отмена последней — отключает его.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/ConnectableFlux.html

> “Connects to the upstream source when the first Subscriber subscribes and disconnects when all Subscribers cancelled or the upstream source completed.”

RU:

> «Подключается к upstream-источнику, когда подписывается первый подписчик, и отключается, когда все подписчики отменили подписку либо источник завершился.»


## 1. Разница между Cold и Hot

**Cold Publisher** — каждый новый подписчик получает **данные с нуля**, независимо от других. 
- Аналог подкаста: каждый слушатель _начинает с начала_ слушать передачу по радио.

**Hot Publisher** — данные производятся независимо от подписки; 
 - подключившиеся зрители, позже видят только то, что происходит после их подписки. 
 - Аналог живого теле-эфира.

По умолчанию, все Publisher в Reactor — **cold**:

```java
Flux<Long> cold = Flux.interval(Duration.ofSeconds(1));
cold.subscribe(a -> log.info("A: {}", a)); // считает с 0
        
Thread.sleep(3000);

cold.subscribe(b -> log.info("B: {}", b)); // ТОЖЕ считает с 0, независимо от A
```

Источник: https://temofeev.ru/info/articles/reaktivnoe-programmirovanie-so-spring-chast-2-project-reactor/

***

## 2. Две базы: publish() и replay()

Два основных паттерна Flux API возвращают **ConnectableFlux**: 
- **publish** и **replay**. 

Они отличаются тем, **хранится ли история** произведенных(выпущенных) элементов.

Источник: https://projectreactor.io/docs/core/release/reference/advancedFeatures/advanced-broadcast-multiple-subscribers-connectableflux.html

EN:

> "Two main patterns are covered in the Flux API that return a ConnectableFlux: publish and replay. publish dynamically tries to respect the demand from its various subscribers, in terms of backpressure, by forwarding these requests to the source... replay buffers data seen through the first subscription, up to configurable limits (in time and buffer size). It replays the data to subsequent subscribers."

RU:

> "Два основных паттерна покрыты в API Flux, которые возвращают ConnectableFlux: **publish** и **replay**. 
> **publish** динамически старается учитывать спрос различных подписчиков в терминах **backpressure**, перенаправляя эти запросы к источнику... 
> 
> **replay** буферизует данные, увиденные при первой подписке, до настраиваемых пределов (по времени и размеру буфера). Он воспроизводит эти данные последующим подписчикам."

Вывод:

- **publish** — это "эфир без записи" (только текущие данные), 
- **replay** — "эфир с записью" (полная история для любого подписчика, даже опоздавшего).

***

## 3. Стратегии подключения

`publish()` превращает cold Flux в **ConnectableFlux** — объект, который сам по себе ничего не производит, пока не запущено подключение к источнику одним из трёх способов.

```java
ConnectableFlux<Long> connectable = 
        Flux.interval(Duration.ofSeconds(1))
                .publish();
```


### connect() — вручную

Источник: https://projectreactor.io/docs/core/release/reference/advancedFeatures/advanced-broadcast-multiple-subscribers-connectableflux.html

EN:

> "connect() can be called manually once you reach enough subscriptions to the Flux. That triggers the subscription to the upstream source."

RU:

> "connect() можно вызвать вручную, когда набрано достаточно подписок на Flux. 
> Это запускает подписку на исходный источник."

```java
connectable.subscribe(a -> log.info("A: {}", a)); // просто регистрируется
        
connectable.subscribe(b -> log.info("B: {}", b)); // просто регистрируется
        
connectable.connect(); // ТОЛЬКО теперь начинается эмиссия для всех сразу
```

---

## Пример: `publish().connect()`

Платёжный сервис должен начать чтение событий Kafka в заранее определённый момент — например, после завершения инициализации приложения, до подключения клиентов.

```java
ConnectableFlux<PaymentEvent> events =
    paymentEventConsumer.events().publish();

events.subscribe(auditService::save);
events.subscribe(fraudService::check);

events.connect();
```

До `connect()` подписчики зарегистрированы, но чтение событий не началось. `connect()` создаёт одну upstream-подписку; оба сервиса получают одни и те же события.

---

### autoConnect(n) — автоматически, без остановки

Источник: https://projectreactor.io/docs/core/release/reference/advancedFeatures/advanced-broadcast-multiple-subscribers-connectableflux.html

EN:

> "autoConnect(n) can do the same job automatically once n subscriptions have been made."

RU:

> "autoConnect(n) выполняет ту же работу автоматически, как только сделано **n** подписок (подключилось n потребителей, не менее заданного числа)."

```java
Flux<Long> hot = Flux.interval(Duration.ofSeconds(1))
    .publish()
    .autoConnect(2); // старт сам при 2-м подписчике, НЕ останавливается никогда
```

- После срабатывания поток продолжает работать и издавать данные для всех — и для тех, кто был при запуске, и для тех, кто подключился позже. 
Он никогда сам не остановится и не проверяет условие повторно.


### refCount(n) — автоматически, с остановкой и рестартом

Источник: https://projectreactor.io/docs/core/release/reference/advancedFeatures/advanced-broadcast-multiple-subscribers-connectableflux.html

EN:

> "refCount(n) not only automatically tracks incoming subscriptions but also detects when these subscriptions are cancelled. If not enough subscribers are tracked, the source is 'disconnected,' causing a new subscription to the source later if additional subscribers appear."

RU:

> "refCount(n) не только автоматически отслеживает входящие подписки, но и обнаруживает их отмену. 
> Если отслеживается недостаточно подписчиков, источник 'отключается', что вызывает новую подписку позже, если появятся дополнительные подписчики."

```java
Flux<Long> hot = Flux.interval(Duration.ofSeconds(1))
    .publish()
    .refCount(2); // старт при 2 подписчиках, СТОП при 0, возможен рестарт
```

Итог: 
 - **autoConnect** умеет только включаться, 
 - **refCount** умеет включаться, гаснуть и включаться заново.

***

---

## Пример: `autoConnect(2)`

Источник должен начаться только после готовности двух сервисов, но после старта **не должен останавливаться** из-за отключения **WebSocket**-клиентов.

```java
Flux<MarketRate> rates =
    rateProvider.liveRates()
        .publish()
        .autoConnect(2);
```

Вторая подписка запускает получение курсов. Последующие отмены подписок не отменяют **upstream** автоматически.

---

## Пример: `refCount(2)`

Поток надо запускать, только когда одновременно доступны _потребители_ **аудита** и **antifraud**.

```java
Flux<PaymentEvent> events =
    paymentEventConsumer.events()
        .publish()
        .refCount(2);
```

- Первый подписчик только ожидает. 
- Второй запускает **upstream**. 
- Если один из двух потребителей отключится, **число подписчиков** станет **меньше** 2-х — **upstream** будет **отменён**.

---

## Пример: `refCount(2, gracePeriod)`

Краткий сетевой разрыв у одного потребителя не должен перезапускать потребление событий.

```java
Flux<PaymentEvent> events =
    paymentEventConsumer.events()
        .publish()
        .refCount(2, Duration.ofSeconds(30));
```

- После падения числа подписчиков ниже двух Reactor ждёт 30 секунд. **Если** нужное _число подписчиков_ **восстановится** за это время, **upstream** не отменяется.


## 4. share() и cache() — готовые комбинации

`share()` — это шорткат для `publish().refCount(1)`: 
 - живой эфир **без хранения истории**, гаснет при нулевом числе подписчиков.

Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/ConnectableFlux.html

EN:

> "Connects to the upstream source when the first Subscriber subscribes and disconnects when all Subscribers cancelled or the upstream source completed."

RU:

> "Подключается к исходному источнику при подписке первого Subscriber и отключается, когда все Subscriber отменили подписку или исходный источник завершился."

```java
Flux<Long> ticker = Flux.interval(Duration.ofSeconds(1))
        .share();
ticker.subscribe(t -> log.info("A sees: {}", t));
// через 3 секунды подключается B
ticker.subscribe(t -> log.info("B sees: {}", t)); // B пропустил первые 3 события
```
---

## Пример: `share()`

- WebSocket-клиенты смотрят статусы платежей. **Нельзя создавать** Kafka-подписку или запрос к шлюзу **для каждого клиента**.

```java
Flux<PaymentStatus> statuses =
    paymentGateway.statusEvents()
        .share();
```

Первый клиент запускает `statusEvents()`.
- Все текущие клиенты получают одни и те же новые статусы. После отключения последнего клиента **upstream** отменяется.
- 
---

`cache()` — это шорткат для `replay().autoConnect(1)`: тоже hot-паттерн, но с полной историей. Первый подписчик реально выполняет запрос, результат сохраняется, все последующие подписчики получают тот же результат, даже подключившись намного позже.

Источник: https://stackoverflow.com/questions/68466979/what-is-the-difference-between-flux-cache-replay-and-publish-if-creating

```java
Mono<UserProfile> profile = userService.fetchProfile(id).cache();
profile.subscribe(a -> log.info("A: {}", a)); // реальный запрос выполняется
profile.subscribe(b -> log.info("B: {}", b)); // получает тот же результат из кэша
```

Применение **cache()**: 
- данные, которые не меняются в рамках жизни объекта или запроса — справочники, конфигурация, разово посчитанный агрегат.

***

---

## Пример: `cache()`

Сервис возвращает **справочник банков.** Источник нужно вызвать один раз, а результат **повторно использовать** для следующих HTTP-запросов.

```java
Flux<Bank> banks =
    bankClient.loadBanks()
        .cache(Duration.ofMinutes(10));
```

Первый HTTP-запрос запускает `loadBanks()`. 
- В течение 10 минут последующие запросы получают сохранённые элементы **без необходимости создания новой подписки** на источник.

---
## 5. Итоговая таблица

| Оператор | База | Хранит историю | Запуск эмиссии | Стоп при 0 подписчиков | Рестарт | Типичный кейс |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| publish() + connect() | publish | Нет | Вручную, по команде | Нет | Только новый connect() | Точный контроль момента старта |
| publish().autoConnect(n) | publish | Нет | Автоматически при n подписчиках | Нет | Нет | Разогрев потока для группы потребителей |
| publish().refCount(n) / share() | publish | Нет | Автоматически при n подписчиках | Да | Да, при новой подписке | Живой эфир с экономией ресурсов |
| cache() | replay | Да | При первой подписке | Нет | Нет | Справочники, разовый агрегат |

Мнемоника: publish — эфир без записи, replay — эфир с записью; refCount умеет гаснуть и включаться заново, autoConnect — только включаться.

