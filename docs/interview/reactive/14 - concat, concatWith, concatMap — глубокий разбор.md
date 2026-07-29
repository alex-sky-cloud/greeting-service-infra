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

В обоих случаях смысл одинаковый: сначала выполняется левая цепочка, затем к ней последовательно добавляется ещё один `Publisher`. Но `concatWith` позволяет не “заворачивать” всю предыдущую цепочку в аргумент `Flux.concat(...)`, а продолжать писать её через точку.

Именно в этом смысле `concatWith` — синтаксический сахар: не новый тип поведения, а более удобная форма записи той же операции для fluent API.

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

```java
Flux.concat(left, right);
```

Здесь оба потока передаются как аргументы функции.

```java
left.concatWith(right);
```

Здесь `left` становится текущим объектом (`this`), а `right` передаётся как дополнительный аргумент.

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

Вот ключевая идея: каждый оператор в Reactor не “выполняет поток сразу”, а создаёт новый `Flux`, который оборачивает предыдущий. Поэтому вызов `.concatWith(other)` означает:

1. Слева уже есть построенная цепочка операторов.
2. `concatWith` создаёт новый `Flux`, который оборачивает эту цепочку.
3. При подписке он сначала подписывается на левый источник.
4. Только после `onComplete` левого источника он подписывается на `other`.
5. Поэтому `concatWith` — это не новая семантика, а новая форма подключения следующего `Publisher` к уже собранной слева цепочке.

То есть природа действия `concatWith` такая: он берёт **текущий Flux как левую часть**, а переданный `Publisher` — как правую часть, и строит над ними новый последовательный оператор конкатенации.

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

Поэтому разница такая:

- `concat` / `concatWith` — соединяют уже готовые `Publisher`;
- `concatMap` — сначала создаёт `Publisher` из каждого элемента, затем склеивает их последовательно;
- `flatMap` — тоже создаёт `Publisher` из каждого элемента, но потом merge'ит их с возможным interleaving.

<a id="summary"></a>

## Итоговая таблица различий

| Аспект | concat | concatWith | concatMap |
| :-- | :-- | :-- | :-- |
| Тип вызова | Статический метод `Flux.concat(...)` | Метод экземпляра `flux.concatWith(...)` | Оператор-трансформация `flux.concatMap(fn)` |
| Что объединяет | Готовые `Publisher` | Готовые `Publisher` | `Inner Publisher`, созданные из элементов |
| Поведение | Последовательная подписка | Последовательная подписка | Последовательная подписка на inner publisher |
| Fluent-стиль | Часто разрывает цепочку | Естественно продолжает цепочку | Естественно продолжает цепочку |
| Interleaving | Нет | Нет | Нет |
| Отношение к другому | Та же семантика, что у `concatWith` | Та же семантика, что у `concat` | Другая категория оператора: `map` + последовательный flatten |