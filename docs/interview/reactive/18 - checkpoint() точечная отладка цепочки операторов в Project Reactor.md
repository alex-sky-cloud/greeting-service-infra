# checkpoint() точечная отладка цепочки операторов в Project Reactor

## Содержание
1. [Проблема: бесполезный стектрейс](#1-проблема-бесполезный-стектрейс)
2. [Что делает checkpoint()](#2-что-делает-checkpoint)
3. [Три режима использования](#3-три-режима-использования)
4. [Бизнес-кейс 1: обработка заказов](#4-бизнес-кейс-1-обработка-заказов)
5. [Бизнес-кейс 2: агрегация данных из нескольких сервисов](#5-бизнес-кейс-2-агрегация-данных-из-нескольких-сервисов)
6. [checkpoint() vs Hooks.onOperatorDebug()](#6-checkpoint-vs-hooksonoperatordebug)

## 1. Проблема: бесполезный стектрейс

Обычный стектрейс ошибки в реактивной цепочке показывает только внутренние классы Reactor, не место в бизнес-коде, где цепочка была собрана:

```
reactor.core.Exceptions$ErrorCallbackNotImplemented: java.lang.ArithmeticException: / by zero
Caused by: java.lang.ArithmeticException: / by zero
	at com.example.OrderService.lambda$processOrder$2(OrderService.java:45)
	at reactor.core.publisher.FluxMap$MapSubscriber.onNext(FluxMap.java:96)
	at reactor.core.publisher.FluxFlatMap$FlatMapMain.onNext(FluxFlatMap.java:154)
	at reactor.core.publisher.FluxSubscribeOn$SubscribeOnSubscriber.run(FluxSubscribeOn.java:189)
	at reactor.core.scheduler.WorkerTask.call(WorkerTask.java:84)
	...
```

Если в приложении десятки похожих цепочек `map`/`flatMap`, понять, **какая именно** цепочка сломалась, невозможно без дополнительной информации.

## 2. Что делает checkpoint()

`checkpoint()` встраивает в исключение (как suppressed exception) информацию о месте сборки (assembly) конкретной цепочки — либо текстовую метку, либо полный stack trace на момент создания оператора.

## 3. Три режима использования

```java
.checkpoint()                          // полный assembly stack trace, дорогая операция
.checkpoint("orderProcessing")         // light: только текстовая метка, почти бесплатно
.checkpoint("orderProcessing", true)   // текстовая метка + полный stack trace
```

## 4. Бизнес-кейс 1: обработка заказов

Сервис обрабатывает заказы, вычисляя скидку. При баге с делением на ноль в лямбде хочется сразу понять, что упало именно в блоке расчёта скидки, а не где-то в middleware.

```java
public Mono<Order> processOrder(Order order) {
    return orderRepository.findById(order.getId())
        .map(o -> applyDiscount(o))
        .checkpoint("applyDiscount stage")
        .flatMap(paymentService::charge)
        .checkpoint("paymentService.charge stage");
}
```

**Стектрейс без checkpoint():**

```
reactor.core.Exceptions$ErrorCallbackNotImplemented: java.lang.ArithmeticException: / by zero
	at reactor.core.publisher.FluxMap$MapSubscriber.onNext(FluxMap.java:96)
	at reactor.core.publisher.FluxFlatMap$FlatMapMain.onNext(FluxFlatMap.java:154)
	...
```

Непонятно, в каком именно `map` или `flatMap` из десятков в проекте произошла ошибка.

**Стектрейс с checkpoint("applyDiscount stage"):**

```
reactor.core.Exceptions$ErrorCallbackNotImplemented: java.lang.ArithmeticException: / by zero
	at reactor.core.publisher.FluxMap$MapSubscriber.onNext(FluxMap.java:96)
	...
	Suppressed: reactor.core.publisher.FluxOnAssembly$OnAssemblyException:
Assembly trace from producer [reactor.core.publisher.MonoMap] :
	reactor.core.publisher.Mono.checkpoint(Mono.java:2153)
	com.example.OrderService.processOrder(OrderService.java:22)
Error has been observed at the following site(s):
	*__checkpoint ⇢ applyDiscount stage
	*__checkpoint ⇢ paymentService.charge stage
```

Теперь сразу видно: ошибка произошла до `paymentService.charge stage` и связана с этапом `applyDiscount stage` — разработчик мгновенно понимает, куда смотреть.

## 5. Бизнес-кейс 2: агрегация данных из нескольких сервисов

Эндпоинт собирает данные из трёх реактивных сервисов (профиль пользователя, заказы, рекомендации) через `zip`. Если один из них падает, важно быстро понять, какой именно.

```java
public Mono<UserDashboard> getDashboard(String userId) {
    Mono<Profile> profile = profileService.getProfile(userId)
        .checkpoint("profileService call");
    Mono<List<Order>> orders = orderService.getOrders(userId)
        .checkpoint("orderService call");
    Mono<List<Recommendation>> recs = recommendationService.getRecommendations(userId)
        .checkpoint("recommendationService call");

    return Mono.zip(profile, orders, recs)
        .map(tuple -> new UserDashboard(tuple.getT1(), tuple.getT2(), tuple.getT3()));
}
```

**Стектрейс при падении recommendationService:**

```
reactor.core.Exceptions$ErrorCallbackNotImplemented: java.net.ConnectException: Connection refused
	at reactor.core.publisher.MonoFlatMap$FlatMapMain.onError(MonoFlatMap.java:171)
	...
	Suppressed: reactor.core.publisher.FluxOnAssembly$OnAssemblyException:
Error has been observed at the following site(s):
	*__checkpoint ⇢ recommendationService call
	*__checkpoint ⇢ profileService call
	*__checkpoint ⇢ orderService call
```

Видно точное имя упавшего вызова без необходимости добавлять логирование в каждый сервис отдельно.

## 6. checkpoint() vs Hooks.onOperatorDebug()

| Критерий | checkpoint() | Hooks.onOperatorDebug() |
|---|---|---|
| Область действия | Точечно, только там, где вызван | Глобально, инструментирует все операторы приложения |
| Влияние на производительность | Минимальное (особенно light-режим) | Значительное — не рекомендуется для production |
| Использование в production | Безопасно на критичных участках | Только временно, для локальной отладки |
| Гибкость | Точное указание проблемного участка цепочки | Автоматическая трассировка всего, без разметки |
