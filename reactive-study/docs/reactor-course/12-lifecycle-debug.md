# Тема 12. Жизненный цикл, ресурсы и отладка

> **Статус:** теория · **Дальше:** интервью в чате, затем [практика](12-lifecycle-debug-tasks.md)

---

## Оглавление

- [1. Подсмотреть сигналы: doOn*](#1-подсмотреть-сигналы-doon)
- [2. doFinally: при любом исходе](#2-dofinally-при-любом-исходе)
- [3. Flux.using: синхронный ресурс](#3-fluxusing-синхронный-ресурс)
- [4. Mono.usingWhen: асинхронный ресурс с commit и rollback](#4-monousingwhen-асинхронный-ресурс-с-commit-и-rollback)
- [5. Почему стек ошибки бесполезен и что с этим делать](#5-почему-стек-ошибки-бесполезен-и-что-с-этим-делать)
- [6. log: увидеть все сигналы](#6-log-увидеть-все-сигналы)
- [Вопросы для интервью](#вопросы-для-интервью)

---

## 1. Подсмотреть сигналы: doOn*

**Аналогия:**

Счётчик посетителей на входе в магазин.

Он не мешает людям проходить, а только записывает, кто вошёл.

**Как это устроено в коде:**

- **Счётчик** — операторы `doOnSubscribe`, `doOnNext`, `doOnError`, `doOnComplete`, `doOnCancel`: вызывают ваш код при соответствующем сигнале.
- **Не мешает проходить** — сигнал идёт дальше без изменений, элементы не меняются.
- **Зачем** — метрики, логи, аудит.
- **Магазин закрыт** — как и вся цепочка, `doOn*` срабатывают только после подписки.

Javadoc `Flux.doOnSubscribe(Consumer)`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#doOnSubscribe(java.util.function.Consumer)

> Add behavior (side-effect) triggered when the Flux is being subscribed, that is to say when a Subscription has been produced by the Publisher and is being passed to the Subscriber.onSubscribe(Subscription). This method is not intended for capturing the subscription and calling its methods, but for side effects like monitoring.

RU:

> Добавляет поведение (побочный эффект), срабатывающее при подписке на `Flux`, то есть когда `Publisher` создал `Subscription` и передаёт её в `Subscriber.onSubscribe(Subscription)`. Метод не предназначен для перехвата подписки и вызова её методов — он для побочных эффектов вроде мониторинга.

**Правило:** наблюдать, не меняя, — `doOn*`.

---

## 2. doFinally: при любом исходе

**Аналогия:**

Охранник выключает свет в зале, когда последний посетитель ушёл. Неважно, как он ушёл:

- досмотрел фильм;
- ушёл из-за поломки проектора;
- просто встал и вышел.

**Как это устроено в коде:**

- **Досмотрел фильм** — `ON_COMPLETE`.
- **Поломка проектора** — `ON_ERROR`.
- **Встал и вышел** — `CANCEL`: подписчик отказался, например `take(1)`.
- **Охранник** — `doFinally(signalType -> ...)`: срабатывает при любом из трёх и получает, каким именно.

**Внимание:**

`doOnComplete` и `doOnError` отмену не увидят.

```java
Flux.just(1, 2, 3)
    .doFinally(s -> System.out.println("finally " + s))
    .take(1)
    .blockLast();
// finally cancel

Flux.just(1)
    .doFinally(s -> System.out.println("finally " + s))
    .subscribe(v -> {}, e -> {}, () -> System.out.println("subscriber complete"));
// subscriber complete
// finally onComplete
```

**Разбор** (запуск на Reactor 3.8.4):

- при `take(1)` тип сигнала — `cancel`;
- `doFinally` срабатывает **после** того, как подписчик получил сигнал.

Javadoc `Flux.doFinally(Consumer)`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#doFinally(java.util.function.Consumer)

> Add behavior (side-effect) triggered after the Flux terminates for any reason, including cancellation. The terminating event (SignalType.ON_COMPLETE, SignalType.ON_ERROR and SignalType.CANCEL) is passed to the consumer, which is executed after the signal has been passed downstream.

RU:

> Добавляет поведение (побочный эффект), срабатывающее после завершения `Flux` по любой причине, включая отмену. Завершающее событие (`SignalType.ON_COMPLETE`, `SignalType.ON_ERROR` и `SignalType.CANCEL`) передаётся в consumer, который выполняется после того, как сигнал передан вниз.

**Правило:** «в любом случае, включая отмену» — `doFinally`.

---

## 3. Flux.using: синхронный ресурс

**Аналогия:**

Библиотека выдаёт книгу каждому читателю отдельно. Забирает её обратно в любом случае:

- читатель дочитал;
- бросил;
- уронил книгу в лужу.

**Как это устроено в коде:**

- **Книга** — ресурс: файл, сокет, курсор. Его надо закрыть при любом исходе.
- **Библиотека** — `Flux.using(open, use, close)`:
  1. `open` — открыть ресурс, отдельно для каждой подписки;
  2. `use` — построить из ресурса поток;
  3. `close` — закрыть при завершении, ошибке или отмене.

**Почему не иначе:**

- `try-with-resources` здесь не помогает;
- метод вернёт `Flux` и закроет ресурс сразу, а чтение начнётся позже, при подписке.

```java
Flux.using(
        () -> { System.out.println("open"); return "res"; },
        r -> Flux.<String>error(new IllegalStateException("bad")),
        r -> System.out.println("close " + r))
    .onErrorResume(e -> Flux.empty())
    .blockLast();
// open
// close res
```

Вывод — запуск на Reactor 3.8.4.

Javadoc `Flux.using(Callable, Function, Consumer)`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#using(java.util.concurrent.Callable,java.util.function.Function,java.util.function.Consumer)

> Uses a resource, generated by a supplier for each individual Subscriber, while streaming the values from a Publisher derived from the same resource and makes sure the resource is released if the sequence terminates or the Subscriber cancels.

RU:

> Использует ресурс, созданный поставщиком для каждого отдельного подписчика, выдавая значения из `Publisher`, построенного из этого ресурса, и гарантирует освобождение ресурса, если последовательность завершилась или подписчик отменил подписку.

**Правило:** синхронный ресурс на время потока — `Flux.using`.

---

## 4. Mono.usingWhen: асинхронный ресурс с commit и rollback

**Аналогия:**

Нотариус оформляет сделку:

1. Открывает сделку.
2. Стороны подписывают документы.
3. Всё прошло — сделку регистрируют.
4. Ошибка или кто-то ушёл — сделку аннулируют.

И открытие, и регистрация, и аннулирование — тоже долгие операции.

**Как это устроено в коде:**

- **Долгие операции нотариуса** — открытие и закрытие ресурса сами асинхронные (`Mono`).
- **Нотариус** — `usingWhen`.
- **Разные исходы** — в полной форме у `usingWhen` разные пути закрытия:

| Параметр | Когда вызывается | Пример |
|---|---|---|
| `resourceSupplier` | при подписке | `begin()` сессии |
| `resourceClosure` | работа с ресурсом | `write(rows)` |
| `asyncComplete` | работа завершилась успешно | `commit()` |
| `asyncError` | работа завершилась ошибкой | `rollback()` |
| `asyncCancel` | подписчик отменил | `rollback()` |

**Для сравнения:**

Та же схема «начать → работать → подтвердить или откатить» — у транзакций в теме 13. Там её берёт на себя `TransactionalOperator`.

Javadoc `Mono.usingWhen(Publisher, Function, Function, BiFunction, Function)`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#usingWhen(org.reactivestreams.Publisher,java.util.function.Function,java.util.function.Function,java.util.function.BiFunction,java.util.function.Function)

> Uses a resource, generated by a Publisher for each individual Subscriber, to derive a Mono. Note that all steps of the operator chain that would need the resource to be in an open stable state need to be described inside the resourceClosure Function. …
>
> asyncComplete - an asynchronous resource cleanup invoked if the resource closure terminates with onComplete
>
> asyncError - an asynchronous resource cleanup invoked if the resource closure terminates with onError. The terminating error is provided to the BiFunction
>
> asyncCancel - an asynchronous resource cleanup invoked if the resource closure is cancelled. When null, the asyncComplete path is used instead.

RU:

> Использует ресурс, созданный `Publisher` для каждого отдельного подписчика, чтобы построить `Mono`. Все шаги цепочки, которым ресурс нужен открытым и стабильным, нужно описать внутри функции `resourceClosure`. …
>
> `asyncComplete` — асинхронная очистка ресурса, вызывается, если `resourceClosure` завершился `onComplete`.
>
> `asyncError` — асинхронная очистка ресурса, вызывается, если `resourceClosure` завершился `onError`. Завершающая ошибка передаётся в `BiFunction`.
>
> `asyncCancel` — асинхронная очистка ресурса, вызывается при отмене `resourceClosure`. Если `null`, используется путь `asyncComplete`.

**Почему не иначе:**

- если не передать `asyncCancel`, отмена пойдёт по пути `asyncComplete`;
- то есть недописанная загрузка будет **подтверждена**.

**Правило:** асинхронный ресурс с подтверждением и откатом — `usingWhen` с отдельными путями для ошибки и отмены.

---

## 5. Почему стек ошибки бесполезен и что с этим делать

**Аналогия:**

Посылка пришла разбитой.

Накладная говорит, какой курьер её привёз, но не говорит, на каком складе её уронили.

**Как это устроено в коде:**

- **Склад** — место сборки (assembly): цепочку **собирают** в одном месте кода.
- **Курьер** — выполнение: цепочку **выполняют** позже, при подписке, часто на другом потоке.
- **Накладная** — стек исключения: показывает внутренности Reactor и место выполнения, но не строку, где вы написали `map`.

Что помогает найти «склад»:

| Способ | Цена | Что даёт |
|---|---|---|
| `checkpoint("описание")` | низкая | метка в тексте ошибки; ставится на важные цепочки |
| `checkpoint()` | средняя | стек места сборки для этой цепочки |
| `Hooks.onOperatorDebug()` | высокая, на каждый оператор | стек места сборки для всех цепочек; только для отладки |

```java
try {
    Flux.just("5", "x").map(Integer::parseInt).checkpoint("импорт количеств").blockLast();
} catch (Exception e) {
    // e: java.lang.NumberFormatException
    // e.getSuppressed()[0]: FluxOnAssembly$OnAssemblyException:
    //   Error has been observed at the following site(s):
    //       *__checkpoint ⇢ импорт количеств
}
```

**Разбор** (запуск на Reactor 3.8.4):

- тип ошибки не меняется;
- метка добавляется как suppressed-исключение.

Javadoc `Flux.checkpoint(String)`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#checkpoint(java.lang.String)

> Activate traceback (assembly marker) for this particular Flux by giving it a description that will be reflected in the assembly traceback in case of an error upstream of the checkpoint. Note that unlike checkpoint(), this doesn't create a filled stack trace, avoiding the main cost of the operator. … It should be placed towards the end of the reactive chain, as errors triggered downstream of it cannot be observed and augmented with assembly trace. The traceback is attached to the error as a suppressed exception.

RU:

> Включает traceback (метку сборки) для этого `Flux`, задавая описание, которое попадёт в traceback при ошибке выше checkpoint. В отличие от `checkpoint()`, не создаёт заполненный стек — это убирает основную цену оператора. … Ставить его стоит ближе к концу цепочки: ошибки, возникшие ниже него, он не видит и не дополняет. Traceback прикрепляется к ошибке как suppressed-исключение.

Reactor Reference Guide, «Debugging Reactor»:

- Источник: https://projectreactor.io/docs/core/release/reference/debugging.html

> this section describes the easiest but also the slowest way to enable the debugging capabilities due to the fact that it captures the stacktrace on every operator. …
>
> We deal with a form of instrumentation here, and creating a stack trace is costly. That is why this debugging feature should only be activated in a controlled manner, as a last resort. …
>
> There is also a `checkpoint(String)` variant that lets you add a unique `String` identifier to the assembly traceback. This way, the stack trace is omitted and you rely on the description to identify the assembly site. `checkpoint(String)` imposes less processing cost than a regular `checkpoint`.

RU:

> в этом разделе описан самый простой, но и самый медленный способ включить отладку: он захватывает стек на каждом операторе. …
>
> Это разновидность инструментирования, а создание стека — дорогая операция. Поэтому эту отладочную возможность стоит включать только контролируемо, в крайнем случае. …
>
> Есть и вариант `checkpoint(String)`: он добавляет в traceback сборки уникальный строковый идентификатор. Стек при этом не собирается, и место сборки находят по описанию. `checkpoint(String)` обходится дешевле обычного `checkpoint`.

**Правило:** важные цепочки помечают `checkpoint("…")`; глобальную отладку не держат включённой в продакшене.

---

## 6. log: увидеть все сигналы

**Аналогия:**

Видеорегистратор на конвейере.

Пишет всё, что проехало мимо, в обе стороны.

**Как это устроено в коде:**

- **Видеорегистратор** — `log()`: пишет в лог все сигналы в месте, где стоит.
- **В обе стороны**:
  - вниз — `onSubscribe`, `onNext`, `onError`, `onComplete`;
  - вверх — `request`, `cancel`.
- **Зачем** — увидеть, кто сколько запросил и кто отменил.

```java
Flux.just(1, 2).log("заказы").take(1).blockLast();
// [ INFO] (main) | onSubscribe([Synchronous Fuseable] FluxArray.ArraySubscription)
// [ INFO] (main) | request(1)
// [ INFO] (main) | onNext(1)
// [ INFO] (main) | cancel()
```

Вывод — запуск на Reactor 3.8.4 без SLF4J (вывод в консоль).

Reactor Reference Guide, «Logging a Sequence»:

- Источник: https://projectreactor.io/docs/core/release/reference/debugging.html#_logging_a_sequence

> The `log()` operator can do just that. Chained inside a sequence, it peeks at every event of the `Flux` or `Mono` upstream of it (including `onNext`, `onError`, and `onComplete` as well as subscriptions, cancellations, and requests).

RU:

> Это умеет оператор `log()`. Вставленный в последовательность, он видит каждое событие `Flux` или `Mono` выше себя (включая `onNext`, `onError` и `onComplete`, а также подписки, отмены и запросы).

**Правило:** непонятно, кто сколько запросил и кто отменил, — поставить `log()`.

---

## Вопросы для интервью

1. Чем `doOnNext` отличается от `map`? Для чего нужны `doOn*`?
2. Какими тремя способами может закончиться поток? Какой из них не увидят `doOnComplete` и `doOnError`?
3. Когда срабатывает `doFinally` — до или после того, как подписчик получил сигнал?
4. Почему `try-with-resources` не закрывает файл правильно, если метод возвращает `Flux`?
5. Чем `Flux.using` отличается от `usingWhen`? Когда нужен второй?
6. Что будет с незавершённой загрузкой при отмене, если в `usingWhen` не передать `asyncCancel`?
7. Почему стек исключения в Reactor часто не показывает вашу строку кода?
8. Сравните `checkpoint("…")`, `checkpoint()` и `Hooks.onOperatorDebug()` по цене и пользе.
9. Что покажет `log()`, поставленный перед `take(1)`?

---

**Практика:** [12-lifecycle-debug-tasks.md](12-lifecycle-debug-tasks.md)
