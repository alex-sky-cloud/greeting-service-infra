# Decorator Pattern и Subscription/Context в Project Reactor

## Оглавление

- [1. Классический паттерн Decorator](#1-классический-паттерн-decorator)
- [2. Как Subscriber создаётся по цепочке операторов](#2-как-subscriber-создаётся-по-цепочке-операторов)
- [3. Как Subscription оборачивается (decorator) снизу вверх](#3-как-subscription-оборачивается-decorator-снизу-вверх)
- [4. Context и его связь с Subscriber](#4-context-и-его-связь-с-subscriber)

---

## 1. Классический паттерн Decorator

**Утверждение:**
Decorator — структурный паттерн, позволяющий динамически добавлять объекту новое поведение, оборачивая его в объект-обёртку того же интерфейса. Все обёртки следуют одному интерфейсу с исходным объектом, поэтому их можно накладывать друг на друга сколько угодно раз.

- Источник: https://refactoring.guru/design-patterns/decorator/java/example

> "Decorator is a structural pattern that allows adding new behaviors to objects dynamically by placing them inside special wrapper objects, called decorators. Using decorators you can wrap objects countless number of times since both target objects and decorators follow the same interface."

RU:

> "Декоратор — это структурный паттерн, который позволяет динамически добавлять объектам новое поведение, помещая их в объекты-обёртки, называемые декораторами. С помощью декораторов можно оборачивать объекты бесконечное число раз, так как целевые объекты и декораторы реализуют один и тот же интерфейс."

**Код (интерфейс + декоратор):**

```java
public interface DataSource {
    void writeData(String data);
    String readData();
}

/**
 * DataSourceDecorator хранит ссылку на wrappee того же интерфейса DataSource и делегирует вызовы, добавляя своё поведение поверх
 */
public abstract class DataSourceDecorator implements DataSource {
    private DataSource wrappee;

    DataSourceDecorator(DataSource source) {
        this.wrappee = source;
    }

    @Override
    public void writeData(String data) {
        wrappee.writeData(data);
    }

    @Override
    public String readData() {
        return wrappee.readData();
    }
}
```

- Источник: https://refactoring.guru/design-patterns/decorator/java/example

Ключевая идея: `DataSourceDecorator` хранит ссылку на `wrappee` того же интерфейса `DataSource` и делегирует вызовы, добавляя своё поведение поверх.

---

## 2. Как Subscriber создаётся по цепочке операторов

**Утверждение:**
Вызов `subscribe()` идёт от последнего оператора к первому (к источнику), и на каждом шаге оператор создаёт новый Subscriber, оборачивающий тот, что пришёл к нему снизу.

- Источник: https://stackoverflow.com/questions/45739200/how-to-write-operators-in-reactor-3

Каждый оператор в Reactor (`FluxMap`, `FluxFilter`, `FluxContextWrite` и т.д.) реализует `Publisher`, а при вызове `subscribe(downstreamSubscriber)` создаёт собственный внутренний класс `Subscriber` (например, `FluxMap.MapSubscriber`), который оборачивает `downstreamSubscriber` и подписывает его на исходный (upstream) `Publisher`.

---

## 3. Как Subscription оборачивается (decorator) снизу вверх

**Утверждение:**
- Объект `Subscription`, создаётся источником данных (то есть, объектом Publisher), и происходит это в момент вызова `onSubscribe`. 
  - Каждый промежуточный реактивный оператор, может обернуть полученный объект `Subscription` в свою реализацию, 
    - делегируя вызовы `request()`/`cancel()` вложенному объекту. 
  - То есть, это тот же паттерн **Decorator**, просто применённый к интерфейсу `Subscription`.

- Источник: https://github.com/reactive-streams/reactive-streams-jvm

> "Subscription represents a one-to-one lifecycle of a Subscriber subscribing to a Publisher. [...] Subscription.request(long n) and Subscription.cancel() [...] MUST only be performed by the Subscriber to whom the Subscription was given."

RU:

> "Subscription представляет собой связь один-к-одному в жизненном цикле подписки Subscriber на Publisher. [...] Методы Subscription.request(long n) и Subscription.cancel() должны вызываться только тем Subscriber-ом, которому была выдана данная Subscription."

**Код (упрощённая обёртка Subscription по аналогии с Decorator):**

```java
public interface Subscription {
    void request(long n);
    void cancel();
}

public class WrappingSubscription implements Subscription {
    private final Subscription wrapped;

    public WrappingSubscription(Subscription wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void request(long n) {
        System.out.println("Логируем request: " + n);
        wrapped.request(n);
    }

    @Override
    public void cancel() {
        wrapped.cancel();
    }
}
```

Каждый оператор **Reactor**, которому нужно изменить поведение `request`/`cancel` (например, для подсчёта, буферизации, backpressure), создаёт такую обёртку вокруг Subscription, полученной снизу, и передаёт её выше по цепочке через `onSubscribe()`.

---

## 4. Context и его связь с Subscriber

**Утверждение:**
**Context** привязан не к оператору, а к каждому **Subscriber** в цепочке. 
 - Он распространяется через механизм **Subscription** — начиная с финального **subscribe** и двигаясь вверх по цепочке. `contextWrite` объединяет переданный ContextView с Context, пришедшим снизу, создавая новый Context для всех операторов выше.

- Источник: https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "Actually, a Context is tied to each Subscriber in a chain. It uses the Subscription propagation mechanism to make itself available to each operator, starting with the final subscribe and moving up the chain. [...] contextWrite(ContextView) merges the ContextView you provide and the Context from downstream [...] resulting in a NEW Context for upstream."

RU:

> "На самом деле Context привязан к каждому Subscriber-у в цепочке. Он использует механизм распространения Subscription, чтобы стать доступным для каждого оператора, начиная с финального subscribe и двигаясь вверх по цепочке. [...] contextWrite(ContextView) объединяет переданный вами ContextView с Context, пришедшим снизу [...] в результате создаётся НОВЫЙ Context для операторов выше."

> "The reason is that the Context is associated to the Subscriber and each operator accesses the Context by requesting it from its downstream Subscriber."

RU:

> "Причина в том, что Context связан с Subscriber-ом, и каждый оператор получает доступ к Context, запрашивая его у своего downstream Subscriber-а."

**Иллюстрация (пример из документации Reactor):**

```java
String key = "message";
Mono<String> r = Mono.just("Hello")
    .flatMap(s -> Mono.deferContextual(ctx -> Mono.just(s + " " + ctx.get(key))))
    .contextWrite(ctx -> ctx.put(key, "World"));

StepVerifier.create(r)
    .expectNext("Hello World")
    .verifyComplete();
```

- Источник: https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html
