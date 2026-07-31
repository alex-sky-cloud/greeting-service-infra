# Reactor: почему цепочки `map()` и `flatMap()` иногда считают overhead

## Оглавление

- [Что именно считается overhead](#what-is-overhead)
- [Почему SonarLint так пишет](#why-sonarlint)
- [Несколько `map()` подряд](#many-map)
- [Неправильный `flatMap()`](#wrong-flatmap)
- [Практическая памятка](#summary)

<a id="what-is-overhead"></a>

## Что именно считается overhead

Когда анализатор пишет, что цепочка вроде

```java
.map(User::getName)
.map(String::toLowerCase)
.map(String::length)
```

создаёт overhead, он обычно имеет в виду не «код сломан», а то, что для слишком мелкой логики вы строите лишние промежуточные шаги pipeline (пайплайна).

В Reactor каждый оператор (`map`, `filter`, `flatMap` и т.д.) не выполняет данные сразу, а создаёт новый слой обработки над предыдущим `Publisher`.

То есть три `map()` подряд — это не одна операция, а три последовательных operator-узла в цепочке.

- Источник: https://projectreactor.io/docs/core/3.5.16/reference

EN:

> "Operators are intermediaries that can change the events emitted by the upstream Publisher. Internally, they create a chain of operators."

RU:

> «Операторы — это промежуточные звенья, которые могут изменять сигналы upstream Publisher. Внутри они образуют цепочку операторов.»

Из-за этого overhead бывает двух видов:

1. **Runtime overhead** — лишние обёртки, лишние operator-слои, лишние вызовы функций.
2. **Cognitive overhead** — код труднее читать: одна простая мысль раздроблена на много микрошагов.

Обычно второй вид даже важнее первого.

<a id="why-sonarlint"></a>

## Почему SonarLint так пишет

SonarLint не измеряет ваш код профилировщиком во время выполнения. Он работает как static analysis (статический анализатор): видит шаблон кода и предупреждает, что конструкция выглядит избыточной или менее эффективной.

- Источник: https://www.sonarsource.com/products/sonarlint/

SonarLint или похожие анализаторы ругаются на такие цепочки по простой причине:

- несколько `map()` подряд иногда можно свернуть в один `map()`;
- `flatMap(x -> Mono.just(...))` часто означает, что асинхронности на самом деле нет;
- лишние reactive-обёртки ухудшают читаемость;
- код начинает выглядеть так, будто для синхронного преобразования выбрали слишком тяжёлый оператор.

Важно: слово `overhead` здесь не всегда означает «будет заметно медленнее в проде». Чаще это означает: «для такой простой задачи выбран более тяжёлый pipeline, чем нужен».

Есть ещё важный нюанс.

Reactor умеет часть подобных цепочек оптимизировать. В частности, в сообществе Reactor отдельно разбирается идея fusion (слияния операторов), включая случаи, когда несколько соседних synchronous-операторов могут быть сведены к более компактной форме.

- Источник: https://gist.github.com/Lukas-Krickl/50f1daebebaa72c7e944b7c319e3c073

EN:

> "Macro-fusion happens mainly in the assembly-time in the form of replacing two or more subsequent operators with a single operator, thus reducing the subscription-time overhead (and sometimes the runtime overhead...)."

RU:

> «Macro-fusion в основном происходит на этапе assembly-time: два или больше соседних оператора заменяются одним, что уменьшает overhead на этапе подписки, а иногда и во время выполнения.»

Но анализатор всё равно видит исходный код, а не гипотетическую оптимизацию JIT или fusion внутри библиотеки. Поэтому он и пишет про overhead уже на уровне формы записи.

<a id="many-map"></a>

## Несколько `map()` подряд

### Когда это нормально

Если каждый `map()` — это отдельный осмысленный этап бизнес-обработки, такая запись может быть нормальной.

Например:

```java
Flux<Order> paidOrders = orderRepository.findByCustomerId(customerId)
    .filter(Order::isPaid)
    .map(this::attachCurrency)
    .map(this::attachTaxInfo)
    .map(this::maskInternalFields);
```

Здесь ещё можно спорить, нужно ли сливать шаги, потому что каждый `map()` выражает отдельный смысловой этап.

### Когда это выглядит непрофессионально

Если три `map()` вместе образуют одну маленькую синхронную операцию, код выглядит раздробленным.

Плохо:

```java
Flux<Integer> nameLengths = users
    .map(User::getName)
    .map(String::toLowerCase)
    .map(String::length);
```

Почему анализатору это не нравится:

- из одного поля делается одно простое вычисление;
- нет трёх независимых бизнес-стадий;
- цепочка длиннее, чем смысл задачи.

Лучше так:

```java
Flux<Integer> nameLengths = users
    .map(user -> user.getName().toLowerCase().length());
```

Здесь одна мысль = один `map()`.

### Бизнес-кейс 1: нормализация email

Плохо:

```java
Flux<String> normalizedEmails = customerFlux
    .map(Customer::getEmail)
    .map(String::trim)
    .map(String::toLowerCase);
```

Лучше:

```java
Flux<String> normalizedEmails = customerFlux
    .map(customer -> customer.getEmail().trim().toLowerCase());
```

Почему: это одна синхронная операция нормализации, а не три разные бизнес-стадии.

### Бизнес-кейс 2: подготовка SKU

Плохо:

```java
Flux<String> normalizedSkus = productFlux
    .map(Product::getSku)
    .map(String::trim)
    .map(String::toUpperCase);
```

Лучше:

```java
Flux<String> normalizedSkus = productFlux
    .map(product -> product.getSku().trim().toUpperCase());
```

Здесь снова одна простая CPU-операция, поэтому один `map()` читается профессиональнее.

### Бизнес-кейс 3: длина имени файла

Плохо:

```java
Flux<Integer> fileNameLengths = fileTasks
    .map(FileTask::getOriginalFileName)
    .map(String::trim)
    .map(String::length);
```

Лучше:

```java
Flux<Integer> fileNameLengths = fileTasks
    .map(fileTask -> fileTask.getOriginalFileName().trim().length());
```

### Бизнес-кейс 4: когда несколько `map()` оставить можно

```java
Flux<InvoiceView> invoiceViews = invoiceRepository.findOpenInvoices(customerId)
    .map(this::attachExchangeRate)
    .map(this::calculatePenalty)
    .map(this::maskSensitiveFields);
```

Здесь цепочка может быть оправдана, если каждый шаг — отдельная бизнес-стадия и каждое имя метода хорошо объясняет смысл.

То есть проблема не в самом количестве `map()`, а в том, несут ли эти шаги отдельную смысловую нагрузку.

<a id="wrong-flatmap"></a>

## Неправильный `flatMap()`

`flatMap()` нужен тогда, когда функция возвращает `Publisher`, то есть новую асинхронную реактивную операцию.

- Источник: https://eherrera.net/project-reactor-course/03-working-with-map-and-flatmap/flatmap.html

EN:

> "The flatMap operator transforms the elements emitted by a Publisher asynchronously by applying a function that returns the values emitted by inner publishers."

RU:

> «Оператор `flatMap` асинхронно преобразует элементы Publisher, применяя функцию, которая возвращает значения из inner publisher.»

Поэтому вот такой код часто считают лишним overhead:

```java
Flux<Integer> nameLengths = users.flatMap(user ->
    Mono.just(user.getName().toLowerCase().length())
);
```

Почему это плохо:

- вычисление синхронное;
- нового реального async-шагa нет;
- `Mono.just(...)` здесь просто создаёт лишнюю reactive-обёртку;
- `map()` выразил бы смысл точнее и проще.

Правильно:

```java
Flux<Integer> nameLengths = users
    .map(user -> user.getName().toLowerCase().length());
```

### Бизнес-кейс 1: расчёт суммы заказа

Плохо:

```java
Flux<OrderTotalDto> totals = orders.flatMap(order ->
    Mono.just(new OrderTotalDto(order.getId(), pricingService.calculateTotal(order)))
);
```

Если `calculateTotal(order)` — обычный синхронный расчёт в памяти, `flatMap` здесь не нужен.

Лучше:

```java
Flux<OrderTotalDto> totals = orders.map(order ->
    new OrderTotalDto(order.getId(), pricingService.calculateTotal(order))
);
```

### Бизнес-кейс 2: формирование display name

Плохо:

```java
Flux<String> displayNames = users.flatMap(user ->
    Mono.just(user.getLastName() + " " + user.getFirstName())
);
```

Лучше:

```java
Flux<String> displayNames = users.map(user ->
    user.getLastName() + " " + user.getFirstName()
);
```

### Бизнес-кейс 3: подготовка DTO

Плохо:

```java
Flux<ProductCardDto> productCards = products.flatMap(product ->
    Mono.just(new ProductCardDto(product.getId(), product.getName(), product.getPrice()))
);
```

Лучше:

```java
Flux<ProductCardDto> productCards = products.map(product ->
    new ProductCardDto(product.getId(), product.getName(), product.getPrice())
);
```

### Бизнес-кейс 4: когда `flatMap()` уже нужен по-настоящему

```java
Flux<ProductCardDto> productCards = productIds.flatMap(productId ->
    Mono.zip(
            productRepository.findById(productId),
            pricingClient.getActualPrice(productId),
            stockClient.getAvailableStock(productId)
    )
    .map(tuple -> new ProductCardDto(
        tuple.getT1().getId(),
        tuple.getT1().getName(),
        tuple.getT2(),
        tuple.getT3()
    ))
);
```

Здесь `flatMap()` уже корректен, потому что для каждого `productId` действительно создаётся новая асинхронная цепочка с вызовами БД и внешних сервисов.

### Отдельный важный случай: блокирующая операция

Иногда разработчик пишет так:

```java
Flux<PaymentStatus> statuses = paymentIds.flatMap(paymentId ->
    Mono.just(paymentGatewayClient.blockingGetStatus(paymentId))
);
```

Это плохо уже по другой причине: здесь не просто лишний `flatMap`, а ещё и блокирующий вызов замаскирован под реактивный код.

В таких случаях обычно нужен не `Mono.just(...)`, а `Mono.fromCallable(...)` с выносом на подходящий scheduler.

- Источник: https://stackoverflow.com/questions/78253412/project-reactor-mono-just-inside-a-flatmap

EN:

> "You could use `Mono.fromCallable()` together with `.subscribeOn()` to move the blocking call to a different thread..."

RU:

> «Можно использовать `Mono.fromCallable()` вместе с `.subscribeOn()`, чтобы перенести блокирующий вызов в другой поток.»

Пример:

```java
Flux<PaymentStatus> statuses = paymentIds.flatMap(paymentId ->
    Mono.fromCallable(() -> paymentGatewayClient.blockingGetStatus(paymentId))
        .subscribeOn(Schedulers.boundedElastic())
);
```

Здесь `flatMap()` уже оправдан, потому что он связывает внешний поток `paymentIds` с отдельной асинхронной `Mono`-операцией для каждого `paymentId`.

<a id="summary"></a>

## Практическая памятка

- Несколько `map()` подряд — это не автоматически ошибка, но часто признак раздробленного синхронного преобразования.
- Если это одна простая мысль, чаще лучше один `map()`.
- `flatMap()` нужен для `T -> Publisher<R>`.
- `flatMap(x -> Mono.just(...))` для обычной синхронной логики чаще всего выглядит как лишний overhead.
- Когда анализатор пишет `overhead`, он обычно имеет в виду: «для этой задачи выбран более тяжёлый и менее читаемый pipeline, чем нужно».
- Реальная производительность зависит от контекста, JIT и поведения всей цепочки, но замечание анализатора полезно как сигнал: проверь, не используешь ли ты слишком тяжёлую форму там, где хватило бы более простой.
