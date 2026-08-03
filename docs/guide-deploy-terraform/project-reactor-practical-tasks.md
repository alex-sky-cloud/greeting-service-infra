# Project Reactor: 10 практических задач с решениями

> Практические упражнения по **Project Reactor** (и связке с WebClient/R2DBC) для закрепления перед собеседованием.  
> Каждая задача: **условие → решение → проверка StepVerifier → что спрашивают на интервью**.

**См. также:**

- [project-reactor-interview-guide.md](../interview/project-reactor-interview-guide.md) — теория
- [project-reactor-r2dbc-guide.md](project-reactor-r2dbc-guide.md) — R2DBC
- [project-reactor-webclient-guide.md](project-reactor-webclient-guide.md) — WebClient

---

## Оглавление

1. [map — преобразовать Flux строк](#задача-1-map--преобразовать-flux-строк)
2. [flatMap — асинхронный lookup по id](#задача-2-flatmap--асинхронный-lookup-по-id)
3. [zip — объединить два Mono](#задача-3-zip--объединить-два-mono)
4. [filter + take — топ-N после фильтра](#задача-4-filter--take--топ-n-после-фильтра)
5. [onErrorResume — fallback при ошибке](#задача-5-onerrorresume--fallback-при-ошибке)
6. [retryWhen — повтор с backoff](#задача-6-retrywhen--повтор-с-backoff)
7. [concatMap vs flatMap — порядок важен](#задача-7-concatmap-vs-flatmap--порядок-важен)
8. [StepVerifier + virtual time — delayElements](#задача-8-stepverifier--virtual-time--delayelements)
9. [subscribeOn — вынести блокирующий код](#задача-9-subscribeon--вынести-блокирующий-код)
10. [Цепочка WebClient + обработка 404](#задача-10-цепочка-webclient--обработка-404)

---

## Подготовка (Maven)

```xml
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-core</artifactId>
</dependency>
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Задача 1. map — преобразовать Flux строк

**Условие:** Дан `Flux<String>` с именами. Верните `Flux<String>` с приветствиями `"Hello, {name}"` для каждого непустого имени (пустые строки отфильтровать **до** map).

**Решение:**

```java
public Flux<String> greet(Flux<String> names) {
    return names
        .filter(name -> !name.isBlank())
        .map(name -> "Hello, " + name);
}
```

**Проверка:**

```java
@Test
void greet() {
    StepVerifier.create(greet(Flux.just("Ann", "", "Bob")))
        .expectNext("Hello, Ann")
        .expectNext("Hello, Bob")
        .verifyComplete();
}
```

**На интервью:** *What is the difference between map and flatMap?* — здесь достаточно синхронного `map`.

---

## Задача 2. flatMap — асинхронный lookup по id

**Условие:** Есть `Flux<Long> userIds` и сервис `Mono<User> findById(Long id)` (имитация R2DBC/WebClient). Соберите `Flux<User>` для всех id. Если пользователь не найден — пропустить (empty Mono).

**Решение:**

```java
public Flux<User> loadUsers(Flux<Long> ids, UserService userService) {
    return ids.flatMap(id ->
        userService.findById(id)
            .onErrorResume(e -> Mono.empty()) // или switchIfEmpty
    );
}
```

**Проверка (mock):**

```java
UserService mock = id -> id == 1L ? Mono.just(new User(1L, "Ann"))
    : id == 2L ? Mono.empty()
    : Mono.error(new RuntimeException("DB down"));

StepVerifier.create(loadUsers(Flux.just(1L, 2L, 3L), mock))
    .expectNext(new User(1L, "Ann"))
    .verifyComplete();
```

**На интервью:** Почему не `map`? — потому что `findById` возвращает `Mono`, нужен `flatMap`.

---

## Задача 3. zip — объединить два Mono

**Условие:** Независимо загрузить `Mono<User>` и `Mono<List<Order>>`, собрать `UserOrdersDto`.

**Решение:**

```java
public Mono<UserOrdersDto> getUserOrders(Mono<User> userMono, Mono<List<Order>> ordersMono) {
    return Mono.zip(userMono, ordersMono)
        .map(tuple -> new UserOrdersDto(tuple.getT1(), tuple.getT2()));
}
```

**Проверка:**

```java
User u = new User(1L, "Ann");
List<Order> orders = List.of(new Order(10L, 1L));

StepVerifier.create(getUserOrders(Mono.just(u), Mono.just(orders)))
    .expectNextMatches(dto -> dto.user().name().equals("Ann") && dto.orders().size() == 1)
    .verifyComplete();
```

**На интервью:** `zip` ждёт **оба** источника; если один упал — вся цепочка с ошибкой (если не обработать).

---

## Задача 4. filter + take — топ-N после фильтра

**Условие:** Из `Flux<Integer>` взять первые **3** числа больше 10.

**Решение:**

```java
public Flux<Integer> topThreeAboveTen(Flux<Integer> numbers) {
    return numbers
        .filter(n -> n > 10)
        .take(3);
}
```

**Проверка:**

```java
StepVerifier.create(topThreeAboveTen(Flux.range(1, 20)))
    .expectNext(11, 12, 13)
    .verifyComplete();
```

**На интервью:** `take` отменяет upstream после N элементов (важно для backpressure и ресурсов).

---

## Задача 5. onErrorResume — fallback при ошибке

**Условие:** `Mono<String>` из внешнего API. При любой ошибке вернуть `"default"`.

**Решение:**

```java
public Mono<String> fetchWithFallback(Mono<String> upstream) {
    return upstream.onErrorResume(e -> Mono.just("default"));
}
```

**Проверка:**

```java
StepVerifier.create(fetchWithFallback(Mono.error(new RuntimeException("timeout"))))
    .expectNext("default")
    .verifyComplete();

StepVerifier.create(fetchWithFallback(Mono.just("ok")))
    .expectNext("ok")
    .verifyComplete();
```

**На интервью:** Разница `onErrorReturn` vs `onErrorResume` — второй может вернуть другой `Publisher` (например, запрос к cache).

---

## Задача 6. retryWhen — повтор с backoff

**Условие:** `Mono<String>` падает 2 раза, на 3-й успех. Использовать `Retry.fixedDelay(3, Duration.ZERO)` для теста без реальной задержки.

**Решение:**

```java
public Mono<String> retryExample(Supplier<Mono<String>> supplier) {
    return supplier.get()
        .retryWhen(Retry.fixedDelay(3, Duration.ZERO));
}
```

**Проверка:**

```java
AtomicInteger attempts = new AtomicInteger();

Mono<String> flaky = Mono.defer(() -> {
    if (attempts.incrementAndGet() < 3) {
        return Mono.error(new RuntimeException("fail"));
    }
    return Mono.just("success");
});

StepVerifier.create(retryExample(() -> flaky))
    .expectNext("success")
    .verifyComplete();

assert attempts.get() == 3;
```

**На интервью:** `retry` = **новая подписка**, не повтор того же экземпляра.

---

## Задача 7. concatMap vs flatMap — порядок важен

**Условие:** Для `Flux.just(1, 2, 3)` вызвать «медленный» `Mono` с задержкой. Нужен **сохранённый порядок** 1→2→3 в результате. Выберите оператор.

**Решение:**

```java
public Flux<Integer> orderedSquares(Flux<Integer> ids) {
    return ids.concatMap(id ->
        Mono.just(id * id).delayElement(Duration.ofMillis(id * 10L))
    );
}
// flatMap мог бы выдать 1, 4, 9 в порядке завершения: 1, 9, 4 — если задержки разные
```

**Проверка (virtual time):**

```java
StepVerifier.withVirtualTime(() -> orderedSquares(Flux.just(1, 2, 3)))
    .thenAwait(Duration.ofSeconds(1))
    .expectNext(1, 4, 9)
    .verifyComplete();
```

**На интервью:** `concatMap` — последовательно; `flatMap` — параллельно (merge inner streams).

---

## Задача 8. StepVerifier + virtual time — delayElements

**Условие:** `Flux.just("a", "b").delayElements(Duration.ofSeconds(5))` — протестировать **без** реального ожидания 5 секунд.

**Решение (тест):**

```java
@Test
void delayedFlux() {
    Flux<String> flux = Flux.just("a", "b").delayElements(Duration.ofSeconds(5));

    StepVerifier.withVirtualTime(() -> flux)
        .expectSubscription()
        .expectNoEvent(Duration.ofSeconds(4))
        .thenAwait(Duration.ofSeconds(2))
        .expectNext("a")
        .thenAwait(Duration.ofSeconds(5))
        .expectNext("b")
        .verifyComplete();
}
```

**На интервью:** *How do you test operators that use time?* — `StepVerifier.withVirtualTime`.

**Источник:** [Reactor Reference — Testing with Virtual Time](https://projectreactor.io/docs/core/release/reference/#testing-virtual-time)

> **EN:** «StepVerifier.withVirtualTime allows testing time-based operators without waiting in real time.»

> **RU:** «withVirtualTime позволяет тестировать time-операторы без реального ожидания.»

---

## Задача 9. subscribeOn — вынести блокирующий код

**Условие:** Legacy-метод `String loadFromDbSync()` блокирует поток. Оберните в `Mono` без блокировки event loop.

**Решение:**

```java
public Mono<String> loadReactive() {
    return Mono.fromCallable(this::loadFromDbSync)
        .subscribeOn(Schedulers.boundedElastic());
}
```

**Проверка:**

```java
StepVerifier.create(loadReactive())
    .expectNextMatches(s -> s.startsWith("row-"))
    .verifyComplete();
```

**На интервью:** `boundedElastic` для блокирующего I/O; **не** `parallel()`.

**Источник:** [Reactor — Schedulers](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html)

> **EN:** «boundedElastic is a better choice for I/O blocking work.»

> **RU:** «boundedElastic — выбор для блокирующего I/O.»

---

## Задача 10. Цепочка WebClient + обработка 404

**Условие:** WebClient GET `/users/{id}`. 404 → `Mono.empty()`, 5xx → пробросить как `UpstreamException`, 200 → `UserDto`.

**Решение (фрагмент сервиса):**

```java
public Mono<UserDto> fetchUser(WebClient client, Long id) {
    return client.get()
        .uri("/users/{id}", id)
        .retrieve()
        .onStatus(HttpStatus.NOT_FOUND::equals,
            resp -> Mono.empty())
        .onStatus(HttpStatusCode::is5xxServerError,
            resp -> Mono.error(new UpstreamException("User service error")))
        .bodyToMono(UserDto.class)
        .switchIfEmpty(Mono.defer(() -> {
            // onStatus 404 → empty body pipeline; при необходимости явный empty
            return Mono.empty();
        }));
}
```

**Упрощённый вариант с exception на 404:**

```java
.onStatus(HttpStatusCode::is4xxClientError,
    resp -> Mono.error(new NotFoundException(id)))
```

**Проверка:** MockWebServer + StepVerifier (см. [project-reactor-webclient-guide.md](project-reactor-webclient-guide.md)).

**На интервью:** Разница `retrieve()` vs `exchangeToMono`; почему не `block()`.

---

## Сводная таблица «задача → оператор»

| # | Оператор / приём | Навык |
|---|------------------|--------|
| 1 | `filter`, `map` | синхронное преобразование |
| 2 | `flatMap` | async 1→N merge |
| 3 | `zip` | параллельная загрузка |
| 4 | `filter`, `take` | ограничение потока |
| 5 | `onErrorResume` | fallback |
| 6 | `retryWhen` | устойчивость |
| 7 | `concatMap` | порядок vs скорость |
| 8 | virtual time | тестирование |
| 9 | `subscribeOn`, `boundedElastic` | блокирующий код |
| 10 | WebClient `onStatus` | HTTP + Reactor |

---

## Дополнительные упражнения (самостоятельно)

1. **`merge`** — объединить два `Flux` с событиями в порядке поступления.
2. **`cache()`** — cold → hot для дорогого HTTP-вызова; два подписчика — один запрос.
3. **`collectList`** — `Flux<Order>` → `Mono<List<Order>>` для JSON-массива в ответе.
4. **`timeout` + `onErrorResume`** — деградация при медленном upstream.
5. **`doOnNext` / `log()`** — отладка цепочки без изменения данных.

---

## Полезные ссылки

| Ресурс | URL |
|--------|-----|
| Reactor — Testing | https://projectreactor.io/docs/core/release/reference/#testing |
| Reactor — Which operator | https://projectreactor.io/docs/core/release/reference/#which-operator |
| StepVerifier API | https://projectreactor.io/docs/test/release/api/reactor/test/StepVerifier.html |

---

*Практикум для подготовки к собеседованиям. Решения рассчитаны на Java 17+ и Reactor 3.5+.*
