

# Паттерн: всегда используй defer для методов, которые могут иметь eager-эффекты

// Утилита для безопасного then с любым сервисом

```
static <T> Mono<T> deferThen(Mono<Void> predecessor, Supplier<Mono<T>> next) {
```

    return predecessor.then(Mono.defer(next));
    }

// Использование — чисто и безопасно:
deferThen(
ensureSufficientBalance(accountId, amount),
() -> paymentService.charge(accountId, amount)
).subscribe();

Слушай, а можешь мне рассказать вообще, как ты додумался до этого? Ну, чтоб понимать, как мне... Можно придумать. Вот такие конструкции. Вот ты. Нарисовал утилиту Defer10, потом. Ты указал Mono Void pre. Predecessor, Supplier. Mono Next. Я немножко не понимаю, как этот механизм будет работать. Можешь мне разложить по полочкам? Как вообще такие утилиты строятся, и как в данном случае эта утилита будет работать. Объясните лямбды, вот эти все...

Разложу «по полочкам» — от сигнатуры до того, что происходит в момент `subscribe()`.

***

## 1. Сигнатура метода

```java
static <T> Mono<T> deferThen(Mono<Void> predecessor, Supplier<Mono<T>> next)
```

| Часть | Что значит |
| :-- | :-- |
| `<T>` | Обобщённый тип результата — тот, который вернёт `next` |
| `Mono<Void> predecessor` | Первый этап (проверка баланса). Важно: он **уже построен** (это готовый `Mono`) |
| `Supplier<Mono<T>> next` | **Фабрика** второго этапа. Не `Mono<T>`, а `Supplier<Mono<T>>` — лямбда, которая *по требованию* создаст `Mono` |
| `Mono<T>` возвращаемое | Новая цепочка: «предекаessor → (lazy) next» |


***

## 2. Что такое `Supplier<Mono<T>>` и лямбда `() -> ...`

```java
// Тип Supplier<Mono<T>> — это функциональный интерфейс с методом Mono<T> get()
Supplier<Mono<ChargeResult>> supplier = () -> paymentService.charge(accountId, amount);

// Эквивалентно анонимному классу:
Supplier<Mono<ChargeResult>> supplier = new Supplier<>() {
    @Override public Mono<ChargeResult> get() {
        return paymentService.charge(accountId, amount);
    }
};
```

**Ключевой момент:** код внутри лямбды (`paymentService.charge(...)`) **НЕ ВЫПОЛНЯЕТСЯ** в момент создания `supplier`. Он выполнится только при вызове `supplier.get()`.

***

## 3. Что делает `Mono.defer(Supplier<Mono<T>>)`

```java
Mono.defer(() -> paymentService.charge(accountId, amount))
```

Внутри `defer` (упрощённо):

```java
public static <T> Mono<T> defer(Supplier<Mono<? extends T>> supplier) {
    return new Mono<T>() {
        @Override public void subscribe(Subscriber<? super T> actual) {
            // 1. Вызываем supplier.get() — ТОЛЬКО ЗДЕСЬ, ПРИ ПОДПИСКЕ
            Mono<? extends T> source = supplier.get();
            // 2. Подписываем реального подписчика на только что созданный source
            source.subscribe(actual);
        }
    };
}
```

**Каждый новый `subscribe()` → новый вызов `supplier.get()` → новый `Mono`.**

***

## 4. Как работает `deferThen` пошагово

```java
static <T> Mono<T> deferThen(Mono<Void> predecessor, Supplier<Mono<T>> next) {
    return predecessor.then(Mono.defer(next));
}
```


### Фаза 1: Построение цепочки (вызов `deferThen`)

```java
Mono<ChargeResult> chain = deferThen(
    ensureSufficientBalance(accountId, amount),  // predecessor — ГОТОВЫЙ Mono<Void>
    () -> paymentService.charge(accountId, amount)  // next — Supplier (лямбда, НЕ ВЫЗЫВАЕТСЯ)
);
```

**Что произошло:**

- `ensureSufficientBalance(...)` вызвался → вернул `Mono<Void>` (проверка ещё не идёт)
- Лямбда `() -> paymentService.charge(...)` **НЕ ВЫЗЫВАЛАСЬ** — просто упаковалась в `Supplier`
- `Mono.defer(next)` создал обёртку, которая *позже* вызовет лямбду
- `predecessor.then(...)` соединил их в новый `Mono<ChargeResult>`
- **Никаких побочных эффектов ещё нет.**

***

### Фаза 2: Подписка (вызов `chain.subscribe()`)

```java
chain.subscribe(result -> System.out.println("Done: " + result));
```

**Что происходит внутри Reactor:**

```
1. Подписчик подписывается на chain (результат then)
         │
         ▼
2. then подписывается на predecessor (ensureSufficientBalance)
         │
         ▼
3. ensureSufficientBalance ЗАПУСКАЕТСЯ (проверка баланса)
         │
         ├── onError(InsufficientFunds) → then пробрасывает ошибку дальше → chain завершается ошибкой
         │
         └── onComplete() → then срабатывает:
                              │
                              ▼
                       4. ВНУТРИ then: Mono.defer(next).subscribe()
                              │
                              ▼
                       5. defer вызывает next.get() → paymentService.charge(...) → создаёт Mono<ChargeResult>
                              │
                              ▼
                       6. defer подписывает исходного подписчика на этот новый Mono
                              │
                              ▼
                       7. charge ЗАПУСКАЕТСЯ (списание денег)
                              │
                              ├── onError → chain завершается ошибкой
                              └── onSuccess(result) → chain завершается с result
```


***

## 5. Почему это безопасно для eager-сервисов

```java
// Eager-сервис (побочные эффекты ВНУТРИ метода)
public Mono<ChargeResult> charge(...) {
    Connection conn = pool.acquire();  // ← побочный эффект
    log.info("CHARGING...");           // ← побочный эффект
    return Mono.fromFuture(...).doFinally(s -> pool.release(conn));
}
```

**Без `deferThen`:**

```java
ensureSufficientBalance(...).then(paymentService.charge(...))
//                            ↑
//              charge() ВЫЗЫВАЕТСЯ ЗДЕСЬ → conn acquired, log written
//              Даже если баланс не пройдёт проверку!
```

**С `deferThen`:**

```java
deferThen(
    ensureSufficientBalance(...),
    () -> paymentService.charge(...)  // ← лямбда, НЕ ВЫЗЫВАЕТСЯ
)
// charge() вызовется ТОЛЬКО в шаге 5 выше — ПОСЛЕ onComplete проверки
```


***

## 6. Общий паттерн: «Supplier вместо значения» для ленивости

| Где встречается | Сигнатура | Зачем |
| :-- | :-- | :-- |
| `Mono.defer` | `Supplier<Mono<T>>` | Отложить создание `Mono` до `subscribe` |
| `Flux.defer` | `Supplier<Flux<T>>` | То же для `Flux` |
| `Mono.fromCallable` | `Callable<T>` | Обернуть блокирующий вызов в ленивый `Mono` |
| `switchIfEmpty` (в новых версиях) | `Supplier<Mono<T>>` | Не создавать fallback `Mono`, если не понадобится |
| **Твоя утилита** | `Supplier<Mono<T>>` | Не вызывать следующий шаг до завершения предыдущего |


***

## 7. Мини-шаблон для своих утилит

```java
// Паттерн: принимай Supplier<Mono<T>> вместо Mono<T>, когда нужен контроль времени создания
public static <T> Mono<T> safeThen(Mono<Void> first, Supplier<Mono<T>> secondFactory) {
    return first.then(Mono.defer(secondFactory));
}

// Использование:
safeThen(
    checkBalance(),           // Mono<Void> — уже построен
    () -> chargeMoney()       // Supplier — лениво
);

// Если нужно передать данные из first во second — используй flatMap:
public static <U, T> Mono<T> safeFlatMap(Mono<U> first, Function<U, Mono<T>> secondFactory) {
    return first.flatMap(u -> Mono.defer(() -> secondFactory.apply(u)));
}
```


***

## Резюме: как додуматься до такой утилиты

1. **Проблема:** `a.then(b)` вычисляет `b` сразу (Java eval order).
2. **Инструмент:** `Mono.defer(() -> b)` делает `b` ленивым.
3. **Паттерн:** везде, где API просит `Mono/Flux`, а тебе нужно отложить вычисление — оборачивай в `Supplier` + `defer`.
4. **Утилита:** упаковал паттерн в метод с говорящим именем (`deferThen`, `safeThen`, `lazyThen`), чтобы не писать `then(Mono.defer(() -> ...))` каждый раз.

**Главный трюк:** `Supplier<Mono<T>>` — это «рецепт Mono», который готововится по требованию.

---

л бы задать уточнение: во-первых, почему ты назвал это.  predecessor И почему это строится через. Цепочку. То есть предестор, then, то есть ты. Вначале вызываешь предестор. А, он... ты его вызвал для того, чтобы он выполнился, и только потом в. Оператор then из-за этого ты сделал так.

`predecessor` — это просто **имя параметра** («предшественник»), чтобы подчеркнуть: этот шаг должен выполниться **до** следующего. Никакой магии в слове нет.

***

## Как строится цепочка — по порядку

```java
static <T> Mono<T> deferThen(Mono<Void> predecessor, Supplier<Mono<T>> next) {
    return predecessor.then(Mono.defer(next));
}
```


### 1. В момент **вызова** `deferThen(...)` (фаза сборки)

```java
Mono<ChargeResult> chain = deferThen(
    ensureSufficientBalance(accountId, amount),  // ① predecessor — ГОТОВЫЙ Mono<Void>
    () -> paymentService.charge(accountId, amount)  // ② next — Supplier (лямбда, НЕ ВЫЗЫВАЕТСЯ)
);
```

Что происходит:

- `ensureSufficientBalance(...)` **вызывается сразу** → возвращает `Mono<Void>` (проверка ещё не идёт, это просто «рецепт» проверки)
- Лямбда `() -> paymentService.charge(...)` **НЕ вызывается** — просто запоминается как `Supplier`
- `Mono.defer(next)` создаёт обёртку: «когда подпишутся — вызову лямбду»
- `predecessor.then(...)` соединяет их в новый `Mono<ChargeResult>`
- **Никакой работы ещё не сделано** — только собран граф.

***

### 2. В момент **подписки** `chain.subscribe(...)` (фаза выполнения)

```
subscribe()
    │
    ▼
predecessor (ensureSufficientBalance)  ←── ЗАПУСКАЕТСЯ ПЕРВЫМ
    │
    ├── onError → цепочка умирает, next НИКОГДА НЕ ВЫЗЫВАЕТСЯ
    │
    └── onComplete → then срабатывает:
                        │
                        ▼
                   Mono.defer(next).subscribe()
                        │
                        ▼
                   next.get() → paymentService.charge(...)  ←── ЗАПУСКАЕТСЯ ВТОРЫМ
                        │
                        ▼
                   подписка на результат charge
```


***

## Почему именно так: `predecessor.then(...)`

| Вариант | Что происходит |
| :-- | :-- |
| `predecessor.then(nextMono)` | `nextMono` создаётся **сразу** при сборке (проблема eager-эффектов) |
| `predecessor.then(Mono.defer(() -> nextMono))` | `nextMono` создаётся **после** `onComplete` predecessor — **безопасно** |

`then` — это оператор, который говорит: *«подпишись на второй источник только когда первый завершится успешно»*.
`predecessor` — просто удобное имя для «первого источника».

***

## Коротко

- `predecessor` = «первый этап» (уже построенный `Mono<Void>`)
- `next` = «рецепт второго этапа» (`Supplier<Mono<T>>`)
- `deferThen` = «собери цепочку так, чтобы второй этап создавался **после** успешного завершения первого»
- Имя `predecessor` — просто читаемое обозначение порядка, никакой спец-логики не несёт.

