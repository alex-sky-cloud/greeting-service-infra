# StepVerifier: тестирование Mono/Flux и виртуальное время

## Оглавление

1. [Что такое StepVerifier](#1-что-такое-stepverifier)
2. [Базовый пример](#2-базовый-пример)
3. [Проверка ошибок](#3-проверка-ошибок)
4. [Проблема с реальными задержками в тестах](#4-проблема-с-реальными-задержками-в-тестах)
5. [StepVerifier.withVirtualTime](#5-stepverifierwithvirtualtime)
6. [Ключевой нюанс: почему Publisher нужно создавать внутри лямбды](#6-ключевой-нюанс-почему-publisher-нужно-создавать-внутри-лямбды)
7. [thenAwait vs expectNoEvent](#7-thenawait-vs-expectnoevent)
8. [Частые ошибки](#8-частые-ошибки)

---

## 1. Что такое StepVerifier

`StepVerifier` — это тестовая утилита из модуля `reactor-test`, которая подписывается на существующий `Mono`/`Flux` и позволяет декларативно описать ожидаемую последовательность сигналов (`onNext`, `onComplete`, `onError`).

```java
StepVerifier.create(someFlux)
    .expectNext("a")
    .expectNext("b")
    .expectComplete()
    .verify(); // без verify() подписка не произойдёт и ничего не будет проверено
```

Важно: `verify()` (или один из его "коротких" вариантов вроде `verifyComplete()`, `verifyError()`) обязателен — именно он запускает подписку и блокирует текущий поток, ожидая завершения проверки.

---

## 2. Базовый пример

```java
Flux<Integer> numbers = Flux.just(1, 2, 3);

StepVerifier.create(numbers)
    .expectNext(1)
    .expectNext(2)
    .expectNext(3)
    .expectComplete()
    .verify();
```

Более компактно — через `verifyComplete()`, объединяющий финальное ожидание и запуск:

```java
StepVerifier.create(numbers)
    .expectNext(1, 2, 3) // можно передать несколько значений сразу
    .verifyComplete();
```

---

## 3. Проверка ошибок

```java
Mono<String> failing = Mono.error(new IllegalArgumentException("bad input"));

StepVerifier.create(failing)
    .expectError(IllegalArgumentException.class)
    .verify();

// либо более точно, с проверкой сообщения:
StepVerifier.create(failing)
    .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
        && ex.getMessage().equals("bad input"))
    .verify();
```

---

## 4. Проблема с реальными задержками в тестах

Если тестируемый код содержит `delayElement`, `Mono.delay`, `Flux.interval` — обычный `StepVerifier.create(...)` заставит тест реально ждать это время:

```java
Mono<String> slow = Mono.just("data").delayElement(Duration.ofMinutes(10));

StepVerifier.create(slow)
    .expectNext("data")
    .verifyComplete(); // тест будет выполняться 10 РЕАЛЬНЫХ минут
```

Это делает тесты медленными и непрактичными — нужен способ "перематывать" время без реального ожидания.

---

## 5. StepVerifier.withVirtualTime

`withVirtualTime` подменяет реальные `Schedulers` Reactor'а на единый `VirtualTimeScheduler` с управляемыми виртуальными часами:

```java
StepVerifier.withVirtualTime(() ->
        Mono.just("data").delayElement(Duration.ofMinutes(10))
    )
    .expectSubscription()
    .thenAwait(Duration.ofMinutes(10)) // "прокручиваем" время мгновенно, без реального ожидания
    .expectNext("data")
    .verifyComplete();
```

Тест с `thenAwait(Duration.ofMinutes(10))` завершится за миллисекунды, а не за 10 минут — потому что `thenAwait` просто продвигает виртуальные часы вперёд, а не блокирует поток на реальное время.

---

## 6. Ключевой нюанс: почему Publisher нужно создавать внутри лямбды

Это самая частая причина, по которой `withVirtualTime` "не работает" на практике.

`withVirtualTime` принимает не готовый `Mono`/`Flux`, а `Supplier<Publisher<T>>` — то есть функцию, которая СОЗДАЁТ Publisher по требованию, а не сам Publisher:

```java
StepVerifier.withVirtualTime(() -> Mono.just("data").delayElement(Duration.ofMinutes(10)))
//                            ^^^^^^ это Supplier — лямбда без аргументов, возвращающая Mono
```

**Почему это критично.** Официальная документация Reactor объясняет:

"This virtual time feature plugs in a custom Scheduler in Reactor's Schedulers factory. Since these timed operators usually use the default Schedulers.parallel() scheduler, replacing it with a VirtualTimeScheduler does the trick. However, an important prerequisite is that the operator be instantiated after the virtual time scheduler has been activated."

Перевод: "Функция виртуального времени подставляет специальный Scheduler в фабрику Schedulers Reactor'а. Поскольку операторы, работающие с временем, обычно используют стандартный Schedulers.parallel(), замена его на VirtualTimeScheduler решает задачу. Однако важное условие — оператор должен быть создан ПОСЛЕ того, как виртуальный Scheduler уже активирован."

Механика по шагам:

1. `withVirtualTime` сначала подменяет глобальную фабрику `Schedulers` — все стандартные шедулеры (`parallel()`, `single()` и т.д.) заменяются на один `VirtualTimeScheduler`.
2. Только ПОСЛЕ этой подмены вызывается переданная лямбда (`Supplier`), которая создаёт сам `Mono`/`Flux`.
3. Оператор `delayElement` внутри создаваемого `Mono` при инициализации "захватывает" тот `Scheduler`, который на данный момент является активным по умолчанию — то есть уже подменённый `VirtualTimeScheduler`.

Если же `Mono` создать заранее (до вызова `withVirtualTime`), оператор `delayElement` захватит РЕАЛЬНЫЙ `Schedulers.parallel()`, который действовал в момент создания — и последующая подмена фабрики уже не повлияет на уже созданный объект.

```java
// НЕПРАВИЛЬНО: Mono создан заранее, delayElement захватил реальный Schedulers.parallel()
Mono<String> slow = Mono.just("data").delayElement(Duration.ofMinutes(10));

StepVerifier.withVirtualTime(() -> slow) // подмена schedulers происходит здесь, но уже поздно
    .expectSubscription()
    .thenAwait(Duration.ofMinutes(10))
    .expectNext("data")
    .verifyComplete(); // тест будет висеть реальные 10 минут, virtual time не сработает
```

```java
// ПРАВИЛЬНО: Mono создаётся ВНУТРИ лямбды, delayElement захватывает уже подменённый VirtualTimeScheduler
StepVerifier.withVirtualTime(() ->
        Mono.just("data").delayElement(Duration.ofMinutes(10)) // создание происходит здесь, после подмены
    )
    .expectSubscription()
    .thenAwait(Duration.ofMinutes(10))
    .expectNext("data")
    .verifyComplete(); // выполняется мгновенно
```

Есть и более тонкий случай: если внутри цепочки встречается `subscribeOn(Schedulers.single())` или похожий явный шедулер (не через фабрику по умолчанию), виртуальное время тоже может не подхватиться корректно — потому что подмена работает через фабрику `Schedulers`, а явно захваченные экземпляры шедулеров могут вести себя иначе в зависимости от версии Reactor.

---

## 7. thenAwait vs expectNoEvent

Оба метода продвигают виртуальное время вперёд, но с разной строгостью:

| Метод | Поведение |
|---|---|
| `thenAwait(Duration)` | Просто продвигает часы на указанное время, ничего не проверяя |
| `expectNoEvent(Duration)` | Продвигает часы и дополнительно проверяет, что за это время НЕ пришло никаких сигналов (иначе тест падает) |

```java
StepVerifier.withVirtualTime(() -> Mono.delay(Duration.ofHours(3)))
    .expectSubscription()
    .expectNoEvent(Duration.ofHours(2)) // явно проверяем, что за 2 часа ничего не произошло
    .thenAwait(Duration.ofHours(1))     // довираем оставшийся час
    .expectNextCount(1)
    .expectComplete()
    .verify();
```

Нюанс: сразу после `.withVirtualTime()`, если планируется использовать `expectNoEvent`, обычно нужно поставить `expectSubscription()` — поскольку сам сигнал подписки формально считается "событием", и `expectNoEvent` без этого шага может упасть даже без продвижения времени.

---

## 8. Частые ошибки

- Забыть вызвать `verify()`/`verifyComplete()` — тест "пройдёт" без единой реальной проверки, потому что подписка вообще не произойдёт.
- Создать `Mono`/`Flux` с time-based оператором ДО передачи в `withVirtualTime` — виртуальное время не подключится, тест будет ждать реальное время.
- Использовать `expectNoEvent` без предварительного `expectSubscription()` — можно столкнуться с ложным падением теста из-за самого сигнала подписки.
- Смешивать в одной цепочке явные `Schedulers.single()`/`Schedulers.parallel()` с ожиданием, что `withVirtualTime` их автоматически подменит — в некоторых случаях это работает не полностью, стоит проверять на конкретной версии Reactor.
