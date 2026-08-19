# Reactor: subscribeOn, publishOn, Mono.defer/fromCallable, Schedulers.immediate()

## Оглавление

1. [subscribeOn — управление upstream](#1-subscribeon--управление-upstream)
2. [publishOn — управление downstream](#2-publishon--управление-downstream)
3. [Ключевая разница между subscribeOn и publishOn](#3-ключевая-разница-между-subscribeon-и-publishon)
4. [Mono.fromCallable vs Mono.defer](#4-monofromcallable-vs-monodefer)
5. [Почему WebClient не блокирует event-loop](#5-почему-webclient-не-блокирует-event-loop)
6. [Schedulers.immediate() — когда применяется](#6-schedulersimmediate--когда-применяется)
7. [Итоговая таблица решений](#7-итоговая-таблица-решений)

---

## 1. subscribeOn — управление upstream

`subscribeOn` определяет, на каком потоке будет выполняться **подписка** и весь **upstream** (всё, что находится выше него по цепочке до самого источника), независимо от того, в каком месте цепочки он стоит.

**Когда использовать:** есть единичный **блокирующий** вызов (JDBC без реактивного драйвера, legacy-клиент, синхронный SDK), который нужно не выполнять на event-loop потоке.

```java
Mono<Account> getAccount(String id) {
    return Mono.fromCallable(() -> jdbcRepo.findById(id)) // блокирующий вызов
        .subscribeOn(Schedulers.boundedElastic());        // выполняется на отдельном пуле
}
```

Внутри `flatMap` с несколькими внешними вызовами `subscribeOn()` ставится на каждый отдельный внутренний блокирующий `Publisher`.
- `Mono.zip()` агрегирует результаты источников;
- параллельное выполнение блокирующих вызовов в этом примере обеспечивается отдельным `subscribeOn(Schedulers.boundedElastic())` для каждого `Mono.fromCallable(...)`.

```java
Mono<AccountView> aggregate(String id) {
    
    Mono<Account> account = accountService.get(id); // уже реактивный
    
    Mono<Score> score = Mono.fromCallable(() -> legacyScoreClient.fetch(id))
        .subscribeOn(Schedulers.boundedElastic());
    
    Mono<History> history = Mono.fromCallable(() -> legacyHistoryClient.fetch(id))
        .subscribeOn(Schedulers.boundedElastic());

    return Mono.zip(account, score, history)
        .map(t -> new AccountView(t.getT1(), t.getT2(), t.getT3()));
}
```

- `subscribeOn` действительно физически переносит выполнение (включая блокирующий вызов)
на другой пул потоков — это и есть его прямая задача, 
- "снять" блокирующую работу с **event-loop** потока и отправить её на `boundedElastic`.

---

## Какая роль оператора zip()

`Mono.zip(account, score, history)` создаёт итоговый `Mono`, который при подписке подпишется на все три исходных `Mono`: `account`, `score` и `history`.

Подписка выполняется **не в момент вызова `Mono.zip(...)`**, а когда кто-то подпишется на возвращённый `Mono` — например, Spring WebFlux, при обработке результата контроллера. 
- Внутри `MonoZip`, подписки запускаются **последовательно** в цикле, но без ожидания результата предыдущего источника. 
- Поэтому `score` и `history` начинают работу почти одновременно и могут выполняться конкурентно.

```java
return Mono.zip(account, score, history)
    .map(t -> new AccountView(t.getT1(), t.getT2(), t.getT3()));
```

`zip` не создаёт потоки. 
- Конкурентность будет, если `score` и `history` являются асинхронными `Mono`, например результатами HTTP-вызовов `WebClient`.

В официальной реализации `reactor.core.publisher.MonoZip`, в методе `ZipCoordinator.request(long n)`, Reactor вызывает `subscribe(...)` для каждого source `Mono`:

**Исходный код:** https://github.com/reactor/reactor-core/blob/main/reactor-core/src/main/java/reactor/core/publisher/MonoZip.java

```java
for (int i = 0; i < subscribers.length; i++) {
    monos[i].subscribe(subs[i]);
}
```

`monos[i]` — это `account`, `score`, `history`; `subs[i]` — внутренний `ZipInner`, который является `CoreSubscriber` для соответствующего source `Mono`.

```java
return Mono.zip(account, score, history) // подписка на ВСЕ ТРИ происходит практически в один момент
    .map(t -> new AccountView(t.getT1(), t.getT2(), t.getT3()));
```


## Итоговая формулировка

`subscribeOn` — это "на каком потоке выполнится ЭТА конкретная подписка" (то есть перевод поезда с данными на запасной путь, чтобы не блокировать основную линию). 
## Итоговая формулировка

`subscribeOn` задаёт **Scheduler**, на котором будет выполнена подписка и upstream-работа конкретной реактивной цепочки. Для блокирующего источника `Mono.fromCallable(...)` его обычно ставят сразу после источника, чтобы вызов не блокировал event loop.

- `zip` / `merge` / `flatMap` сами по себе не создают потоки и не переключают Scheduler.

- Они управляют подписками на внутренние `Publisher`:
    - `zip` ждёт значение от каждого источника и объединяет эти значения в один результат;
    - `merge` подписывается на несколько независимых источников и передаёт в один `Flux` все их элементы в порядке фактического поступления;
    - `flatMap` для **каждого элемента исходного `Flux`** создаёт inner-`Publisher` через `mapper`, подписывается на них и передаёт их результаты в общий поток без гарантии порядка;
    - `concatMap` делает то же, что `flatMap`, но подписывается на следующий inner-`Publisher` только после завершения предыдущего, поэтому сохраняет порядок исходных элементов.


- Например, если есть **три независимых потока** уведомлений, `merge` просто объединит все уведомления в одну ленту. 
- Если же есть `Flux<Order>`, а для каждого заказа нужно вызвать `Mono<Invoice>`, применяется `flatMap` или `concatMap`.


**Источник:** https://projectreactor.io/docs/core/release/reference/apdx-operatorChoice.html

Официальный справочник Reactor различает 
 - **объединение** нескольких источников (`merge`), 
 - последовательное объединение (`concat`) и 
 - асинхронное преобразование каждого элемента в `Publisher` (`flatMap` / `concatMap`).


- Если несколько независимых `Mono.fromCallable(...)` передать в `Mono.zip(...)` и у каждого есть `subscribeOn(Schedulers.boundedElastic())`, их блокирующие вызовы могут выполняться одновременно на worker’ах `boundedElastic`.
  - Если такие `Mono` создавать или подписывать последовательно, например через последовательные `then(...)`, то `subscribeOn` только вынесет каждый вызов с event loop, но не создаст конкурентного запуска.
---


 В методе _**aggregate()**_ из примера выше, подписчика **ещё нет**: он только возвращает `Mono<AccountView>`.

_Подписчик_ появляется в момент внешней **подписки** — например:

```java
aggregate("42").subscribe();
```

В Spring WebFlux эту подписку обычно выполняет сам фреймворк при обработке HTTP-ответа.

После этого `Mono.zip(...)` подписывается на `account`, `score` и `history`; для `score` и `history` `subscribeOn` переносит их подписку и выполнение `fromCallable` на `boundedElastic`.

Источник: https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html

## Что такое boundedElastic на самом деле

`boundedElastic` — это **пул** потоков (по умолчанию до 10 × число ядер CPU), а не один поток. 

- **Пул** создаёт и пере-использует физически разные **threads** из ОС, распределяя задачи между ними.

## Так параллельно или последовательно ?

Когда `zip` подписывается на `score` и `history` "одновременно" — это значит, что обе подписки регистрируются в один момент в коде (без ожидания одной перед стартом другой). 

Но что происходит физически, зависит от пула:

- Если у `boundedElastic` есть два свободных потока — `score` и `history` реально выполнятся **параллельно**, на двух разных физических потоках одновременно.
- Если пул занят и свободен только один поток — обе задачи встанут в очередь и выполнятся **последовательно на одном потоке**, просто по очереди, без ожидания завершения одной перед постановкой другой в очередь.


## Ключевое разделение понятий

- **Подписка на источники** — `zip` координирует несколько независимых source-`Publisher`: при подписке на итоговый `Mono` он подписывается на все источники, не ожидая результатов предыдущих.
- **Планирование работы** — `subscribeOn(Schedulers.boundedElastic())` назначает `boundedElastic` для подписки и upstream-работы конкретного источника.
- **Физическое выполнение** — наличие параллельной работы на разных потоках определяется доступными worker-потоками `boundedElastic`, а не оператором `zip`.

Итог:

- `zip` инициирует подписки на независимые источники и объединяет их результаты, но сам не создаёт потоки;
- `subscribeOn(boundedElastic())` планирует блокирующий источник на `boundedElastic`;
- разные источники могут физически выполняться одновременно, если у `boundedElastic` есть свободные worker-потоки;
- если свободных worker-потоков нет, задачи ожидают в очереди `Scheduler`.

**Источник:** https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html

> “Up to 100 000 tasks submitted after the cap has been reached are enqueued and will be re-scheduled when a thread becomes available.”

RU:

> «До 100 000 задач, отправленных после достижения лимита, ставятся в очередь и будут запланированы повторно, когда поток станет доступен.»

## 2. publishOn — управление downstream

`publishOn` переключает поток исполнения только для операторов, идущих **после** него в цепочке (downstream). 
- Он не может повлиять на то, что уже выполнилось выше по цепочке.

**Когда использовать:** 
 - после реактивного (неблокирующего) вызова нужно перенести дальнейшую **CPU-интенсивную** обработку на другой пул, чтобы не грузить **event-loop**.
 - `publishOn()` не обязательно нужен именно после реактивного вызова; 
   - его ставят перед участком **downstream**, который нужно перенести на другой **Scheduler**.


```java
repository.findAll()                       // неблокирующий реактивный вызов
    .publishOn(Schedulers.parallel())      // переключаем ДАЛЬНЕЙШУЮ обработку
    .map(this::heavyCpuComputation);       // тяжёлые вычисления не на event-loop
```

**Частая ошибка:** 
 - ставить **publishOn** _ПОСЛЕ_ блокирующего вызова внутри **flatMap**, надеясь исправить блокировку постфактум. 
 - **Это не работает** — блокировка уже произошла до `publishOn()`:

```java
// НЕ решает проблему: блокировка случилась ДО publishOn
repository.findAll()
    .flatMap(item -> Mono.fromCallable(() -> remoteBlockingCall(item))) // блокирует здесь
    .publishOn(Schedulers.boundedElastic()) // слишком поздно использовать publishOn
    .map(this::process);
```

 - _Как правильно сделать ?_ — **subscribeOn** ставится прямо на внутренний Mono внутри **flatMap**:

```java
  repository.findAll()
                .flatMap(
        item -> Mono.fromCallable(
                                        () -> remoteBlockingCall(item)
                                )
                                        .subscribeOn(Schedulers.boundedElastic()) // блокировка уходит в правильный пул
        )
        .map(this::process);
```

---

## 3. Ключевая разница между subscribeOn и publishOn

| Критерий | subscribeOn | publishOn |
|---|---|---|
| Влияет на | **upstream** (всё выше по цепочке, от точки подписки) | **downstream** (только то, что после него) |
| Место в цепочке | Не важно, где стоит — эффект применяется "снизу вверх" | **Важно**: применяется только к операторам ниже точки установки |
| Типичный кейс | Блокирующий вызов (JDBC, legacy SDK) | Перенос тяжёлых вычислений после реактивного вызова |
| Множественное использование | Только последний вызванный **subscribeOn** в цепочке имеет эффект на общий upstream | Каждый **publishOn** переключает поток с этой точки и далее |

---

## 4. Mono.fromCallable vs Mono.defer

- Оба метода дают **ленивое (lazy) вычисление** — код внутри не выполняется в момент сборки цепочки, а только в момент подписки. 
- Разница — в типе того, что они оборачивают.

| | Mono.fromCallable | Mono.defer |
|---|---|---|
| Принимает | `Callable<T>` — обычное синхронное значение | `Supplier<Mono<T>>` — уже готовый Mono |
| Назначение | Обернуть **блокирующий синхронный** вызов | Отложить создание **уже реактивного** Mono (пересоздаётся при каждой подписке) |

```java
// fromCallable: getCurrency() — синхронный блокирующий метод, возвращает Currency
Mono.fromCallable(() -> getCurrency(code))
    .subscribeOn(Schedulers.boundedElastic());

// defer: getCurrencyReactive() уже возвращает Mono<Currency>, но вызов метода нужно отложить
Mono.defer(() -> getCurrencyReactive(code));
```

**Почему не Mono.just:** 
 - `Mono.just(x)` вычисляет `x` немедленно, в момент сборки цепочки — то есть блокирующий вызов внутри `Mono.just(getCurrency(...))` выполнится сразу на текущем (например, event-loop) потоке, и никакой `subscribeOn/publishOn` после него уже не спасёт.

```java
// ОШИБКА: getCurrency() вызывается СРАЗУ, на текущем потоке
Mono.just(getCurrency(code))
    .subscribeOn(Schedulers.boundedElastic()); // бесполезно, блок уже случился
```

**Если блокирующий метод возвращает не `Mono`**, `defer` не даёт преимущества. 

Технически можно написать:

```java

Mono.defer(() -> Mono.fromCallable(() -> getCurrency(code)))
```

Но это избыточно, поэтому правильно сделать так:

```java
`Mono.fromCallable(() -> getCurrency(code))` 
```

 - уже ленивый и предназначен для обёртки синхронного вызова.
---

## 5. Почему WebClient не блокирует event-loop

`WebClient` — неблокирующий HTTP-клиент Spring. 
При использовании Reactor Netty он работает через event loop и неблокирующий сетевой I/O.
 
**Netty**, имеет сетевой I/O полностью асинхронный на уровне ОС (epoll/selector).
 - При вызове `webClient.get()...` event-loop поток не "ждёт" ответ — он отправляет запрос в сокет и немедленно освобождается для других задач. 
 - Когда данные приходят, Netty уведомляет event-loop через событие готовности сокета, и тот `издает` значение в Mono — без блокирующего ожидания.

```java
Mono<Currency> currency = webClient.get()
    .uri("/rate/{code}", code)
    .retrieve()
    .bodyToMono(Currency.class); // поток не "спит" даже если ответ идёт 5 секунд
```

Разница **с блокирующим вызовом** (RestTemplate, JDBC): 
 - там поток физически находится в состоянии WAITING/BLOCKED на всё время I/O. 
 - Поэтому реактивный клиент масштабируется на малом числе потоков, даже при долгих внешних вызовах.

---

## 6. `Schedulers.immediate()` — когда применяется

`Schedulers.immediate()` не создаёт отдельный поток и не выполняет переключение контекста. Задача запускается сразу в том потоке, который вызвал планирование.

**Когда уместен:**

 - Как значение по умолчанию для параметра типа `Scheduler`, если выполнение должно остаться в текущем потоке.
 - В тестах, когда важно выполнить цепочку синхронно и предсказуемо, без переключения между потоками.
 - В библиотечном коде, когда `Scheduler` передаётся извне, а отсутствие переключения потока является допустимым вариантом поведения.

В прикладном коде `Schedulers.immediate()` используется редко. 
 - Он не подходит для переноса блокирующих операций: 
   - такая операция заблокирует текущий поток, в том числе **event loop**.

**Источник:** https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html

> “No execution context (`Schedulers.immediate()`): at processing time, the submitted `Runnable` will be directly executed, effectively running them on the current `Thread`.”

RU:

> «Без отдельного контекста выполнения (`Schedulers.immediate()`): переданный `Runnable` запускается непосредственно в момент обработки, то есть фактически выполняется на текущем потоке.»

---

## 7. Итоговая таблица решений

| Ситуация | Что использовать                                                                                                         |
| :-- |:-------------------------------------------------------------------------------------------------------------------------|
| Единичный блокирующий вызов (JDBC, legacy SDK) | `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`                                                        |
| Блокирующий вызов внутри `flatMap` | `subscribeOn` на внутреннем `Mono`, а не `publishOn` после `flatMap`                                                     |
| Несколько независимых блокирующих вызовов | `subscribeOn` на каждом + `Mono.zip` / `flatMap` / `merge`; фактическая одновременность зависит от доступности worker’ов |
| CPU-интенсивная downstream-обработка | `publishOn(Schedulers.parallel())`                                                                                       |
| Уже реактивный неблокирующий вызов (`WebClient`, R2DBC) | Scheduler для I/O не нужен; также нельзя блокировать цепочку через `block()` или синхронные вызовы                       |
| Нужно лениво вызвать блокирующий метод | `Mono.fromCallable(...)`, не `Mono.just(...)`                                                                            |
| Нужно отложить создание реактивного `Mono` | `Mono.defer(...)`                                                                                                        |
| Scheduler обязателен, но переключение не нужно | `Schedulers.immediate()`                                                                                                 |