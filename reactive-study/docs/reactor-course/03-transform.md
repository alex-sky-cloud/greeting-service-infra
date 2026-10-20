# Тема 03. Преобразование и агрегация

> **Статус:** теория · **Дальше:** интервью в чате, затем [практика](03-transform-tasks.md)

---

## Оглавление

- [1. map и filter](#1-map-и-filter)
- [2. take: взять первые и отменить остальное](#2-take-взять-первые-и-отменить-остальное)
- [3. reduce и collect: из Flux в Mono](#3-reduce-и-collect-из-flux-в-mono)
- [4. Пустой результат: defaultIfEmpty и switchIfEmpty](#4-пустой-результат-defaultifempty-и-switchifempty)
- [5. distinct](#5-distinct)
- [Вопросы для интервью](#вопросы-для-интервью)

---

## 1. map и filter

**Аналогия:**

На конвейере стоят два рабочих:

- первый перекрашивает каждую деталь;
- второй отбраковывает детали без маркировки.

Ни один не держит у себя склад: деталь пришла — деталь ушла дальше или в брак.

**Как это устроено в коде:**

- **Маляр** — `map(f)`: синхронно применяет функцию к каждому элементу и отправляет результат вниз.
- **Контролёр** — `filter(p)`: пропускает элемент, если предикат вернул `true`.
- **Брак** — отброшенный элемент downstream не видит.
- **Замена брака** — спрос не теряется: `filter` сам просит у upstream ещё один элемент вместо отброшенного.

```java
Flux.range(1, 6)
    .filter(i -> i % 2 == 0)          // 2, 4, 6
    .map(i -> "№" + i)                // "№2", "№4", "№6"
    .subscribe(System.out::println);
```

Javadoc `Flux.filter(Predicate)`, reactor-core 3.8:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#filter(java.util.function.Predicate)

> Evaluate each source value against the given Predicate. If the predicate test succeeds, the value is emitted. If the predicate test fails, the value is ignored and a request of 1 is made upstream.

RU:

> Проверяет каждое значение источника переданным `Predicate`. Если проверка успешна, значение выдаётся. Если проверка не прошла, значение игнорируется, и upstream получает запрос на 1 элемент.

Javadoc `Flux.map(Function)`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#map(java.util.function.Function)

> Transform the items emitted by this Flux by applying a synchronous function to each item.

RU:

> Преобразует элементы, выдаваемые этим `Flux`, применяя к каждому синхронную функцию.

**Почему не иначе:**

- если в `map` вызвать метод, который сам возвращает `Mono` (например, запрос в базу), получится `Flux<Mono<T>>`;
- запросы не выполнятся: на них никто не подпишется;
- для этого есть `flatMap` — тема 04.

**Правило:** `map` — синхронное преобразование значения; `filter` — отбор без потери спроса.

---

## 2. take: взять первые и отменить остальное

**Аналогия:**

Нужны три добровольца. Набрали трёх — объявляем: «спасибо, больше не надо».

Очередь расходится.

**Как это устроено в коде:**

`take(n)`:

1. Пропускает первые `n` элементов.
2. Отправляет вниз `onComplete` — «набрали».
3. Отправляет вверх `cancel()` — «очередь, расходитесь»: источник узнаёт, что дальше работать не нужно.

Кроме того, `take(n)` ограничивает спрос вверх числом `n`.

```java
Flux.range(1, 1000)
    .doOnRequest(n -> System.out.println("request " + n))
    .doOnCancel(() -> System.out.println("cancel"))
    .take(3)
    .subscribe(System.out::println);
// request 3
// 1
// 2
// 3
// cancel
```

**Разбор:**

- подписчик попросил «сколько угодно», а до `range` дошёл запрос только на 3;
- после третьего элемента `take` отменил подписку на источник;
- остальные 997 чисел не сгенерированы.

Javadoc `Flux.take(long)`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#take(long)

> Take only the first N values from this Flux, if available. If n is zero, the source isn't even subscribed to and the operator completes immediately upon subscription. This ensures that the total amount requested upstream is capped at n, although smaller requests can be made if the downstream makes requests < n. In any case, this operator never lets the upstream produce more elements than the cap

RU:

> Берёт только первые N значений из этого `Flux`, если они есть. Если n равно нулю, на источник даже не подписываются, и оператор завершается сразу при подписке. Это гарантирует, что общий запрос к upstream ограничен числом n, хотя запросы могут быть и меньше, если downstream запрашивает меньше n. В любом случае оператор никогда не даёт upstream произвести больше элементов, чем этот предел

**Почему не иначе:**

- отбросить «лишние» элементы после получения (`filter` по счётчику) — значит заставить источник выдать всё;
- для запроса в базу это чтение всей таблицы ради трёх строк.

**Правило:** нужно первые N — `take(N)`: он и остановит источник.

---

## 3. reduce и collect: из Flux в Mono

**Аналогия:**

Кассир весь день пробивает чеки.

Итог за смену он называет один раз — после закрытия кассы.

**Как это устроено в коде:**

- **Итог смены** — `reduce`: сворачивает все элементы в одно значение и выдаёт его как `Mono`.
- **«После закрытия кассы»** — результат появляется только **после** `onComplete` источника.
- **Начальная сумма в кассе** — `reduce(seed, (acc, x) -> ...)` начинает со значения `seed`.
- **Отчёт по чекам** — `collect(Collector)` и `collectList()`: собирают элементы в контейнер — список, карту, группировку из `java.util.stream.Collectors`.

```java
Flux.just(5, 10, 20).reduce(0, Integer::sum).subscribe(System.out::println);   // 35
Flux.just("a", "bb", "cc")
    .collect(Collectors.groupingBy(String::length, Collectors.counting()))
    .subscribe(System.out::println);                                           // {1=1, 2=2}
```

**Пустой источник** — частый вопрос на интервью. Результат запуска на Reactor 3.8.4:

| Выражение на `Flux.empty()` | Результат |
|---|---|
| `reduce(Integer::sum)` | пустой `Mono`: только `onComplete` |
| `reduce(0, Integer::sum)` | `0` |
| `collectList()` | пустой список `[]` |

Javadoc `Flux.reduce(BiFunction)`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#reduce(java.util.function.BiFunction)

> Note, BiFunction will not be invoked for a sequence with 0 or 1 elements. In case of one element's sequence, the result will be directly sent to the subscriber.

RU:

> Учтите: `BiFunction` не вызывается для последовательности из 0 или 1 элемента. Если элемент один, он сразу отправляется подписчику как результат.

Javadoc `Flux.collectList()`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#collectList()

> Collect all elements emitted by this Flux into a List that is emitted by the resulting Mono when this sequence completes, emitting the empty List if the sequence was empty.

RU:

> Собирает все элементы этого `Flux` в `List`, который итоговый `Mono` выдаёт, когда последовательность завершится; если последовательность пустая, выдаётся пустой `List`.

**Почему не иначе:**

- `reduce` и `collect` на бесконечном источнике не выдадут ничего никогда: итог появляется только после `onComplete`;
- сумма без начального значения по клиенту без заказов даст пустоту, а отчёту нужен 0.

**Правило:** нужен итог даже для пустого — `reduce` с начальным значением.

---

## 4. Пустой результат: defaultIfEmpty и switchIfEmpty

**Аналогия:**

Справочная не нашла телефон. Варианты ответа:

- готовая фраза «номер не найден»;
- переадресовать звонок в другую справочную.

**Как это устроено в коде:**

- **«Не нашли»** — пустой `Mono`: нормальный ответ «нет данных».
- **Готовая фраза** — `defaultIfEmpty(значение)`: подставляет готовое значение.
- **Переадресация** — `switchIfEmpty(другойMono)`: подписывается на другой источник.
- **Отказ** — ошибка «не найдено»: `switchIfEmpty(Mono.error(...))`.

```java
Mono<String> name = Mono.empty();
name.defaultIfEmpty("Гость").subscribe(System.out::println);                  // Гость
name.switchIfEmpty(Mono.error(() -> new IllegalStateException("нет клиента")))
    .subscribe(System.out::println, e -> System.out.println(e.getMessage()));  // нет клиента
```

Javadoc `Mono.defaultIfEmpty(T)` и `Mono.switchIfEmpty(Mono)`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#defaultIfEmpty(T)

> Provide a default single value if this mono is completed without any data

RU:

> Подставляет одно значение по умолчанию, если этот `Mono` завершился без данных.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#switchIfEmpty(reactor.core.publisher.Mono)

> Fallback to an alternative Mono if this mono is completed without data

RU:

> Переключается на альтернативный `Mono`, если этот `Mono` завершился без данных.

**Почему не иначе:**

- аргумент `switchIfEmpty` — обычное выражение Java: `switchIfEmpty(loadFromBackup())` вызовет `loadFromBackup()` при сборке, даже если основной ответ не пустой;
- если метод только собирает `Mono` — ничего страшного;
- если метод что-то делает сразу — оборачивайте его в `Mono.defer` (тема 02);
- `Mono.error(() -> new …)` с лямбдой не создаёт исключение зря.

**Правило:** пусто → значение — `defaultIfEmpty`; пусто → другой источник или ошибка — `switchIfEmpty`.

---

## 5. distinct

**Как это устроено в коде:**

- `distinct()` пропускает элемент, только если такого ещё не было **у этого подписчика**;
- для этого он хранит все увиденные значения в `HashSet`;
- на большом потоке это расход памяти.

Javadoc `Flux.distinct()`:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#distinct()

> For each Subscriber, track elements from this Flux that have been seen and filter out duplicates. The values themselves are recorded into a HashSet for distinct detection.

RU:

> Для каждого подписчика отслеживает элементы этого `Flux`, которые уже встречались, и отфильтровывает дубликаты. Сами значения записываются в `HashSet` для определения уникальности.

**Разбор:**

- порядок первых появлений сохраняется;
- `distinct` не сортирует, он только выбрасывает повторы.

**Правило:** `distinct` — по первому появлению, ценой памяти под все увиденные значения.

---

## Вопросы для интервью

1. Что делает `filter` со спросом, когда отбрасывает элемент?
2. Почему вызов, возвращающий `Mono`, внутри `map` — ошибка? Что получится по типу?
3. Что `take(3)` отправляет вверх и вниз после третьего элемента? Сколько элементов сгенерирует `Flux.range(1, 1000)`?
4. Когда `reduce` выдаёт результат? Что он выдаст на бесконечном источнике?
5. Что выдаст `reduce` на пустом потоке без начального значения и с ним?
6. Чем `defaultIfEmpty` отличается от `switchIfEmpty`? Как вернуть ошибку «не найдено»?
7. Почему `switchIfEmpty(loadFromBackup())` может выполнить лишнюю работу и как это исправить?
8. Сохраняет ли `distinct` порядок? Чем он опасен на большом потоке?

---

**Практика:** [03-transform-tasks.md](03-transform-tasks.md)
