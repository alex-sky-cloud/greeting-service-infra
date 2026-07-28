# `concat`, `concatWith`, `concatMap` vs `then`, `flatMap` в Project Reactor

## Оглавление

1. [Зачем в документе про concat-операторы обсуждается then](#зачем-в-документе-про-concat-операторы-обсуждается-then)
2. [Три разные задачи, которые легко перепутать](#три-разные-задачи-которые-легко-перепутать)
3. [`concat` / `concatWith`: объединение готовых потоков](#concat--concatwith-объединение-готовых-потоков)
4. [Как concat реально ведёт себя при ошибке](#как-concat-реально-ведёт-себя-при-ошибке)
5. [Частая путаница: `concatWith` вместо `then`](#частая-путаница-concatwith-вместо-then)
6. [`concatMap`: последовательная обработка элементов](#concatmap-последовательная-обработка-элементов)
7. [`then` внутри `concatMap`](#then-внутри-concatmap)
8. [Опасность готового Mono как аргумента](#опасность-готового-mono-как-аргумента)
9. [`flatMap`, когда нужно значение](#flatmap-когда-нужно-значение)
10. [`flatMap` vs `concatMap` на `Flux` — разница в конкурентности](#flatmap-vs-concatmap-на-flux--разница-в-конкурентности)
11. [Пример с billing](#пример-с-billing)
12. [Практическая памятка](#практическая-памятка)

---

## Зачем в документе про concat-операторы обсуждается then

Изначальная тема документа — сравнение `concat`/`concatWith`/`concatMap`. 
- Операторы `then` и `flatMap` попадают в него не случайно и не как отдельная тема, а потому что они отвечают на вопрос, который неизбежно возникает **внутри** функции-маппера `concatMap`: 
  - как построить последовательность **из нескольких асинхронных шагов** _внутри одного элемента_.

- `concatMap` гарантирует **порядок** между *элементами* внешнего потока, но ничего не знает о том, что происходит *внутри* publisher-а, который он строит для одного элемента. 
- Эту внутреннюю последовательность разработчик собирает сам, и именно тут выбор между `then` и `flatMap` становится значимым. 

- Данный документ рассматривает операторы `then`/`flatMap` как необходимый инструмент для правильного использования `concatMap`, а не как альтернативу concat-семейству.

Отдельно от `concatMap` разбирается ситуация, где `then` иногда путают с `concatWith` .

---

## Три разные задачи, которые легко перепутать

1. **Объединить два уже готовых потока элементов в один, не смешивая их** — задача `concat` / `concatWith`.
2. **Для каждого элемента потока построить свой pipeline и обработать элементы по очереди** — задача `concatMap`.
3. Внутри одного элемента (одного вызова pipeline) сделать так, чтобы **шаг B** начался **только после** успешного завершения шага **A** — задача `then` (если значение A не нужно) или `flatMap` (если оно нужно).

- Первая и вторая задачи касаются структуры *потока элементов*. 
- Третья касается структуры *одной цепочки внутри одного элемента*. 
  - Путать их — источник большинства ошибок с этими операторами.

---

## `concat` / `concatWith`: объединение готовых потоков

`Flux.concat(...)` и `concatWith(...)` применяют, когда **источники** уже существуют и их нужно **последовательно объединить** в один поток элементов: 
  - сначала все элементы первого источника, затем все элементы второго.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#concat(org.reactivestreams.Publisher...)

EN:

> "Concatenation is achieved by sequentially subscribing to the first source then waiting for it to complete before subscribing to the next, and so on until the last source completes. Any error interrupts the sequence immediately and is forwarded downstream."

RU:

> «Конкатенация достигается последовательной подпиской на первый источник,
> 
> затем **ожиданием** его завершения перед подпиской на следующий, и так далее, пока не завершится последний источник.
> 
> Любая ошибка немедленно прерывает последовательность и передаётся вниз по потоку.»

```java
public Flux<Order> loadOrders(String userId) {
    Flux<Order> cachedOrders = cacheService.getRecentOrders(userId);
    Flux<Order> freshOrders = orderRepository.findAllByUser(userId);

    return cachedOrders.concatWith(freshOrders);
}
```

`concat`/`concatWith` подходят здесь, потому что оба источника однотипны (`Flux<Order>`), и итоговый поток должен содержать элементы обоих, один за другим.

---

## Как concat реально ведёт себя при ошибке

**Важное уточнение,** которое нельзя упускать: 
 - если первый источник завершается ошибкой (`onError`), 
 - второй источник **не будет подписан** — ошибка немедленно прерывает всю цепочку `concat`, как явно указано в официальном javadoc выше. 
 - Это подтверждается и справочным руководством Reactor по обработке ошибок: 
   - любой `onError` — терминальный сигнал, который останавливает последовательность и распространяется вниз по цепочке операторов.

- Источник: https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html

Таким образом **по поведению при ошибке операторы `concatWith` и `then` эквивалентны**: оба не подпишутся на следующий шаг, если предыдущий завершился с `onError`. 
- Разница между ними лежит не в обработке ошибок, а в другом месте (см. следующий раздел).

---

## Частая путаница: `concatWith` вместо `then`

Проблема с `concatWith` не в обработке ошибок, а в том, что он **не анализирует содержимое элементов**, которые эмитирует источник. Рассмотрим кейс: перед списанием денег нужно проверить баланс, и списание должно начаться только если проверка показала, что средств достаточно.

```java
// НЕВЕРНО: concatWith не читает содержимое элемента balanceCheck
Mono<BalanceCheck> balanceCheck = paymentService.checkBalance(accountId, amount);
Mono<ChargeResult> chargeTransaction = paymentService.charge(accountId, amount);

balanceCheck.concatWith(chargeTransaction);
```

Здесь две реальные проблемы:

- `checkBalance` возвращает объект `BalanceCheck` с полем-флагом (например `isSufficient()`), а не ошибку. 
  - `concatWith` реагирует только на терминальный сигнал (`onComplete`), а не на значение внутри `onNext` — поэтому подписка на `chargeTransaction` произойдёт **независимо от значения флага**, если `balanceCheck` завершится штатно.

- **Типы результата разные**: итоговым потоком станет `Flux`, эмитирующий элементы и `BalanceCheck`, и `ChargeResult` вперемешку по типу, потому что `concatWith` объединяет потоки элементов, а не заменяет один результат другим.

Правильный инструмент — `then`, если нужен только факт успешного завершения проверки, а её значение не нужно следующему шагу:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#then(reactor.core.publisher.Mono)

EN: "Let this Mono complete then play another Mono."
RU: «Даёт этому Mono завершиться, а затем запускает другой Mono.»

```java
// ensureSufficientBalance возвращает Mono<Void>:
// onComplete — средств достаточно,
// onError(InsufficientFundsException) — недостаточно средств.
Mono<ChargeResult> chargeAfterSuccessfulCheck(String accountId, BigDecimal amount) {
    return paymentService.ensureSufficientBalance(accountId, amount)
        .then(paymentService.charge(accountId, amount));
}
```

Ключевое отличие не в том, что `then` умеет останавливать цепочку по ошибке лучше, чем `concatWith` (оба останавливают одинаково), а в том, что `ensureSufficientBalance` **сам конвертирует бизнес-условие в сигнал** (`onError`, если средств недостаточно), а не оставляет это значение в виде обычного элемента потока, который `concatWith` не станет анализировать.

---

*Краткое резюме:**


| Аспект | Суть |
| :-- | :-- |
| **Проблема `concatWith`** | Объединяет *потоки элементов*, не анализируя их значения. Подписка на второй источник происходит по `onComplete` первого — независимо от бизнес-логики внутри элемента. |
| **Суть паттерна `then`** | Цепочка строится на **сигналах завершения**, а не на значениях. Первый `Mono<Void>` сам решает: `onComplete` (успех → идём дальше) или `onError` (провал → цепочка прерывается). |
| **Контракт первого вызова** | Должен возвращать `Mono<Void>` и конвертировать бизнес-условие в терминальный сигнал: достаточно средств → `complete`, недостаточно → `error(InsufficientFundsException)`. |
| **Результат** | `then` гарантирует: второй вызов выполнится **только** при успешном завершении проверки. Никаких `if`, никаких флагов в элементах потока. |

**В одной фразе:** *Не клади бизнес-решение в `onNext` — положи его в `onComplete` / `onError`, и `then` сделает остальное.*

---

## `concatMap`: последовательная обработка элементов

`concatMap` применяют, когда есть поток элементов, а для каждого элемента нужно построить отдельный асинхронный publisher, сохраняя порядок и последовательность обработки.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#concatMap(java.util.function.Function)

EN: "Transform the elements emitted by this Flux asynchronously into Publishers, then flatten these inner publishers into a single Flux, sequentially and preserving order using concatenation."

RU: «Асинхронно преобразует элементы, испускаемые этим Flux, во внутренние Publisher, затем объединяет эти внутренние Publisher в один Flux последовательно и с сохранением порядка с помощью конкатенации.»

```java
public Flux<ReturnResult> processPendingReturns() {
    
    return returnRequestRepository.findPending() // request-1
        .concatMap(this::processOneReturn); // request-2
}
```

`concatMap` сериализует обработку разных заявок: 
 - pipeline для `request-2` не начнётся, пока не завершится pipeline для `request-1`.

---

## `then` внутри `concatMap`

`concatMap` видит лишь один `Mono<ReturnResult>`, возвращаемый маппером, и ждёт его терминального сигнала — но не выстраивает последовательность операций внутри этого Mono автоматически. 
 
 - Эту внутреннюю последовательность нужно построить явно:

```java
private Mono<ReturnResult> processOneReturn(ReturnRequest request) {
    
    return paymentService.refund(request.paymentId())
        .then(inventoryService.returnItems(request.orderId()))
        .then(accountingService.createRefundDocument(request.orderId()))
        .thenReturn(new ReturnResult(request.orderId(), "DONE"));
}
```

`then` подходит, потому что значение, эмитированное `refund(...)`, действительно не нужно следующим шагам — важен только факт успешного завершения.

---

## Опасность готового Mono как аргумента

У кода вида `a.then(b)` есть подводный камень, не связанный напрямую с семантикой оператора, а связанный с тем, как Java вычисляет аргументы метода. 
- Аргумент `b` вычисляется **до** вызова `then`, то есть выражение `paymentService.charge(...)` выполняется в момент построения цепочки.

Это безопасно, если создание Mono/Flux — чистая операция без побочных эффектов (реактивные источники по умолчанию должны быть "холодными": ничего не происходит до подписки).

- Источник: https://projectreactor.io/docs/core/release/reference/coreFeatures/reactiveStream.html (принцип "nothing happens until you subscribe")


- **Но если метод**, возвращающий Mono, содержит **eager-логику** (например, немедленно открывает соединение или пишет в лог до момента subscribe), то реальный побочный эффект может произойти раньше, чем завершится предыдущий шаг. 
  - Решение — оборачивать построение такого Mono в `Mono.defer(() -> ...)`, чтобы гарантировать, что оно строится не раньше подписки:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#defer(java.util.function.Supplier)

Во всех примерах этого документа проблемы не возникает, поскольку `paymentService.charge(...)`, `inventoryService.returnItems(...)` и другие вызовы возвращают декларативные, ленивые Mono/Flux, типичные для реактивных клиентов (R2DBC, WebClient, реактивные драйверы). Но при написании собственного кода это стоит проверять явно.

---

Пример «небезопасного» сервиса и как `defer` спасает:

***

### Опасность готового `Mono` как аргумента — пример из бизнес-кейса

```java
// ❌ ПРОБЛЕМНЫЙ СЕРВИС: eager-логика внутри метода
public class EagerPaymentService {
    
    public Mono<ChargeResult> charge(String accountId, BigDecimal amount) {
        // Этот код выполняется СРАЗУ при ВЫЗОВЕ метода, а не при subscribe!
        Connection conn = pool.acquire();           //захватили соединение
        log.info("CHARGE STARTED for {}", accountId); //записали в лог
        metrics.chargeStarted.increment();           //увеличили счётчик
        
        // Возвращаем Mono, но побочные эффекты уже произошли
        return Mono.fromFuture(
            asyncClient.executeCharge(conn, accountId, amount)
        ).doFinally(signal -> pool.release(conn));
    }
}
```

```java
// Использование в цепочке — ПРОБЛЕМА:
Mono<Void> check = ensureSufficientBalance(accountId, amount);
Mono<ChargeResult> charge = paymentService.charge(accountId, amount); // ← ВЫЧИСЛЯЕТСЯ ЗДЕСЬ!

check.then(charge).subscribe();

// Что происходит:
// 1. paymentService.charge(...) ВЫЗЫВАЕТСЯ немедленно при построении цепочки
// 2. Соединение захвачено, лог записан, метрика инкрементирована
// 3. check ешё НЕ ЗАВЕРШИЛСЯ (баланс не проверен)
// 4. Если check упадёт с ошибкой — charge НИКОГДА НЕ ЗАПУСТИТСЯ (subscribe не вызовется)
//    но побочные эффекты УЖЕ СЛУЧИЛИСЬ: соединение утеряно, лог зашумлён, метрика испорчена
```


***

### Решение — `Mono.defer`: построение Mono откладывается до подписки

```java
// ✅ ИСПРАВЛЕННЫЙ СЕРВИС: оборачиваем в defer
public class SafePaymentService {
    
    public Mono<ChargeResult> charge(String accountId, BigDecimal amount) {
        // Возвращаем Supplier<Mono> — код внутри выполнится ТОЛЬКО при subscribe
        return Mono.defer(() -> {
            Connection conn = pool.acquire();            // ТУТ, при подписке
            log.info("CHARGE STARTED for {}", accountId); // ТУТ
            metrics.chargeStarted.increment();            // ТУТ
            
            return Mono.fromFuture(
                asyncClient.executeCharge(conn, accountId, amount)
            ).doFinally(signal -> pool.release(conn));
        });
    }
}
```

```java
// Теперь безопасно:
Mono<Void> check = ensureSufficientBalance(accountId, amount);

// defer заставляет charge() строиться ЛЕНИВО — только когда then решит подписаться
check.then(SafePaymentService::charge)  // или .then(() -> paymentService.charge(...))
     .subscribe();

// Порядок гарантирован:
// 1. check завершается (onComplete)
// 2. ТОЛЬКО ТОГДА вызовется Supplier внутри defer
// 3. Соединение захвачено, лог записан — ровно в момент, когда бизнес-логика это позволяет
```


***

### Паттерн: всегда используй `defer` для методов, которые могут иметь eager-эффекты

```java
// Утилита для безопасного then с любым сервисом
static <T> Mono<T> deferThen(Mono<Void> predecessor, Supplier<Mono<T>> next) {
    return predecessor.then(Mono.defer(next));
}

// Использование — чисто и безопасно:
deferThen(
    ensureSufficientBalance(accountId, amount),
    () -> paymentService.charge(accountId, amount)
).subscribe();
```


***

### Правило большого пальца

| Ситуация | Нужен `defer`? |
| :-- | :-- |
| Реактивный клиент (R2DBC, WebClient, Redis reactive) — чистый `Mono` | ❌ Нет |
| Метод делает `new Connection()`, `log.info()`, `metrics.inc()`, `Thread.start()` **до** возврата `Mono` | ✅ **Да** |
| Не уверен — оберни в `defer`, издержки нулевые | ✅ Лучше перестраховаться |

**Коротко:** `a.then(b)` вычисляет `b` **сразу**. `a.then(Mono.defer(() -> b))` вычисляет `b` **после** `a`.
---

## `flatMap`, когда нужно значение

Если следующий шаг зависит от значения, которое вернул предыдущий Mono, `then` **использовать нельзя** — он это значение отбросит. 

Нужен `flatMap`.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#flatMap(java.util.function.Function)

EN: "Transform the item emitted by this Mono asynchronously, returning the value emitted by another Mono."

RU: «Асинхронно преобразует элемент, испущенный этим Mono, возвращая значение, испущенное другим Mono.»

```java
private Mono<ReturnResult> processOneReturn(ReturnRequest request) {
    
    return paymentService.refund(request.paymentId())
        .flatMap(refund ->
            inventoryService.returnItems(request.orderId())
                .then(accountingService.createRefundDocument(request.orderId(), refund.id()))
        )
        .map(document -> new ReturnResult(request.orderId(), "DONE"));
}
```

Здесь `flatMap` не отменяет последовательность — `returnItems(...)` создаётся и подписывается только после получения `refund`, а его назначение — передать значение `refund` дальше по pipeline (в `createRefundDocument`).

Та же логика применима к банковскому кейсу, если нужно явно проверить булево поле результата проверки, а не полагаться на ошибку:

```java
// processOneReturn: весь метод — ДЕКЛАРАТИВНЫЙ, просто СТРОИТ цепочку Mono.
// Никакого реального выполнения (нет подписки — нет работы).

private Mono<ReturnResult> processOneReturn(ReturnRequest request) {

    // 1. paymentService.refund(...) вызывается СРАЗУ при сборке цепочки,
    //    но возвращает ХОЛОДНЫЙ Mono — реальный HTTP/DB вызов начнётся ТОЛЬКО при subscribe().
    return paymentService.refund(request.paymentId())

            // 2. flatMap создаёт НОВЫЙ Mono для КАЖДОГО элемента upstream (здесь элемент один — refund).
            //    Внутри лямбды создаются ДВА Publisher'а последовательно:
            .flatMap(refund ->

                            // 2a. inventoryService.returnItems(...) вызывается СРАЗУ при прохождении flatMap
                            //     (т.е. ПОСЛЕ onNext(refund)), возвращает ХОЛОДНЫЙ Mono.
                            inventoryService.returnItems(request.orderId())

                                    // 2b. .then(...) соединяет returnItems с createRefundDocument:
                                    //    на результат подписка на createRefundDocument, произойдет только после успешного выполнения (inventoryService.returnItems)  
                                    //     Реальный вызов accountingService.createRefundDocument(...) произойдёт ТОЛЬКО
                                    //     ПОСЛЕ onComplete от returnItems.
                                    .then(accountingService.createRefundDocument(request.orderId(), refund.id()))

                    // Итог flatMap: возвращается Mono<Void> (результат then), 
                    // который завершится, когда ОБА шага (returnItems → createDoc) успешно пройдут.
            )

            // 3. map выполняется ПОСЛЕ завершения всего flatMap (после createRefundDocument).
            //    refund и document уже не в scope — нужен только orderId для DTO.
            .map(document -> new ReturnResult(request.orderId(), "DONE"));
}

/* ПОРЯДОК РЕАЛЬНОГО ВЫПОЛНЕНИЯ ПРИ subscribe():
1. paymentService.refund → onNext(refund)
2. inventoryService.returnItems → onComplete
3. accountingService.createRefundDocument → onComplete
4. map → onNext(ReturnResult) → onComplete

Все шаги СТРОГО ПОСЛЕДОВАТЕЛЬНЫ, следующий стартует ТОЛЬКО после onComplete предыдущего.
Никаких параллельных запусков здесь нет. */
```


**Поэтому eager-эффекты В ТЕЛЕ МЕТОДА опасны:**

```java
public Mono<Doc> createRefundDocument(...) {
    log.info("CALL");          // ← сработает ПРИ СБОРКЕ ЦЕПОЧКИ
    return db.insert(...);     // ← это лениво (R2DBC)
}
```

`log.info` выполнится до проверки баланса. А вот `db.insert` — только при подписке внутри `then`.

***

**Коротко:**
`then` откладывает **подписку**, не **вызов метода-фабрики**.

---

## `flatMap` vs `concatMap` на `Flux` — разница в конкурентности

### Суть в одной фразе

| Оператор | Порядок запуска внутренних потоков | Порядок результатов |
| :-- | :-- | :-- |
| `flatMap` | **Параллельно** (merge) — подписывается на все сразу | Как придут (может перемешаться) |
| `concatMap` | **Последовательно** (concat) — следующий после `onComplete` предыдущего | Строго по порядку исходного Flux |


***

### Реальный пример: обработка платежей

```java
// ИСХОДНИК: Flux<PaymentRequest> — 1000 запросов на списание
Flux<PaymentRequest> requests = paymentRepository.findPending();

// ┌─────────────────────────────────────────────────────────────┐
// │  flatMap — МАКСИМАЛЬНАЯ ПРОИЗВОДИТЕЛЬНОСТЬ                  │
// │  Все 1000 запросов летят в банк одновременно                │
// └─────────────────────────────────────────────────────────────┘
requests
    .flatMap(req -> paymentGateway.charge(req))  // Merge: все сразу
    .subscribe(result -> ...);  // Результаты приходят хаотично

// ┌─────────────────────────────────────────────────────────────┐
// │  concatMap — СТРОГИЙ ПОРЯДОК (например, лимиты API)         │
// │  Запрос 2 стартует ТОЛЬКО после завершения запроса 1        │
// └─────────────────────────────────────────────────────────────┘
requests
    .concatMap(req -> paymentGateway.charge(req))  // Concat: по очереди
    .subscribe(result -> ...);  // Результаты в порядке запросов
```


***

### Когда что использовать

| Ситуация | Выбор | Почему |
| :-- | :-- | :-- |
| Независимые запросы, нужна скорость | `flatMap` | Параллелизм из коробки |
| Внешний API с rate limit (10 req/sec) | `concatMap` | Не превысим лимит |
| Записи в БД с автоинкрементом / конфликтами | `concatMap` | Избегаем deadlock'ов |
| Обработка событий по агрегату (DDD) | `concatMap` | Гарантия порядка для одного aggregateId |


* DDD (Domain-Driven Design) — подход к разработке, где код строится вокруг бизнес-домена, а не технических слоёв.


> **Агрегат** = корневая сущность + связанные объекты, которые **всегда меняются вместе** и имеют **единый идентификатор**.

Примеры:

- `Order` = `Order` + `OrderLine[]` + `Payment[]` — всё меняется только через `Order`
- `Account` = `Account` + `Transaction[]` — баланс меняется только через `Account.withdraw()`

**Правило:** внешний код не лезет во внутренности агрегата — только через корень.

***

### Продвинутый вариант: `flatMap` с контролем параллелизма

```java
// До 10 одновременных запросов — баланс между скоростью и лимитами
requests
    .flatMap(req -> paymentGateway.charge(req), 10)  // concurrency = 10
    .subscribe(...);
```


***

### Пример с `billing` — правильный паттерн

```java
public Flux<BillingCommandResult> applyCommands(String accountId) {
    
    return billingCommandRepository.findForAccount(accountId)
        .concatMap(command ->  // ПОРЯДОК КОМАНД ВАЖЕН
            billingApi.applyCommand(command)           // 1. Применить
                .then(billingCommandRepository.markApplied(command.id()))  // 2. Пометить
                .thenReturn(new BillingCommandResult(command.id(), "APPLIED"))
        );
}
```

**Почему `concatMap` снаружи + `then` внутри:**

- `concatMap` — команды для **одного аккаунта** идут строго по порядку

- `then` — внутри одной команды: 
  - `markApplied` **только после** успешного `applyCommand`

***

### Если нужно параллелить по разным аккаунтам, но сериализовать внутри одного

```java
// Группируем по accountId → внутри группы concatMap, группы идут параллельно
Flux<BillingCommand> allCommands = billingCommandRepository.findAllPending();

allCommands
    .groupBy(BillingCommand::getAccountId)          // Flux<GroupedFlux<String, Command>>
    .flatMap(group -> 
        group.concatMap(cmd -> 
            billingApi.applyCommand(cmd)
                .then(repo.markApplied(cmd.getId()))
                .thenReturn(new Result(cmd.getId(), "OK"))
        )
    )
    .subscribe(...);
```


***

### Шпаргалка

```java
flux.flatMap(fn)           // merge — параллельно, порядок не гарантирован
flux.flatMap(fn, 10)       // merge с лимитом 10 — контролируемая параллельность
flux.concatMap(fn)         // concat — строго последовательно, порядок сохранён
flux.concatMap(fn).subscribeOn(Schedulers.boundedElastic()) // если fn блокирующий
```

**Правило:** по умолчанию `flatMap`. Переходи на `concatMap` только когда порядок или изоляция критичны.



---

## Практическая памятка

- `Flux.concat(a, b)` / `a.concatWith(b)` — есть два готовых потока элементов; нужно объединить их в один, сохраняя все значения обоих; при ошибке в первом — второй не подписывается.
- `concatMap(mapper)` — есть Flux элементов; для каждого нужно динамически создать publisher, причём следующий внутренний publisher должен начаться после завершения предыдущего.
- `then(next)` — нужен следующий асинхронный шаг после успешного завершения текущего, значение текущего шага не нужно; ведёт себя как concat по признаку ошибки, но применяется к одной цепочке, а не к объединению элементов.
- `flatMap(value -> next(value))` — следующий шаг зависит от значения предыдущего Mono, либо нужно условно прервать цепочку по этому значению.
- Внешний `flatMap` на Flux — допускает конкурентную обработку нескольких элементов; не подходит, если порядок бизнес-операций критичен.
- Готовый Mono, переданный аргументом в `then`/`concatWith`, вычисляется в момент построения цепочки (eager evaluation аргумента в Java) — если само построение содержит побочный эффект, оборачивайте его в `Mono.defer(...)`.
- **Не путать**: `concatWith` объединяет потоки элементов и не анализирует их содержимое (но одинаково с `then` реагирует на onError); `then`/`flatMap` передают управление между зависимыми шагами внутри одной цепочки и могут прервать её по условию.
