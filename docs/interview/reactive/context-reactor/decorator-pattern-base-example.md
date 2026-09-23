# Паттерн Decorator — базовый пример на Java

## Оглавление

- [1. Component — базовый интерфейс](#1-component--базовый-интерфейс)
- [2. ConcreteComponent — реальный объект](#2-concretecomponent--реальный-объект)
- [3. Decorator — абстрактный декоратор](#3-decorator--абстрактный-декоратор)
- [4. ConcreteDecoratorA — конкретная обёртка №1](#4-concretedecoratora--конкретная-обёртка-1)
- [5. ConcreteDecoratorB — конкретная обёртка №2](#5-concretedecoratorb--конкретная-обёртка-2)
- [6. Client — сборка матрёшки](#6-client--сборка-матрёшки)
- [7. Итог: как это связано с Subscription в Reactor](#7-итог-как-это-связано-с-subscription-в-reactor)

---

## 1. Component — базовый интерфейс

**Утверждение:**
В основе паттерна Decorator лежит общий интерфейс, который реализуют как исходный объект, так и все его обёртки — благодаря этому обёртки можно накладывать друг на друга сколько угодно раз.

Источник: https://refactoring.guru/design-patterns/decorator/java/example

> "Decorator is a structural pattern that allows adding new behaviors to objects dynamically by placing them inside special wrapper objects, called decorators. Using decorators you can wrap objects countless number of times since both target objects and decorators follow the same interface."

RU:

> "Декоратор — это структурный паттерн, который позволяет динамически добавлять объектам новое поведение, помещая их в объекты-обёртки, называемые декораторами. С помощью декораторов можно оборачивать объекты бесконечное число раз, так как целевые объекты и декораторы реализуют один и тот же интерфейс."

```java
public interface Component {
    String operation();
}
```

---

## 2. ConcreteComponent — реальный объект

Это исходный объект, чьё поведение мы будем дополнять. У него нет никаких обёрток — просто базовая реализация интерфейса `Component`.

```java
public class ConcreteComponent implements Component {

    @Override
    public String operation() {
        return "базовая операция";
    }
}
```

---

## 3. Decorator — абстрактный декоратор

Это ключевой класс паттерна: он реализует тот же интерфейс `Component`, но хранит внутри себя ссылку на объект, который оборачивает, и по умолчанию просто делегирует ему вызов.

Источник: https://www.baeldung.com/java-decorator-pattern

> "The decoration does not change the object itself; instead, decoration wraps the original object and provides extra behavior."

RU:

> "Декорирование не изменяет сам объект; вместо этого декорирование оборачивает исходный объект и предоставляет дополнительное поведение."

```java
public abstract class Decorator implements Component {

    protected final Component wrapped;

    protected Decorator(Component wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public String operation() {
        return wrapped.operation();
    }
}
```

---

## 4. ConcreteDecoratorA — конкретная обёртка №1

Наследует `Decorator` и добавляет собственное поведение сверху того, что вернул вложенный объект.

```java
public class ConcreteDecoratorA extends Decorator {

    public ConcreteDecoratorA(Component wrapped) {
        super(wrapped);
    }

    @Override
    public String operation() {
        return super.operation() + " + добавка A";
    }
}
```

---

## 5. ConcreteDecoratorB — конкретная обёртка №2

Ещё одна независимая обёртка — устроена точно так же, как `ConcreteDecoratorA`, но добавляет своё поведение.

```java
public class ConcreteDecoratorB extends Decorator {

    public ConcreteDecoratorB(Component wrapped) {
        super(wrapped);
    }

    @Override
    public String operation() {
        return super.operation() + " + добавка B";
    }
}
```

---

## 6. Client — сборка матрёшки

Здесь видно саму суть паттерна: объекты оборачиваются друг в друга, образуя цепочку ("матрёшку"), и вызов метода `operation()` проходит через все слои по очереди.

```java
public class Client {

    public static void main(String[] args) {
        Component base = new ConcreteComponent();
        System.out.println(base.operation());
        // базовая операция

        Component decorated = new ConcreteDecoratorB(
                new ConcreteDecoratorA(base)
        );
        System.out.println(decorated.operation());
        // базовая операция + добавка A + добавка B
    }
}
```

Источник: https://java-design-patterns.com/patterns/decorator/

> "You can start with a plain coffee object, then wrap it with a milk decorator, followed by a sugar decorator, and finally a whipped cream decorator."

RU:

> "Можно начать с обычного объекта кофе, затем обернуть его декоратором молока, следом декоратором сахара и, наконец, декоратором взбитых сливок."

Здесь `decorated` — это объект `ConcreteDecoratorB`, у которого внутри поле `wrapped` ссылается на объект `ConcreteDecoratorA`, а у того внутри поле `wrapped` ссылается на исходный `ConcreteComponent`. Именно так строится "матрёшка" — каждый слой хранит ссылку на слой ниже и делегирует ему вызов, добавляя своё поведение.

---

## 7. Итог: как это связано с Subscription в Reactor

Та же самая структура применяется в Project Reactor к интерфейсу `Subscription`: вместо `Component` — `Subscription` с методами `request()`/`cancel()`, а каждый оператор в цепочке создаёт свой аналог `ConcreteDecoratorA/B`, который оборачивает `Subscription`, полученную снизу по цепочке, и делегирует ей вызовы, добавляя собственную логику (подсчёт запросов, отмену, backpressure).

Источник: https://github.com/reactive-streams/reactive-streams-jvm

> "Subscription represents a one-to-one lifecycle of a Subscriber subscribing to a Publisher."

RU:

> "Subscription представляет собой связь один-к-одному в жизненном цикле подписки Subscriber на Publisher."
