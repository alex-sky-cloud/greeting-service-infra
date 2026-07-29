# Reactor: concat, concatWith, concatMap — глубокий разбор

## Оглавление

- [Три оператора одной строкой](#tldr)
- [concat — статический метод](#concat)
- [concatWith — метод экземпляра](#concatwith)
- [Почему concatWith — синтаксический сахар](#sugar)
- [Как это выглядит под капотом](#under-the-hood)
- [concatMap — трансформация в inner publisher](#concatmap)
- [Итоговая таблица различий](#summary)

<a id="tldr"></a>

## Три оператора одной строкой

`concat` и `concatWith` — это два способа записать одну и ту же операцию последовательного объединения уже готовых `Publisher`; `concatMap` — другой оператор: он превращает каждый элемент в `inner publisher`, а затем последовательно их объединяет.

<a id="concat"></a>

## concat — статический метод

```java
Flux<String> flux1 = Flux.just("a", "b").delayElements(Duration.ofMillis(500));
Flux<String> flux2 = Flux.just("c", "d").delayElements(Duration.ofMillis(500));

Flux<String> result = Flux.concat(flux1, flux2); //здесь можно в качестве аргумента передавать любое количество Publishers (потоков с данными)
result.subscribe(System.out::println);
// Вывод: a, b, c, d — flux2 не начинает работу, пока flux1 не завершится (onComplete)
```

https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#concat(org.reactivestreams.Publisher...)

> EN: "Concatenate all sources provided as a vararg, forwarding elements emitted by the sources downstream."

> R: "**Конкатенирует (соединяет)** все источники, переданные в качестве **vararg**, передавая элементы, эмитируемые источниками, далее вниз по потоку."

Здесь **оба источника** передаются в статический метод как аргументы. Такая форма удобна, когда вы сразу работаете с несколькими готовыми `Publisher`.

---

```java

public static <T> Flux<T> concat(Publisher<? extends Publisher<? extends T>> sources, int prefetch) {
  return from(sources).concatMap(identityFunction(), prefetch);
}`
```


- `prefetch` в этом контракте — это **не количество чисел внутри `Flux`**, а сколько **вложенных `Publisher`** взять заранее из внешнего источника.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html

EN:

> `prefetch` - the number of Publishers to prefetch from the outer `Publisher`

RU:

> `prefetch` — количество `Publisher`, которые нужно заранее запросить у внешнего `Publisher`.

Пример:

```java
Flux<Flux<Integer>> sources = Flux.just(
    Flux.just(1, 2), //1 Publisher
    Flux.just(3, 4), //2 Publisher
    Flux.just(5, 6) //3 Publisher
);

Flux<Integer> result = Flux.concat(sources, 2);
```

Здесь `sources` — это не один поток чисел, а **внешний поток, который производит (emitting) другие потоки**.
- Поэтому `prefetch = 2` значит:
  - Reactor заранее попросит у внешнего `sources` **два inner publisher** в запас.

- Источник: https://github.com/reactor/reactor-core/blob/main/reactor-core/src/main/java/reactor/core/publisher/Flux.java

EN:

```java
 `public static <T> Flux<T> concat(Publisher<? extends Publisher<? extends T>> sources, int prefetch) {`

    return from(sources).concatMap(identityFunction(), prefetch);`
}
```

**RU**:

> `concat(sources, prefetch)` внутри реализован через `concatMap(identityFunction(), prefetch)`.

Если у тебя просто `Flux.just(1, 2, 3, ..., 100)`, то это **один publisher со 100 элементами**.
- Тут нет "100 publisher'ов", поэтому в таком примере `prefetch` из `concat(Publisher<Publisher<T>>, prefetch)` вообще не про числа, а не про **уровень вложенных потоков**.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html

EN:

> `Create a Flux that emits the provided elements and then completes.`

RU:

> `Создаёт Flux, который эмитит переданные элементы и затем завершается.`

Запомнить можно так:

```java
Flux<Flux<Integer>> sources = Flux.just(
        Flux.just(1, 2),
        Flux.just(3, 4)
);

Flux.concat(sources, 2);
```

Здесь `2` — это `prefetch`: сколько внутренних `Publisher` заранее запрашивается у внешнего `Publisher`.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html

EN:

> `prefetch` - the number of Publishers to prefetch from the outer `Publisher`

RU:

> `prefetch` — количество `Publisher`, которые нужно заранее запросить у внешнего `Publisher`.

---
```java

Flux<Flux<Order>> orderSources = orderRepository.findOrderSources();

Flux<Order> result = Flux.concat(orderSources, 2)
        .doOnNext(this::log);
```

Здесь `prefetch` регулирует количество обрабатываемых источников данных:
- внешний `Publisher` поставляет внутренние `Publisher`, а `2` задаёт, сколько таких источников запрашивается заранее.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html

EN:

> `prefetch` - the number of Publishers to prefetch from the outer `Publisher`

RU:

> `prefetch` — количество `Publisher`, которые нужно заранее запросить у внешнего `Publisher`.
---

<a id="concatwith"></a>

## concatWith — метод экземпляра

```java
Flux<String> flux1 = Flux.just("a", "b").delayElements(Duration.ofMillis(500));
Flux<String> flux2 = Flux.just("c", "d").delayElements(Duration.ofMillis(500));

Flux<String> result = flux1.concatWith(flux2);
result.subscribe(System.out::println);
// Вывод идентичен предыдущему примеру: a, b, c, d
```

https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#concatWith(org.reactivestreams.Publisher)

> EN: "Concatenate emissions of this Flux with the provided Publisher (no interleave)."

> R: "Конкатенирует производимые элементы данным Flux с предоставленным Publisher (без чередования)."

По поведению это та же **последовательная конкатенация**:
- сначала отрабатывает **левый поток**, затем после того, как **левый поток** сообщил сигнал `onComplete` и начинается **правый поток**. Разница не в семантике, а в форме вызова.

<a id="sugar"></a>

## Почему concatWith — синтаксический сахар

Разница между `concat` и `concatWith` не в логике выполнения, а в том, как эта логика записывается в коде.

```java
// Вариант А: статическая форма
Flux<Order> pendingOrders = orderRepository.findPendingOrders()
                .filter(order -> order.getAmount() > 100)
                .map(this::enrichOrder);

Flux<Order> archivedOrders = orderRepository.findArchivedOrders();

Flux<Order> result = Flux.concat(pendingOrders, archivedOrders)
        .doOnNext(this::log);


// Вариант Б: fluent-форма через метод экземпляра
Flux<Order> result2 = orderRepository.findPendingOrders()
    .filter(order -> order.getAmount() > 100)
    .map(this::enrichOrder)
    .concatWith(orderRepository.findArchivedOrders())
    .doOnNext(this::log);
```

В обоих случаях **смысл одинаковый**: 
 - сначала выполняется левая цепочка, затем к ней последовательно добавляется ещё один `Publisher`. 
 - Но `concatWith` позволяет не "заворачивать" всю предыдущую цепочку в аргумент `Flux.concat(...)`, а продолжать писать её **через точку**.

Именно в этом смысле `concatWith` — синтаксический сахар: не новый тип поведения, а более удобная форма записи той же операции для **fluent** API.

<a id="under-the-hood"></a>

## Как это выглядит под капотом

Чтобы понять природу `concatWith`, полезнее проводить аналогию не с лямбдой и анонимным классом, а со **статическим вызовом** и **вызовом через экземпляр**.

```java
Flux<String> left = Flux.just("A", "B");
Flux<String> right = Flux.just("C", "D");

// Статическая форма
Flux<String> result1 = Flux.concat(left, right);

// Форма через экземпляр
Flux<String> result2 = left.concatWith(right);
```

С точки зрения результата это одно и то же: сначала подписка идёт на `left`, после его завершения — на `right`. Разницы в порядке эмиссии здесь нет.

Но с точки зрения формы вызова разница есть:

 - Здесь оба потока передаются как аргументы функции.
```java
Flux.concat(left, right);
```


 - Здесь `left` становится текущим объектом (`this`), а `right` передаётся как дополнительный аргумент.

```java
left.concatWith(right);
```



Именно поэтому `concatWith` естественно ложится в цепочку:

```java
repository.findActiveOrders()
    .filter(Order::isValid)
    .map(this::enrich)
    .concatWith(repository.findArchivedOrders())
    .doOnNext(this::log);
```

Если мысленно развернуть эту запись по шагам, получится так:

```java
Flux<Order> step1 = repository.findActiveOrders();
Flux<Order> step2 = step1.filter(Order::isValid);
Flux<Order> step3 = step2.map(this::enrich);
Flux<Order> step4 = step3.concatWith(repository.findArchivedOrders());
Flux<Order> step5 = step4.doOnNext(this::log);
```

Вот ключевая идея: каждый оператор в Reactor не "выполняет поток сразу", а создаёт новый `Flux`, который оборачивает предыдущий. Поэтому вызов `.concatWith(other)` означает:

1. Слева уже есть построенная цепочка операторов.
2. `concatWith` создаёт новый `Flux`, который оборачивает эту цепочку.
3. При подписке он сначала подписывается на левый источник.
4. Только после `onComplete` левого источника он подписывается на `other`.
5. Поэтому `concatWith` — это не новая семантика, а новая форма подключения следующего `Publisher` к уже собранной слева цепочке.

То есть природа действия `concatWith` такая: он берёт **текущий Flux как левую часть**, а переданный `Publisher` — как правую часть, и строит над ними новый последовательный оператор конкатенации.


---

`concatWith` под капотом — это не "особая магия цепочки", а обычный вызов, который в итоге сводится к `concat(this, other)`.

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


RU:

> `concatWith(other)` проверяет частный случай `FluxConcatArray`, а в обычном случае просто вызывает `concat(this, other)`.

То есть природа `concatWith` очень простая: левый поток берётся как `this`, правый приходит параметром `other`, после чего собирается новый `Flux` конкатенации.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html

EN:

> `Concatenate emissions of this Flux with the provided Publisher (no interleave).`

RU:

> Последовательно склеивает текущий `Flux` с переданным `Publisher`, без перемешивания элементов.

---

<a id="concatmap"></a>

## concatMap — трансформация в inner publisher

```java
Flux<Integer> source = Flux.just(1, 2, 3);

source.concatMap(value ->
        Mono.just(value * 10).delayElement(Duration.ofMillis(100))
    )
    .subscribe(System.out::println);
// Вывод: 10, 20, 30 — второй inner publisher не подписывается, пока первый не завершится
```

https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#concatMap(java.util.function.Function)

> EN: "Transform the elements emitted by this Flux asynchronously into Publishers, then flatten these inner publishers into a single Flux, sequentially and preserving order using concatenation."

> R: "Трансформирует элементы, эмитируемые этим Flux, асинхронно в Publisher'ы, затем разворачивает эти внутренние (inner) publisher'ы в единый Flux, последовательно и с сохранением порядка, используя конкатенацию."

Это уже не оператор “склейки двух готовых потоков”. Здесь сначала каждый элемент превращается функцией в новый `Publisher`, а затем эти внутренние publisher'ы последовательно раскрываются в один результирующий поток.

Для контраста — `flatMap` тоже делает преобразование в `Publisher`, но объединяет их через merge, поэтому допускает перемешивание результатов.

https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#flatMap(java.util.function.Function)

> EN: "Transform the elements emitted by this Flux asynchronously into Publishers, then flatten these inner publishers into a single Flux through merging, which allow them to interleave."

> R: "Трансформирует элементы, эмитируемые этим Flux, асинхронно в Publisher'ы, затем разворачивает эти внутренние publisher'ы в единый Flux через слияние (merge), которое допускает их чередование."

---

`flatMap` действительно сначала применяет функцию `T -> Publisher<R>` к каждому элементу внешнего `Flux`, а затем объединяет сигналы полученных inner publisher’ов в единый `Flux`.

Официальная документация прямо говорит: `flatMap` «flatten these inner publishers into a single Flux through merging, which allow them to interleave» — то есть разворачивает внутренние publisher’ы через **merge**, позволяя их элементам перемежаться.

https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html\#flatMap(java.util.function.Function)

## Что означает преобразование в `Publisher`

У обычного `map` функция возвращает значение:

```java
Flux<Integer> result =
    Flux.just(1, 2, 3)
        .map(value -> value * 10);
```

Логически это преобразование:

```java
Integer -> Integer
```

У `flatMap` функция возвращает новый реактивный источник:

```java
Flux<Integer> result =
    Flux.just(1, 2, 3)
        .flatMap(value -> Mono.just(value * 10));
```

Здесь сигнатура преобразования такая:

```java
Integer -> Publisher<Integer>
```

Концептуально можно представить промежуточный результат так:

```java
Flux<Mono<Integer>> inners =
    Flux.just(1, 2, 3)
        .map(value -> Mono.just(value * 10));
```

После этого `flatMap` подписывается на созданные `Mono`/`Flux` и объединяет все их эмиссии в один downstream-поток. В этом смысле он похож на «`map` в `Publisher` плюс merge», хотя реальная внутренняя реализация Reactor оптимизирована и не обязана буквально вызывать публичный `Flux.merge(...)`.

https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html\#flatMap(java.util.function.Function)

## Пример merge-поведения

```java
Flux.just(1, 2, 3)
    .flatMap(value ->
        Mono.just(value * 10)
            .delayElement(Duration.ofMillis((4 - value) * 100L))
    )
    .subscribe(System.out::println);
```

Создаются три inner publisher’а:

```text
1 -> Mono<10>, готов через 300 мс
2 -> Mono<20>, готов через 200 мс
3 -> Mono<30>, готов через 100 мс
```

Поскольку `flatMap` не ждёт завершения первого inner publisher перед подпиской на следующий, значения придут приблизительно в таком порядке:

```text
30
20
10
```

Это и означает merge: результат приходит в порядке фактического поступления сигналов от активных inner publisher’ов, поэтому исходный порядок внешнего `Flux` не сохраняется.

https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html\#flatMap(java.util.function.Function)

## Отличие от `concatMap`

| Оператор | Подписка на inner publisher | Порядок результатов | Стратегия |
| :-- | :-- | :-- | :-- |
| `concatMap` | Следующий — только после завершения текущего | Сохраняется | Concatenation |
| `flatMap` | Несколько inner publisher’ов могут быть активны одновременно | Может нарушаться | Merging |
| `flatMapSequential` | Может подписываться конкурентно | Сохраняется | Упорядоченный merge |

Для `concatMap` Javadoc говорит именно о последовательном разворачивании с сохранением порядка через конкатенацию.

https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html\#concatMap(java.util.function.Function)

Для `flatMap` Javadoc явно использует термин **merging** и отдельно указывает на возможность **interleaving** — перемешивания элементов разных inner publisher’ов.

https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html\#flatMap(java.util.function.Function)

## Исходный код

В исходниках публичный `Flux.flatMap(...)` делегирует работу оператору `FluxFlatMap`. Эта реализация управляет несколькими активными inner subscription’ами, принимает их элементы и передаёт их одному downstream subscriber’у; параметр `concurrency` ограничивает число одновременно активных inner publisher’ов.

https://github.com/reactor/reactor-core/blob/main/reactor-core/src/main/java/reactor/core/publisher/Flux.java

Реализация самого оператора находится здесь:

https://github.com/reactor/reactor-core/blob/main/reactor-core/src/main/java/reactor/core/publisher/FluxFlatMap.java

У `concatMap` отдельный оператор и иная стратегия: он не переходит к следующему inner publisher, пока текущий не завершится.

https://github.com/reactor/reactor-core/blob/main/reactor-core/src/main/java/reactor/core/publisher/FluxConcatMap.java


---

Поэтому разница такая:

- `concat` / `concatWith` — соединяют уже готовые `Publisher`;
- `concatMap` — сначала создаёт `Publisher` из каждого элемента, затем склеивает их последовательно;
- `flatMap` — тоже создаёт `Publisher` из каждого элемента, но потом merge'ит их с возможным interleaving.

---


`Publisher` здесь обычно не «создаётся ради данных». Он представляет **асинхронную операцию**, которая позже выдаст результат:

```java
userId -> repository.findById(userId)     // Mono<User>
order  -> paymentService.pay(order)       // Mono<Payment>
file   -> webClient.post(...).retrieve()  // Mono<Response>
```

То есть внешний `Flux` выдаёт задания или входные значения, а `concatMap` / `flatMap` превращают каждое из них в отдельную асинхронную работу.

```java
Flux<Integer> ids = Flux.just(1, 2, 3);

ids.concatMap(id -> repository.findById(id));
```

Для каждого `id` создаётся свой `Mono<User>` — запрос к БД. Затем:

- `concatMap` выполняет такие запросы последовательно;
- `flatMap` запускает несколько запросов одновременно и merge'ит ответы;
- `flatMapSequential` может запускать одновременно, но выдаёт ответы в исходном порядке.

С `Mono.just(value * 10)` пример искусственный: 
 - он нужен лишь показать механику. 
 - В реальном коде inner `Publisher` чаще всего оборачивает I/O, задержку, HTTP-вызов, чтение файла, отправку сообщения или другую асинхронную операцию.


---

# Когда и что использовать ?

- `concatMap` нужен, когда набор inner publisher’ов **неизвестен заранее** или возникает динамически из элементов входного потока.

- `concat` подходит, если источники уже есть:

```java
Flux.concat(
    repository.findById(1),
    repository.findById(2),
    repository.findById(3)
);
```

Но если `id` приходят из другого `Flux`, писать `concat` невозможно или неудобно, поэтому используем **concatMap**:

```java
Flux<Long> ids = getIds();

ids.concatMap(repository::findById);
```

Это по смыслу:

```java
ids
    .map(repository::findById) // Flux<Mono<User>>
    .concatMap(Function.identity());
```

То есть главная выгода — **динамическое создание и последовательное выполнение операций для каждого элемента потока**: 
 - **например**: 
   - последовательно сохранить события, 
   - обработать файлы или 
   - выполнить HTTP-запросы, **cохранив их порядок**.

https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html\#concatMap(java.util.function.Function)

---

<a id="summary"></a>

## Итоговая таблица различий

| Оператор | Когда использовать | Что делает |
| :-- | :-- | :-- |
| `Flux.concat(a, b)` | Publisher’ы уже известны и перечислены в коде | Подписывается на `a`, после его завершения — на `b` |
| `a.concatWith(b)` | Нужно дописать следующий готовый Publisher к текущей цепочке | То же самое, что `concat`, но в fluent-стиле |
| `source.concatMap(item -> operation(item))` | Каждый элемент `source` нужно обработать отдельной асинхронной операцией | Берёт первый элемент, запускает для него операцию и ждёт её окончания. Только затем берёт второй элемент и делает то же самое. |

Например:

```java
Flux.just(1, 2, 3)
    .concatMap(id -> repository.findById(id));
```

Читается так: 
 - «получи пользователя с `id = 1`; 
 - когда запрос завершится — получи пользователя с `id = 2`; 
 - затем — с `id = 3`».

`item -> operation(item)` — это просто запись функции: 
 - она получает очередной элемент (`item`) и возвращает **асинхронную операцию** для него.

 - Ключевая мысль об операторе `concat()`:

```java
Flux.concat(a, b, c)
```

 - это «у меня уже есть асинхронные операции `a`, `b`, `c`».