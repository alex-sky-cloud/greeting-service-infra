# Reactor: concat, concatWith, concatMap — глубокий разбор

## Оглавление

- [Три оператора одной строкой](#tldr)
- [concat — статический метод](#concat)
- [concatWith — метод экземпляра](#concatwith)
- [Почему concatWith — синтаксический сахар](#sugar)
- [Как это выглядит под капотом](#under-the-hood)
- [concatMap — трансформация в inner publisher](#concatmap)
- [Итоговая памятка различий](#summary)

<a id="tldr"></a>

## Три оператора одной строкой

`concat` и `concatWith` — это два способа записать одну и ту же операцию: последовательное объединение уже готовых `Publisher`.

`concatMap` — другой оператор: он берёт каждый элемент входного `Flux`, превращает его в `inner publisher` (внутренний publisher), а потом последовательно раскрывает такие inner publisher в один общий поток.

Если сказать совсем коротко:

- `Flux.concat(a, b)` — у меня уже есть готовые `Publisher` `a` и `b`, нужно сначала выполнить `a`, потом `b`.
- `a.concatWith(b)` — то же самое, но в fluent-форме (в цепочке через точку).
- `source.concatMap(item -> operation(item))` — у меня есть поток элементов, и для каждого элемента нужно создать свою асинхронную операцию и выполнить такие операции по очереди.

<a id="concat"></a>

## concat — статический метод

```java
Flux<String> firstLetters = Flux.just("a", "b").delayElements(Duration.ofMillis(300));
Flux<String> secondLetters = Flux.just("c", "d").delayElements(Duration.ofMillis(300));

Flux<String> result = Flux.concat(firstLetters, secondLetters);

result.subscribe(System.out::println);
// Вывод: a, b, c, d
// secondLetters не начнёт эмитить элементы, пока firstLetters не завершится
```

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#concat(org.reactivestreams.Publisher...)

EN:

> "Concatenate all sources provided as a vararg, forwarding elements emitted by the sources downstream."

RU:

> «Последовательно соединяет все источники, переданные как vararg, и передаёт их элементы дальше downstream-подписчику.»

Здесь оба `Publisher` уже существуют заранее. Мы просто говорим Reactor: сначала подпишись на первый поток, потом — на второй.

### Бизнес-пример 1: сначала кэш, потом live-данные

```java
Flux<OrderEvent> cachedEvents = orderEventCacheService.readRecentEvents(orderId);
Flux<OrderEvent> liveEvents = orderEventStreamService.subscribeLiveEvents(orderId);

Flux<OrderEvent> orderTimeline = Flux.concat(cachedEvents, liveEvents);
```

Здесь сначала клиент получает уже накопленные события заказа из кэша, и только после завершения этого источника начинается живая подписка на новые события.

### Бизнес-пример 2: сначала активные, потом архивные записи

```java
Flux<Order> activeOrders = orderRepository.findActiveByCustomerId(customerId);
Flux<Order> archivedOrders = archiveOrderRepository.findArchivedByCustomerId(customerId);

Flux<Order> allOrders = Flux.concat(activeOrders, archivedOrders);
```

Такой вариант удобен, когда важно сначала показать пользователю актуальные данные, а затем уже подгрузить архив.

### Бизнес-пример 3: сначала локальное хранилище, потом удалённое

```java
Flux<DocumentChunk> localChunks = localDocumentStorage.readChunks(documentId);
Flux<DocumentChunk> remoteChunks = backupDocumentStorage.readChunks(documentId);

Flux<DocumentChunk> documentContent = Flux.concat(localChunks, remoteChunks);
```

Это подходит не для параллельного объединения, а именно для строгой последовательности двух уже готовых источников.

### Бизнес-пример 4: сначала подтверждённые, потом отложенные уведомления

```java
Flux<Notification> confirmedNotifications = notificationRepository.findConfirmedForUser(userId);
Flux<Notification> deferredNotifications = deferredNotificationRepository.findDeferredForUser(userId);

Flux<Notification> notificationFeed = Flux.concat(confirmedNotifications, deferredNotifications);
```

Так читающий код сразу видит бизнес-правило: сначала отдать один заранее подготовленный поток, затем второй.

---

```java
public static <T> Flux<T> concat(
        Publisher<? extends Publisher<? extends T>> sources,
        int prefetch
) {
    return from(sources).concatMap(identityFunction(), prefetch);
}
```

- `prefetch` в этом контракте — это не количество чисел внутри `Flux`, а количество вложенных `Publisher`, которые Reactor заранее запрашивает у внешнего `Publisher`.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html

EN:

> `prefetch` - the number of Publishers to prefetch from the outer `Publisher`

RU:

> `prefetch` — количество `Publisher`, которые нужно заранее запросить у внешнего `Publisher`.

Простой пример:

```java
Flux<Flux<Integer>> numberSources = Flux.just(
    Flux.just(1, 2),
    Flux.just(3, 4),
    Flux.just(5, 6)
);

int publisherPrefetch = 2;

Flux<Integer> result = Flux.concat(numberSources, publisherPrefetch);
```

Здесь `numberSources` — это внешний поток, который выдаёт не числа, а другие `Publisher`.

Поэтому `publisherPrefetch = 2` означает: Reactor заранее запросит два inner publisher у внешнего источника и будет держать их готовыми к последовательной обработке.

- Источник: https://github.com/reactor/reactor-core/blob/main/reactor-core/src/main/java/reactor/core/publisher/Flux.java

EN:

> `return from(sources).concatMap(identityFunction(), prefetch);`

RU:

> `concat(sources, prefetch)` внутри построен через `concatMap(identityFunction(), prefetch)`.

### Бизнес-пример с prefetch 1: поток источников заказов

```java
Flux<Flux<Order>> orderSources = Flux.just(
    orderRepository.findVipOrders(),
    orderRepository.findPriorityOrders(),
    orderRepository.findRegularOrders()
);

int publisherPrefetch = 2;

Flux<Order> orders = Flux.concat(orderSources, publisherPrefetch);
```

Здесь внешний `Flux` поставляет внутренние потоки заказов. `publisherPrefetch` показывает, сколько таких внутренних потоков Reactor может заранее запросить у внешнего источника.

### Бизнес-пример с prefetch 2: последовательная обработка партий файлов

```java
Flux<Flux<FileTask>> taskBatches = fileTaskBatchRepository.findTaskBatchesForNode(nodeId);

int publisherPrefetch = 3;

Flux<FileTask> tasks = Flux.concat(taskBatches, publisherPrefetch);
```

Это удобно, когда внешний поток поставляет партии работ, а сами партии нужно раскрывать строго по очереди.

### Бизнес-пример с prefetch 3: поток групп уведомлений

```java
Flux<Flux<NotificationJob>> notificationGroups = notificationPlanner.findPlannedGroups(campaignId);

int publisherPrefetch = 2;

Flux<NotificationJob> jobs = Flux.concat(notificationGroups, publisherPrefetch);
```

Здесь `prefetch` не про количество `NotificationJob`, а про количество заранее запрошенных групп-источников.

<a id="concatwith"></a>

## concatWith — метод экземпляра

```java
Flux<String> firstLetters = Flux.just("a", "b").delayElements(Duration.ofMillis(300));
Flux<String> secondLetters = Flux.just("c", "d").delayElements(Duration.ofMillis(300));

Flux<String> result = firstLetters.concatWith(secondLetters);

result.subscribe(System.out::println);
// Вывод тот же: a, b, c, d
```

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#concatWith(org.reactivestreams.Publisher)

EN:

> "Concatenate emissions of this Flux with the provided Publisher (no interleave)."

RU:

> «Последовательно соединяет элементы текущего Flux с переданным Publisher без перемешивания.»

По поведению это тот же `concat`: сначала полностью отрабатывает левый поток, потом начинается правый.

Разница только в форме записи:

- `Flux.concat(a, b)` — статический вызов;
- `a.concatWith(b)` — вызов через экземпляр.

### Бизнес-пример 1: сначала данные из БД, потом догрузка из API

```java
Flux<Product> databaseProducts = productRepository.findPublishedProducts(categoryId);
Flux<Product> externalCatalogProducts = productCatalogClient.fetchAdditionalProducts(categoryId);

Flux<Product> productFeed = databaseProducts.concatWith(externalCatalogProducts);
```

Когда код уже строится цепочкой слева направо, `concatWith` читается естественнее.

### Бизнес-пример 2: сначала ошибки текущего часа, потом архивные

```java
Flux<ErrorLog> currentHourErrors = logRepository.findErrorsForCurrentHour(serviceName);
Flux<ErrorLog> archivedErrors = archiveLogRepository.findOlderErrors(serviceName);

Flux<ErrorLog> allErrors = currentHourErrors.concatWith(archivedErrors);
```

Это такой же сценарий, как у `concat`: готовый левый поток плюс готовый правый поток.

### Бизнес-пример 3: сначала локальные сообщения, потом резервный брокер

```java
Flux<MessageEnvelope> localBrokerMessages = localBrokerClient.consume(topicName);
Flux<MessageEnvelope> backupBrokerMessages = backupBrokerClient.consume(topicName);

Flux<MessageEnvelope> messages = localBrokerMessages.concatWith(backupBrokerMessages);
```

Если важно явно показать: «сначала один источник, потом другой», `concatWith` подходит хорошо.

### Бизнес-пример 4: сначала основная цепочка, потом хвост из аудита

```java
Flux<PaymentAuditEvent> mainAuditEvents = auditRepository.findMainEvents(paymentId)
    .filter(PaymentAuditEvent::isVisibleForSupport);

Flux<PaymentAuditEvent> trailingAuditEvents = auditRepository.findTrailingTechnicalEvents(paymentId);

Flux<PaymentAuditEvent> auditTimeline = mainAuditEvents.concatWith(trailingAuditEvents);
```

Здесь fluent-форма особенно читаема, потому что левая цепочка уже собрана до вызова `concatWith`.

<a id="sugar"></a>

## Почему concatWith — синтаксический сахар

Разница между `concat` и `concatWith` не в бизнес-смысле и не в порядке выполнения. Это одна и та же последовательная конкатенация, просто записанная по-разному.

```java
Flux<Order> pendingOrders = orderRepository.findPendingOrders()
    .filter(order -> order.getAmount().compareTo(BigDecimal.valueOf(100)) > 0)
    .map(this::enrichOrder);

Flux<Order> archivedOrders = orderRepository.findArchivedOrders();

Flux<Order> result1 = Flux.concat(pendingOrders, archivedOrders);

Flux<Order> result2 = orderRepository.findPendingOrders()
    .filter(order -> order.getAmount().compareTo(BigDecimal.valueOf(100)) > 0)
    .map(this::enrichOrder)
    .concatWith(orderRepository.findArchivedOrders());
```

В обоих случаях смысл одинаковый: сначала выполняется левая часть, затем к ней последовательно добавляется ещё один уже готовый `Publisher`.

### Бизнес-пример 1: почему fluent-форма бывает удобнее

```java
Flux<CustomerInvoice> invoices = invoiceRepository.findOpenInvoices(customerId)
    .filter(CustomerInvoice::isPayable)
    .map(this::attachCurrency)
    .concatWith(invoiceArchiveRepository.findRecentlyClosedInvoices(customerId));
```

Если левая часть уже выражена как цепочка операторов, дописать `.concatWith(...)` проще и читаемее, чем выносить всю цепочку в аргумент `Flux.concat(...)`.

### Бизнес-пример 2: тот же смысл в статической форме

```java
Flux<CustomerInvoice> openInvoices = invoiceRepository.findOpenInvoices(customerId)
    .filter(CustomerInvoice::isPayable)
    .map(this::attachCurrency);

Flux<CustomerInvoice> recentlyClosedInvoices = invoiceArchiveRepository.findRecentlyClosedInvoices(customerId);

Flux<CustomerInvoice> invoices = Flux.concat(openInvoices, recentlyClosedInvoices);
```

Этот вариант полезен, когда оба потока уже объявлены переменными и их удобно передать как готовые аргументы.

### Бизнес-пример 3: выбор формы зависит от читаемости

```java
Flux<ShipmentEvent> shipmentEvents = shipmentRepository.findEvents(shipmentId)
    .map(this::enrichShipmentEvent)
    .concatWith(shipmentArchiveRepository.findOlderEvents(shipmentId));
```

Здесь нет новой семантики. Есть только более удобная запись той же операции.

<a id="under-the-hood"></a>

## Как это выглядит под капотом

Чтобы понять природу `concatWith`, полезно смотреть на него как на обычный метод экземпляра, который в итоге приводит к той же идее: слева текущий `Flux`, справа ещё один `Publisher`, а результат — новый `Flux` последовательной конкатенации.

```java
Flux<String> left = Flux.just("A", "B");
Flux<String> right = Flux.just("C", "D");

Flux<String> result1 = Flux.concat(left, right);
Flux<String> result2 = left.concatWith(right);
```

С точки зрения результата это одно и то же: сначала подписка идёт на `left`, после его завершения — на `right`.

Если развернуть fluent-цепочку по шагам, картина становится понятнее:

```java
Flux<Order> step1 = repository.findActiveOrders();
Flux<Order> step2 = step1.filter(Order::isValid);
Flux<Order> step3 = step2.map(this::enrich);
Flux<Order> step4 = step3.concatWith(repository.findArchivedOrders());
Flux<Order> step5 = step4.doOnNext(this::log);
```

Это важно понимать так:

1. Каждый оператор не исполняет поток немедленно, а строит новый `Flux`.
2. `concatWith` берёт уже собранную слева цепочку как левый источник.
3. Переданный `Publisher` становится правым источником.
4. При подписке сначала отрабатывает левый источник.
5. Только после `onComplete` левого источника начинается правый.

Под капотом `concatWith` сводится к той же базовой операции конкатенации:

```java
public final Flux<T> concatWith(Publisher<? extends T> other) {
    if (this instanceof FluxConcatArray) {
        FluxConcatArray<T> fluxConcatArray = (FluxConcatArray<T>) this;
        return fluxConcatArray.concatAdditionalSourceLast(other);
    }
    return concat(this, other);
}
```

- Источник: https://github.com/reactor/reactor-core/blob/main/reactor-core/src/main/java/reactor/core/publisher/Flux.java

EN:

> `return concat(this, other);`

RU:

> В обычном случае `concatWith(other)` просто строится как `concat(this, other)`.

### Бизнес-разбор под капотом

```java
Flux<Order> visibleOrders = orderRepository.findActiveOrders()
    .filter(Order::isVisibleForManager)
    .map(this::attachCustomerSegment);

Flux<Order> archivedOrders = archiveOrderRepository.findArchivedOrders();

Flux<Order> result = visibleOrders.concatWith(archivedOrders);
```

Здесь `visibleOrders` уже не «сырой запрос в БД», а цепочка из нескольких операторов. `concatWith` не ломает её и не выполняет отдельно каждую стадию. Он просто строит новый `Flux`, который при подписке сначала целиком выполнит левую цепочку, а затем подпишется на `archivedOrders`.

### Ещё один бизнес-разбор

```java
Flux<AccountEvent> primaryEvents = accountEventRepository.findPrimaryEvents(accountId)
    .map(this::maskSensitiveFields)
    .filter(AccountEvent::isAllowedForSupport);

Flux<AccountEvent> backupEvents = backupEventRepository.findBackupEvents(accountId);

Flux<AccountEvent> accountHistory = primaryEvents.concatWith(backupEvents);
```

Мысленно это можно читать так: «возьми уже собранный слева pipeline (пайплайн) и добавь к нему ещё один готовый поток справа».

<a id="concatmap"></a>

## concatMap — трансформация в inner publisher

```java
Flux<Integer> source = Flux.just(1, 2, 3);

Flux<Integer> result = source.concatMap(value ->
    Mono.just(value * 10)
        .delayElement(Duration.ofMillis(100))
);

result.subscribe(System.out::println);
// Вывод: 10, 20, 30
// второй inner publisher не начнёт эмитить данные, пока первый не завершится
```

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#concatMap(java.util.function.Function)

EN:

> "Transform the elements emitted by this Flux asynchronously into Publishers, then flatten these inner publishers into a single Flux, sequentially and preserving order using concatenation."

RU:

> «Преобразует элементы этого Flux асинхронно в Publisher, затем разворачивает эти inner publisher в один Flux последовательно и с сохранением порядка через конкатенацию.»

Это уже не склейка двух готовых потоков. Здесь для каждого входного элемента создаётся своя асинхронная операция.

То есть логика такая:

- пришёл элемент `1`;
- mapper (маппер) создал для него inner publisher;
- Reactor дождался завершения этого inner publisher;
- только потом взял следующий элемент `2`.

### Что значит `item -> operation(item)`

Вот запись:

```java
source.concatMap(item -> operation(item))
```

нужно читать так:

- `item` — очередной элемент из внешнего `Flux`;
- `operation(item)` — асинхронная операция именно для этого элемента;
- результат `operation(item)` — это `Publisher`, обычно `Mono<T>` или `Flux<T>`.

Например:

```java
Flux<Long> userIds = Flux.just(101L, 102L, 103L);

Flux<User> users = userIds.concatMap(userId -> userRepository.findById(userId));
```

Здесь для каждого `userId` создаётся отдельный `Mono<User>`, и такие запросы выполняются строго по очереди.

### Бизнес-пример 1: последовательная обработка платёжных заявок

```java
Flux<PaymentRequest> paymentRequests = paymentRequestRepository.findPendingRequests();

Flux<PaymentResult> paymentResults = paymentRequests.concatMap(paymentRequest ->
    paymentGateway.charge(paymentRequest)
        .flatMap(chargeResponse ->
            paymentLedgerService.recordSuccessfulCharge(paymentRequest, chargeResponse)
                .thenReturn(new PaymentResult(paymentRequest.id(), chargeResponse.transactionId(), "CHARGED"))
        )
);
```

Здесь `concatMap` нужен на внешнем уровне: следующая заявка на списание начнёт обрабатываться только после завершения текущей. Это полезно, когда порядок и изоляция важнее максимальной скорости.

### Бизнес-пример 2: события одного агрегата по очереди

```java
Flux<DomainEvent> incomingEvents = eventInboxRepository.findEventsForAggregate(aggregateId);

Flux<ProcessingResult> processingResults = incomingEvents.concatMap(event ->
    aggregateStateRepository.loadState(aggregateId)
        .flatMap(currentState -> domainService.applyEvent(currentState, event))
        .flatMap(updatedState -> aggregateStateRepository.saveState(aggregateId, updatedState))
        .thenReturn(new ProcessingResult(event.id(), "APPLIED"))
);
```

Это классический случай: если поменять `concatMap` на `flatMap`, события одного и того же агрегата могут начать обрабатываться конкурентно и сломать порядок бизнес-изменений.

### Бизнес-пример 3: последовательная обработка файлов

```java
Flux<FileImportTask> importTasks = fileImportTaskRepository.findScheduledTasks();

Flux<FileImportResult> importResults = importTasks.concatMap(importTask ->
    fileStorageClient.download(importTask.fileId())
        .flatMap(fileContent -> fileParser.parse(fileContent, importTask.format()))
        .flatMap(parsedRows -> importService.saveRows(importTask.batchId(), parsedRows))
        .thenReturn(new FileImportResult(importTask.fileId(), "IMPORTED"))
);
```

Здесь каждый элемент внешнего потока порождает свою цепочку асинхронных шагов. `concatMap` гарантирует, что задачи пойдут одна за другой.

### Бизнес-пример 4: генерация отчёта по пользователям

```java
Flux<UUID> userIds = reportService.findUserIdsForMonthlyReport();

Flux<UserReportRow> reportRows = userIds.concatMap(userId ->
    userRepository.findById(userId)
        .zipWith(orderRepository.findPaidByUserId(userId).collectList())
        .map(tuple -> new UserReportRow(
            tuple.getT1().getId(),
            tuple.getT1().getEmail(),
            tuple.getT2().size()
        ))
);
```

Здесь снаружи стоит `concatMap`, потому что строки отчёта по пользователям нужно строить по порядку. Но внутри одного пользователя используется `zipWith`, потому что данные пользователя и список заказов можно получать как единый набор результатов.

## Где здесь `zip`, а где `concatMap`

Это один из самых частых вопросов.

`concatMap` и `zip` — не конкуренты и не взаимозаменяемые операторы. Они решают разные задачи.

- `concatMap` отвечает за то, как обрабатываются элементы внешнего потока.
- `zip` отвечает за то, как собрать результаты нескольких источников в один общий результат.

### Когда нужен `zip`

```java
Mono<User> userMono = userRepository.findById(userId);
Mono<List<Order>> ordersMono = orderRepository.findPaidByUserId(userId).collectList();
Mono<LoyaltyProfile> loyaltyMono = loyaltyClient.getProfile(userId);

Mono<UserDashboardDto> dashboard = Mono.zip(userMono, ordersMono, loyaltyMono)
    .map(tuple -> new UserDashboardDto(
        tuple.getT1(),
        tuple.getT2(),
        tuple.getT3()
    ));
```

Здесь три операции независимы. Нам не нужно, чтобы одна завершилась перед стартом другой. Нам нужно дождаться результатов всех трёх и собрать один DTO.

В таком сценарии заменять `zip` на `concatMap` не нужно. Это будет хуже выражать смысл задачи.

### Когда `zip` не подходит вместо последовательной цепочки

```java
Mono<RefundResult> refundResult = paymentService.refund(paymentId);
Mono<Void> inventoryReturn = inventoryService.returnReservedItems(orderId);
Mono<RefundDocument> refundDocument = accountingService.createRefundDocument(orderId);
```

Если бизнес-правило такое:

1. сначала вернуть деньги;
2. потом вернуть товар на склад;
3. потом создать документ возврата;

то `Mono.zip(refundResult, inventoryReturn, refundDocument)` не выражает нужную зависимость шагов. Такой код подходит только тогда, когда операции независимы друг от друга и могут выполняться параллельно.

Для последовательного сценария нужен другой стиль:

```java
Mono<RefundWorkflowResult> result = paymentService.refund(paymentId)
    .flatMap(refund ->
        inventoryService.returnReservedItems(orderId)
            .then(accountingService.createRefundDocument(orderId, refund.refundId()))
            .map(document -> new RefundWorkflowResult(orderId, refund.refundId(), document.documentId()))
    );
```

Здесь уже важна именно последовательность шагов, а не просто сбор результатов.

## Почему внутри `concatMap` иногда нужен `then`

`concatMap` сериализует обработку разных элементов внешнего `Flux`.

Но он не делает автоматически последовательными все шаги внутри одной inner-цепочки. Если внутри обработки одного элемента есть зависимость «сначала A, потом B», эту зависимость нужно выразить отдельно — через `then`, `flatMap`, иногда `zip`, в зависимости от смысла.

Пример:

```java
Flux<ReturnRequest> returnRequests = returnRequestRepository.findPendingReturns();

Flux<ReturnResult> results = returnRequests.concatMap(returnRequest ->
    paymentService.refund(returnRequest.paymentId())
        .flatMap(refundResponse ->
            inventoryService.returnReservedItems(returnRequest.orderId())
                .then(accountingService.createRefundDocument(
                    returnRequest.orderId(),
                    refundResponse.refundId()
                ))
                .map(document -> new ReturnResult(
                    returnRequest.orderId(),
                    refundResponse.refundId(),
                    document.documentId()
                ))
        )
);
```

Здесь:

- внешний `concatMap` гарантирует: заявки на возврат пойдут по очереди;
- `flatMap` нужен, чтобы использовать `refundResponse` дальше;
- `then(...)` нужен, потому что создание документа должно начаться только после успешного завершения возврата товара на склад.

То есть `concatMap` не заменяет собой всю внутреннюю бизнес-оркестрацию. Он отвечает только за последовательность между внешними элементами.

## Чем `concatMap` отличается от `flatMap`

`flatMap` тоже превращает каждый элемент во внутренний `Publisher`, но объединяет такие inner publisher через merge (слияние), поэтому несколько inner-операций могут быть активны одновременно.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#flatMap(java.util.function.Function)

EN:

> "Transform the elements emitted by this Flux asynchronously into Publishers, then flatten these inner publishers into a single Flux through merging, which allow them to interleave."

RU:

> «Преобразует элементы этого Flux в Publisher и затем объединяет их через merging, из-за чего их элементы могут перемешиваться.»

Простой пример:

```java
Flux<Integer> source = Flux.just(1, 2, 3);

Flux<Integer> result = source.flatMap(value ->
    Mono.just(value * 10)
        .delayElement(Duration.ofMillis((4 - value) * 100L))
);

result.subscribe(System.out::println);
// Возможный вывод: 30, 20, 10
```

Здесь внутренние операции стартуют без ожидания завершения предыдущей, поэтому результаты могут прийти в другом порядке.

### Бизнес-пример 1: когда `flatMap` лучше

```java
Flux<UUID> userIds = marketingSegmentService.findAudience(segmentId);

Flux<EmailSendResult> results = userIds.flatMap(userId ->
    emailTemplateService.buildOfferEmail(userId)
        .flatMap(emailClient::send)
);
```

Если письма независимы друг от друга и цель — пропускная способность, `flatMap` обычно лучше `concatMap`.

### Бизнес-пример 2: когда `concatMap` лучше

```java
Flux<AccountCommand> accountCommands = accountCommandRepository.findPendingCommands(accountId);

Flux<CommandResult> results = accountCommands.concatMap(command ->
    accountService.applyCommand(command)
        .flatMap(appliedState -> accountProjectionRepository.save(accountId, appliedState))
        .thenReturn(new CommandResult(command.id(), "APPLIED"))
);
```

Если команды одного аккаунта должны применяться строго по порядку, `flatMap` здесь опасен, а `concatMap` уместен.

## Где нужен `flatMapSequential`

`flatMapSequential` — это компромисс между `flatMap` и `concatMap`.

- Он может запускать несколько inner-операций конкурентно.
- Но наружу результаты выдаёт в порядке исходных элементов.

То есть это выбор для сценария: «хочу ускорить I/O, но итоговый порядок должен остаться исходным».

Пример без магических чисел:

```java
Flux<UUID> userIds = reportService.findUserIdsForMonthlyReport();

int maxConcurrentUserRequests = 16;
// Максимум 16 пользовательских запросов могут быть активны одновременно.

int innerResultBufferSize = 32;
// До 32 результатов inner publisher Reactor может буферизовать,
// пока ждёт завершения более ранних userId для сохранения порядка.

Flux<UserOrdersDto> userOrders = userIds.flatMapSequential(
    userId -> orderRepository.findPaidByUserId(userId)
        .collectList()
        .map(orders -> new UserOrdersDto(userId, orders)),
    maxConcurrentUserRequests,
    innerResultBufferSize
);
```

Этот код читается так:

- `userIds` приходит потоком;
- для каждого `userId` создаётся свой запрос в репозиторий;
- одновременно можно держать до `maxConcurrentUserRequests` активных inner-операций;
- но наружу `UserOrdersDto` будут выдаваться в том же порядке, в каком пришли `userId`.

### Бизнес-пример 1: отчёт по пользователям

```java
Flux<UUID> userIds = reportService.findUserIdsForMonthlyReport();

int maxConcurrentUserRequests = 8;
int innerResultBufferSize = 16;

Flux<UserBillingSummaryDto> summaries = userIds.flatMapSequential(
    userId -> Mono.zip(
            userRepository.findById(userId),
            billingRepository.findInvoicesByUserId(userId).collectList(),
            paymentRepository.findLastPaymentByUserId(userId)
        )
        .map(tuple -> new UserBillingSummaryDto(
            tuple.getT1().getId(),
            tuple.getT1().getEmail(),
            tuple.getT2(),
            tuple.getT3()
        )),
    maxConcurrentUserRequests,
    innerResultBufferSize
);
```

Здесь inner-операции могут стартовать конкурентно, но итоговый отчёт останется упорядоченным по входному списку пользователей.

### Бизнес-пример 2: догрузка карточек товаров

```java
Flux<Long> productIds = recommendationService.findRecommendedProductIds(userId);

int maxConcurrentProductRequests = 10;
int innerResultBufferSize = 20;

Flux<ProductCardDto> productCards = productIds.flatMapSequential(
    productId -> Mono.zip(
            productRepository.findById(productId),
            pricingClient.getActualPrice(productId),
            stockClient.getAvailableStock(productId)
        )
        .map(tuple -> new ProductCardDto(
            tuple.getT1(),
            tuple.getT2(),
            tuple.getT3()
        )),
    maxConcurrentProductRequests,
    innerResultBufferSize
);
```

Это удобно, когда UI ожидает карточки в исходном порядке рекомендаций, но запросы хочется выполнять параллельно.

### Бизнес-пример 3: выгрузка заказов для экспорта

```java
Flux<Long> orderIds = exportService.findOrderIdsForDailyExport();

int maxConcurrentOrderRequests = 6;
int innerResultBufferSize = 12;

Flux<ExportOrderRow> exportRows = orderIds.flatMapSequential(
    orderId -> Mono.zip(
            orderRepository.findById(orderId),
            shipmentRepository.findByOrderId(orderId),
            paymentRepository.findByOrderId(orderId)
        )
        .map(tuple -> new ExportOrderRow(
            tuple.getT1(),
            tuple.getT2(),
            tuple.getT3()
        )),
    maxConcurrentOrderRequests,
    innerResultBufferSize
);
```

Снаружи это выглядит как упорядоченная выгрузка, а внутри даёт более высокую скорость, чем чистый `concatMap`.

## Что означает преобразование в `Publisher`

У обычного `map` функция возвращает обычное значение:

```java
Flux<Integer> numbers = Flux.just(1, 2, 3);
Flux<Integer> multiplied = numbers.map(value -> value * 10);
```

Здесь преобразование такое:

```java
Integer -> Integer
```

У `concatMap` и `flatMap` функция возвращает новый реактивный источник:

```java
Flux<Integer> multipliedAsync = Flux.just(1, 2, 3)
    .flatMap(value -> Mono.just(value * 10));
```

Здесь сигнатура уже такая:

```java
Integer -> Publisher<Integer>
```

В реальном коде это обычно не `Mono.just(...)`, а асинхронный вызов:

```java
userId -> userRepository.findById(userId)
orderId -> paymentRepository.findByOrderId(orderId)
fileId -> fileStorageClient.download(fileId)
```

То есть `Publisher` здесь — это не «обёртка ради формы», а описание асинхронной операции, которая позже даст результат.

## Когда и что использовать

- `Flux.concat(a, b)` — когда несколько `Publisher` уже существуют заранее.
- `a.concatWith(b)` — когда нужно дописать ещё один готовый `Publisher` к уже собранной слева цепочке.
- `source.concatMap(item -> operation(item))` — когда для каждого элемента входного потока нужно создать свою асинхронную операцию и выполнить такие операции строго по очереди.
- `source.flatMap(item -> operation(item))` — когда операции независимы и важнее пропускная способность, чем порядок.
- `source.flatMapSequential(item -> operation(item))` — когда можно стартовать конкурентно, но выдавать результат нужно в исходном порядке.
- `Mono.zip(...)` / `Flux.zip(...)` — когда есть несколько независимых источников, и нужно дождаться результата от каждого, а затем собрать их в один общий объект.

<a id="summary"></a>

## Итоговая памятка различий

| Оператор | Когда использовать | Что делает |
| :-- | :-- | :-- |
| `Flux.concat(a, b)` | Есть несколько готовых `Publisher` | Полностью выполняет `a`, затем подписывается на `b` |
| `a.concatWith(b)` | Есть готовая левая цепочка и готовый правый `Publisher` | Делает то же, что `concat`, но в fluent-форме |
| `source.concatMap(item -> operation(item))` | Для каждого элемента нужно создать свою асинхронную операцию; важен порядок | Создаёт inner publisher для каждого элемента и выполняет их строго последовательно |
| `source.flatMap(item -> operation(item))` | Элементы независимы; нужна скорость | Запускает несколько inner-операций конкурентно и merge-ит результаты |
| `source.flatMapSequential(item -> operation(item))` | Нужна конкурентность, но итоговый порядок должен сохраниться | Запускает inner-операции конкурентно, но наружу выдаёт результаты в исходном порядке |
| `Mono.zip(a, b, c)` | Есть несколько независимых операций, и нужен общий DTO | Ждёт результат от каждого источника и затем собирает их в один объект |

Коротко запомнить можно так:

- `concat` / `concatWith` — склеить уже готовые потоки;
- `concatMap` — для каждого элемента создать свою асинхронную работу и выполнить такие работы по очереди;
- `flatMap` — для каждого элемента создать свою асинхронную работу и выполнить такие работы конкурентно;
- `flatMapSequential` — стартовать конкурентно, но отдавать результат по порядку;
- `zip` — не про порядок элементов внешнего `Flux`, а про сбор нескольких независимых результатов в один.
