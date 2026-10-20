# Тема 02. Источники: Mono и Flux

> **Статус:** теория · **Дальше:** интервью в чате, затем [практика](02-sources-tasks.md)

---

## Оглавление

- [1. Mono и Flux](#1-mono-и-flux)
- [2. Когда вычисляется значение: just, fromCallable, defer](#2-когда-вычисляется-значение-just-fromcallable-defer)
- [3. Ошибка — это сигнал, а не исключение](#3-ошибка--это-сигнал-а-не-исключение)
- [4. Готовые последовательности: range, fromIterable](#4-готовые-последовательности-range-fromiterable)
- [5. Последовательность с состоянием: generate](#5-последовательность-с-состоянием-generate)
- [6. Пусто против null](#6-пусто-против-null)
- [Вопросы для интервью](#вопросы-для-интервью)

---

## 1. Mono и Flux

**Аналогия:**

Курьер привозит либо одну посылку, либо сообщение «посылки нет».

Грузовик привозит сколько угодно коробок или ни одной.

**Как это устроено в коде:**

- **Курьер** — `Mono<T>`: выдаёт **0 или 1** элемент, затем завершение или ошибку.
- **Грузовик** — `Flux<T>`: выдаёт **0…N** элементов.
- Оба реализуют `Publisher<T>` и следуют одному протоколу сигналов из темы 01.
- Тип сам говорит читателю кода, сколько ждать результатов.

| Что возвращает метод | Тип |
|---|---|
| поиск по первичному ключу | `Mono<Customer>` |
| список заказов | `Flux<Order>` |
| «сделано», без значения | `Mono<Void>` |
| количество, сумма | `Mono<Long>`, `Mono<BigDecimal>` |

**Правило:** `Mono` — не больше одного, `Flux` — сколько угодно.

---

## 2. Когда вычисляется значение: just, fromCallable, defer

**Аналогия:**

- «Вот готовый бутерброд» — его сделали утром. Сколько бы людей ни пришло, бутерброд один и тот же.
- «Сделаю бутерброд, когда попросите» — каждому свой, свежий.

**Как это устроено в коде:**

- **Готовый бутерброд** — `Mono.just(x)`: берёт **уже вычисленное** значение. Выражение `x` Java выполнила ещё до вызова `just`, при сборке.
- **«Сделаю, когда попросите»** — `Mono.fromCallable(() -> x)`: вызывает лямбду **при каждой подписке**.
- **«Решу, что делать, когда попросите»** — `Mono.defer(() -> mono)`: при каждой подписке строит новый `Mono`. Так откладывают выбор источника.

```java
AtomicInteger calls = new AtomicInteger();
Mono<Integer> eager = Mono.just(calls.incrementAndGet());           // вызов прямо сейчас
Mono<Integer> lazy  = Mono.fromCallable(calls::incrementAndGet);     // вызов при подписке
System.out.println("после сборки: " + calls.get());                 // после сборки: 1
eager.subscribe(v -> System.out.println("just " + v));               // just 1
eager.subscribe(v -> System.out.println("just " + v));               // just 1
lazy.subscribe(v -> System.out.println("fromCallable " + v));        // fromCallable 2
lazy.subscribe(v -> System.out.println("fromCallable " + v));        // fromCallable 3
```

**Разбор:**

- Строка с `just`: Java сначала вычисляет аргумент `calls.incrementAndGet()`, потом передаёт число 1 в `just`. Reactor этого вызова не видит и отложить не может.
- Строка с `fromCallable`: в `Mono` передана ссылка на метод, а не результат. Вызов случится в момент подписки.
- Две подписки на `eager` получили одно и то же 1. Две подписки на `lazy` вызвали счётчик два раза.

Javadoc `Mono.just(T)`, reactor-core 3.8:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#just(T)

> Create a new Mono that emits the specified item, which is captured at instantiation time.

RU:

> Создаёт новый `Mono`, который выдаёт указанный элемент, захваченный в момент создания.

Javadoc `Mono.fromCallable(Callable)`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#fromCallable(java.util.concurrent.Callable)

> Create a Mono producing its value using the provided Callable. If the Callable resolves to null, the resulting Mono completes empty.

RU:

> Создаёт `Mono`, который производит значение с помощью переданного `Callable`. Если `Callable` вернул `null`, итоговый `Mono` завершается пустым.

Javadoc `Flux.defer(Supplier)`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#defer(java.util.function.Supplier)

> Lazily supply a Publisher every time a Subscription is made on the resulting Flux, so the actual source instantiation is deferred until each subscribe and the Supplier can create a subscriber-specific instance.

RU:

> Лениво предоставляет `Publisher` при каждой подписке на итоговый `Flux`, так что создание настоящего источника откладывается до каждой подписки, и `Supplier` может создать отдельный экземпляр для каждого подписчика.

**Почему не иначе:**

`Mono.just(gateway.fetchPrice(sku))`:

- идёт в сеть в момент вызова метода, даже если на результат никто не подпишется;
- при повторной подписке вернёт старую цену;
- если `fetchPrice` бросит исключение, оно вылетит из метода сборки, а не придёт сигналом `onError`.

**Правило:** `just` — для готовых значений; вызов чего-то внешнего — через `fromCallable` или `defer`.

---

## 3. Ошибка — это сигнал, а не исключение

**Аналогия:**

Курьер не бросает в вас посылку с надписью «ошибка».

Он привозит письмо: «доставка невозможна, причина такая-то».

**Как это устроено в коде:**

- **Бросить посылку** — `throw` в методе, который возвращает `Mono` или `Flux`. Так делать не надо.
- **Письмо с причиной** — `Mono.error(new ...)`: издатель, который сразу после `onSubscribe` отправит `onError`.
- **Получатель** — вызывающий код обрабатывает ошибки единообразно, операторами темы 06.

```java
Mono<Integer> quantity(int q) {
    if (q <= 0) {
        return Mono.error(new IllegalArgumentException("Плохое количество: " + q));
    }
    return Mono.just(q);
}

Mono<Integer> result = quantity(0);          // исключения нет, получили Mono
result.subscribe(
        v -> System.out.println("ok " + v),
        e -> System.out.println("ошибка: " + e.getMessage()));   // ошибка: Плохое количество: 0
```

**Разбор:**

- исключение, брошенное внутри лямбды оператора, Reactor тоже превращает в `onError`;
- наружу из метода сборки оно не вылетает: на момент сборки лямбда ещё не вызывалась.

- Источник: https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html

> As a rule of thumb, an unchecked exception is always propagated through `onError`. For instance, throwing a `RuntimeException` inside a `map` function translates to an `onError` event

RU:

> Как правило, непроверяемое исключение всегда распространяется через `onError`. Например, `RuntimeException`, брошенное внутри функции `map`, превращается в событие `onError`.

**Почему не иначе:**

- `throw` прямо в теле метода вылетит у вызывающего ещё при сборке цепочки;
- исключение пройдёт мимо `onErrorResume` и прочих обработчиков, которые он навесил.

**Правило:** метод с типом `Mono`/`Flux` сообщает об ошибке через `Mono.error`/`Flux.error`, а не через `throw`.

---

## 4. Готовые последовательности: range, fromIterable

**Как это устроено в коде:**

- `Flux.range(start, count)` — `count` целых чисел подряд, начиная со `start`;
- `Flux.fromIterable(list)` — элементы коллекции по порядку;
- оба соблюдают спрос: при `request(2)` выдадут два элемента и подождут.

```java
Flux.range(5, 3).subscribe(System.out::println);           // 5, 6, 7
Flux.fromIterable(List.of("a", "b")).subscribe(System.out::println);   // a, b
```

**Внимание:** второй аргумент `range` — количество, а не конец диапазона. Частая ошибка на интервью.

**Правило:** `range(start, count)` — `count` штук, начиная со `start`.

---

## 5. Последовательность с состоянием: generate

**Аналогия:**

Кассир выдаёт талончики: помнит номер последнего и на каждую просьбу выдаёт следующий.

Когда талончики кончились, он вешает табличку «закрыто».

**Как это устроено в коде:**

`Flux.generate(начальное состояние, (состояние, sink) -> новое состояние)`:

- **Номер в памяти кассира** — состояние. Для каждого подписчика оно начинается заново.
- **Одна просьба — один талончик** — лямбда вызывается по одному разу на каждый запрошенный элемент и делает **один** `sink.next(x)`.
- **Табличка «закрыто»** — `sink.complete()`; при сбое — `sink.error(e)`.
- **Следующий номер** — значение, которое вернула лямбда: состояние для следующего вызова.

```java
Flux<Integer> powersOfThree = Flux.generate(
        () -> 1,                                   // состояние для нового подписчика
        (current, sink) -> {
            if (current > 30) {
                sink.complete();                   // больше элементов не будет
            } else {
                sink.next(current);                // ровно один элемент за вызов
            }
            return current * 3;                    // состояние для следующего вызова
        });
powersOfThree.subscribe(System.out::println);      // 1, 3, 9, 27
```

Javadoc `Flux.generate(Consumer)`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#generate(java.util.function.Consumer)

> Programmatically create a Flux by generating signals one-by-one via a consumer callback.
>
> generator - Consume the SynchronousSink provided per-subscriber by Reactor to generate a single signal on each pass.

RU:

> Программно создаёт `Flux`, генерируя сигналы по одному через callback-потребитель.
>
> generator — потребитель `SynchronousSink`, который Reactor выдаёт каждому подписчику свой, чтобы за каждый проход сгенерировать один сигнал.

**Почему не иначе:**

- два `sink.next` в одном вызове `generate` — ошибка: генератор синхронный и выдаёт один элемент за проход;
- чтобы выдать готовый список сразу, есть `fromIterable`.

**Правило:** `generate` — когда следующий элемент вычисляется из предыдущего.

---

## 6. Пусто против null

**Как это устроено в коде:**

- в Reactive Streams элемент не может быть `null`;
- «нет значения» — это пустой `Mono`: `onSubscribe`, затем сразу `onComplete`;
- если внешний код вернул `null`, его превращают в пустоту одним из способов:
  - `Mono.justOrEmpty(x)`;
  - `fromCallable` — как в цитате выше;
  - явная проверка внутри `defer`.

- Источник: https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.4/README.md, правило 2.13

> Calling `onSubscribe`, `onNext`, `onError` or `onComplete` MUST return normally except when any provided parameter is `null` in which case it MUST throw a `java.lang.NullPointerException` to the caller

RU:

> Вызов `onSubscribe`, `onNext`, `onError` или `onComplete` ДОЛЖЕН завершаться нормально, кроме случая, когда какой-либо переданный параметр равен `null`, — тогда он ДОЛЖЕН бросить вызывающему `java.lang.NullPointerException`.

```java
Mono.justOrEmpty(null).subscribe(
        v -> System.out.println("значение"),
        e -> System.out.println("ошибка"),
        () -> System.out.println("пусто"));        // пусто
```

Javadoc `Mono.justOrEmpty(T)`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#justOrEmpty(T)

> Create a new Mono that emits the specified item if non null otherwise only emits onComplete.

RU:

> Создаёт новый `Mono`, который выдаёт указанный элемент, если он не `null`, а иначе выдаёт только `onComplete`.

**Правило:** нет значения — пустой `Mono`, не `null`.

---

## Вопросы для интервью

1. Чем `Mono` отличается от `Flux`? Какой тип у метода «сохранить и ничего не вернуть»?
2. В какой момент вызывается `service.call()` в `Mono.just(service.call())`, а в какой — в `Mono.fromCallable(service::call)`?
3. Что увидят два подписчика `Mono.just(System.currentTimeMillis())`? А `Mono.fromCallable(System::currentTimeMillis)`?
4. Зачем нужен `defer`, если есть `fromCallable`?
5. Почему метод, возвращающий `Mono`, не должен бросать исключение? Что вернуть вместо этого?
6. Что выдаст `Flux.range(10, 3)`?
7. Как устроен `generate`: сколько раз вызывается лямбда и что она возвращает?
8. Чем пустой `Mono` отличается от `Mono` с `null`? Как превратить `null` от внешнего кода в пустой ответ?

---

**Практика:** [02-sources-tasks.md](02-sources-tasks.md)
