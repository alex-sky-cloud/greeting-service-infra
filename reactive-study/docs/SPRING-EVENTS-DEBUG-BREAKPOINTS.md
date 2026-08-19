# Spring Events — точки останова (Publisher → Multicaster → Listener)

> Лаборатория к [`Observer и Listener — паттерны в Java и Spring`](../../interview/pattern/Observer%20и%20Listener%20-%20паттерны%20в%20Java%20и%20Spring.md).  
> Модуль: **reactive-study**, порт **8083**.

Минимальный пример из документации Spring реализован в пакетах:

| Роль | Класс |
|------|-------|
| Событие | `com.example.reactivestudy.event.BlockedListEvent` |
| Издатель | `com.example.reactivestudy.service.EmailService` |
| Listener | `com.example.reactivestudy.listener.BlockedListNotifier` |
| HTTP-триггер | `com.example.reactivestudy.controller.BlockedListController` |

---

## Запуск и trigger

**1. PostgreSQL модуля** (если ещё не поднят):

```bash

cd reactive-study/src/main/resources/docker-reactive-study
docker compose up -d
```

**2. Приложение** (IntelliJ или терминал):

```bash

cd reactive-study
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun --no-daemon
```

**3. Один HTTP-запрос** — публикует событие:

```bash

curl "http://localhost:8083/api/demo/events/block/spammer@example.com"
```

Ожидаемый лог listener'а:

```text
Заблокирован адрес: spammer@example.com
```

---

## Цепочка вызовов (что проверяем)

Каждый шаг помечен **#N** — номер breakpoint из таблицы ниже. Ставьте stop point **в указанный метод**.

```text
#1  BlockedListController.block(String)
  └→ #2  EmailService.blockAddress(String)
       └→ #3  BlockedListEvent.<init>(Object, String)          [опц., внутри blockAddress]
            └→ #4  ApplicationEventPublisher.publishEvent(Object)
                 └→ #5  AbstractApplicationContext.publishEvent(Object)
                      └→ #6  SimpleApplicationEventMulticaster.multicastEvent(ApplicationEvent, ResolvableType)
                           └→ #7  SimpleApplicationEventMulticaster.invokeListener(ApplicationListener, ApplicationEvent)
                                └→ ApplicationListenerMethodAdapter.onApplicationEvent(ApplicationEvent)   [прокси, BP не нужен]
                                     └→ #8  ApplicationListenerMethodAdapter.processEvent(ApplicationEvent)
                                          └→ #9  BlockedListNotifier.onBlocked(BlockedListEvent)
```

| # | Класс | Метод — сюда breakpoint |
|:--|:------|:------------------------|
| 1 | `BlockedListController` | `block(String)` |
| 2 | `EmailService` | `blockAddress(String)` |
| 3 | `BlockedListEvent` | `<init>(Object, String)` *(опц.)* |
| 4 | `ApplicationEventPublisher` | `publishEvent(Object)` |
| 5 | `AbstractApplicationContext` | `publishEvent(Object)` |
| 6 | `SimpleApplicationEventMulticaster` | `multicastEvent(ApplicationEvent, ResolvableType)` |
| 7 | `SimpleApplicationEventMulticaster` | `invokeListener(ApplicationListener, ApplicationEvent)` |
| 8 | `ApplicationListenerMethodAdapter` | `processEvent(ApplicationEvent)` |
| 9 | `BlockedListNotifier` | `onBlocked(BlockedListEvent)` |

> `ApplicationEventMulticaster` в пользовательском коде **не пишется** — это внутренний посредник между издателем и listener'ами (см. § «Пояснение к рисунку» в документе по паттернам).

---

## Точки останова (порядок срабатывания)

Ставьте breakpoint'ы **сначала в своём коде** (строки 1–4), затем в Spring Framework (5–9).  
В IntelliJ: *Attach Sources* для `spring-context-7.0.6` (Gradle подтянет при первом `./gradlew bootRun`).

| # | Слой | JAR | Класс (FQCN) | Метод | Что увидеть |
|:--|:-----|:----|:-------------|:------|:------------|
| 1 | **Ваш код** | `reactive-study` | `com.example.reactivestudy.controller.BlockedListController` | `block(String)` | HTTP-триггер до публикации |
| 2 | **Ваш код** | `reactive-study` | `com.example.reactivestudy.service.EmailService` | `blockAddress(String)` | Создание события и вызов `publishEvent` |
| 3 | **Ваш код** *(опц.)* | `reactive-study` | `com.example.reactivestudy.event.BlockedListEvent` | `<init>(Object, String)` | Момент создания `ApplicationEvent` |
| 4 | **Spring API** | `spring-context` | `org.springframework.context.ApplicationEventPublisher` | `publishEvent(Object)` | Публичный контракт издателя |
| 5 | **Spring — контекст** | `spring-context` | `org.springframework.context.support.AbstractApplicationContext` | `publishEvent(Object)` | Делегирование в multicaster |
| 6 | **Spring — посредник** | `spring-context` | `org.springframework.context.event.SimpleApplicationEventMulticaster` | `multicastEvent(ApplicationEvent, ResolvableType)` | Поиск listener'ов по типу события |
| 7 | **Spring — посредник** | `spring-context` | `org.springframework.context.event.SimpleApplicationEventMulticaster` | `invokeListener(ApplicationListener, ApplicationEvent)` | Вызов конкретного listener'а |
| 8 | **Spring — адаптер @EventListener** | `spring-context` | `org.springframework.context.event.ApplicationListenerMethodAdapter` | `processEvent(ApplicationEvent)` | Перед вызовом метода `onBlocked` |
| 9 | **Ваш код** | `reactive-study` | `com.example.reactivestudy.listener.BlockedListNotifier` | `onBlocked(BlockedListEvent)` | Финальная обработка события |

### Опционально: регистрация listener'а при старте (не HTTP)

| # | Слой | Класс | Метод | Когда |
|:--|:-----|:------|:------|:------|
| R1 | Spring | `org.springframework.context.event.EventListenerMethodProcessor` | `processBean(Object, String)` | Старт контекста — Spring находит `@EventListener` на `BlockedListNotifier` |
| R2 | Spring | `org.springframework.context.event.ApplicationListenerMethodAdapter` | `<init>(String, Class, Method)` | Создание адаптера для метода `onBlocked` |

---

## Как идти по цепочке в IDE

1. Breakpoint на **#2** `EmailService.blockAddress` — убедитесь, что `publisher` не `null` (внедрён через `ApplicationEventPublisherAware`).
2. **Step Into** на `publisher.publishEvent(...)` → попадёте в **#5** `AbstractApplicationContext`.
3. Дальше **Step Into** → **#6** `multicastEvent` → **#7** `invokeListener`.
4. В **#7** смотрите `listener` в Variables — это `ApplicationListenerMethodAdapter`, не ваш bean напрямую.
5. **Step Into** → **#8** `processEvent` → **#9** `onBlocked`.

**F7 (Step Into)** на границах Spring-классов; если IDE не заходит — включите *Force Step Into* или поставьте breakpoint на **#6** заранее.

---

## Java agent (runtime trace)

Если нужен лог `>>> ENTER fqcn#method` без пошаговой отладки — см. [`docs/java-agent-trace/AI-AGENT-JAVA-TRACE-PREPARE.md`](../../java-agent-trace/AI-AGENT-JAVA-TRACE-PREPARE.md).

**TARGETS** для `InitPathAgent.java`:

```text
com/example/reactivestudy/service/EmailService
com/example/reactivestudy/listener/BlockedListNotifier
org/springframework/context/support/AbstractApplicationContext
org/springframework/context/event/SimpleApplicationEventMulticaster
org/springframework/context/event/ApplicationListenerMethodAdapter
```

**METHODS:** `blockAddress`, `onBlocked`, `publishEvent`, `multicastEvent`, `invokeListener`, `processEvent`

**Trigger:** один `curl` после `Started ReactiveStudyApplication`.

---

## Если breakpoint не срабатывает

| Симптом | Причина | Действие |
|---------|---------|----------|
| #9 не срабатывает | Опечатка в `@EventListener` / bean не в контексте | Проверить, что `BlockedListNotifier` — `@Component` и приложение стартовало без ошибок |
| #6–8 не срабатывают | Нет sources для `spring-context` | Gradle → *Reload*, *Download Sources* для `org.springframework:spring-context` |
| Запрос не доходит до #1 | Порт 8083 занят / БД недоступна | `actuator/health`, docker на :5434 |
| Listener вызывается, но лога нет | Уровень логирования | `logging.level.com.example.reactivestudy.listener=INFO` |

---

## Итог: почему Spring пишет «Observer», а по факту цепочка другая

После прохода по breakpoint'ам (#1–#9) видно: **издатель не вызывает listener напрямую**. Между `EmailService` и `BlockedListNotifier.onBlocked` стоят четыре слоя инфраструктуры Spring, которых нет в классическом GoF Observer.

### Что показала отладка (фактическая цепочка)

| Шаг | Кто | Роль в паттерне |
|:----|:----|:----------------|
| #2 | `EmailService` | **Publisher** — только «бросает» событие |
| #5 | `AbstractApplicationContext` | **Контекст** — принимает `publishEvent`, делегирует дальше |
| #6–#7 | `SimpleApplicationEventMulticaster` | **Mediator** — хранит listener'ы, находит по типу, вызывает |
| #8 | `ApplicationListenerMethodAdapter` | **Adapter** — оборачивает `@EventListener`-метод в `ApplicationListener` |
| #9 | `BlockedListNotifier` | **Ваш код** — реакция на событие |

В пользовательском коде видны только **Publisher** (#2) и **Listener** (#9). Всё между ними — скрытая инфраструктура контекста.

### Почему Spring называет это «standard Observer design pattern»

Spring говорит об **идее**, а не о структуре классов GoF.

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events

**Цитата:**
> Event handling in the ApplicationContext is provided through the ApplicationEvent class and the ApplicationListener interface. … Essentially, this is the standard Observer design pattern.

**Перевод:**
> Обработка событий в ApplicationContext идёт через ApplicationEvent и ApplicationListener. … По сути, это стандартный паттерн Observer.

**Что общего с Observer (концепция):**

- **Push-модель** — получатель не опрашивает источник, его **уведомляют**.
- **Слабая связанность** — `EmailService` не импортирует `BlockedListNotifier` и не знает, сколько listener'ов существует.
- **Пара Subject/Observer** — есть «источник события» и «реакция на событие».

Именно это Spring и называет Observer: *«публикуешь → кто-то получает уведомление»*.

### Почему structurally это **не** классический GoF Observer

В GoF Observer **Subject сам хранит список Observer'ов и сам вызывает `update()`**:

**Источник:** https://docs.oracle.com/javase/8/docs/api/java/util/Observable.html

**Цитата:**
> It can have one or more observers. … calling the Observable's notifyObservers method causes all of its observers to be notified of the change by a call to their update method.

**Перевод:**
> У него может быть один или несколько observers. … вызов notifyObservers приводит к тому, что все observers получают update.

| Критерий | GoF Observer (`Observable` / Subject) | Spring Events (проверено breakpoint'ами) |
|----------|--------------------------------------|------------------------------------------|
| Кто хранит список получателей | **Subject** (`addObserver`) | **`ApplicationEventMulticaster`** |
| Знает ли издатель listener'ов | **Да** | **Нет** — `EmailService` знает только `publisher` |
| Кто вызывает обработчик | Subject → `observer.update()` напрямую | Multicaster → Adapter → `onBlocked()` |
| Посредник | Нет | **`ApplicationEventMulticaster`** (Mediator) |
| Адаптер для метода | Нет | **`ApplicationListenerMethodAdapter`** |

**Источник:** https://blog.beezwax.net/did-you-hear-something-observer-pattern-vs-event-listeners/

**Цитата:**
> Observers are registered to the subjects they are observing, and **a subject knows all the observers it notifies**. Listeners, on the other hand, are listening for events on a global Events instance … **Any objects that are triggering events will broadcast their events without knowing who they are broadcasting to.**

**Перевод:**
> Observer'ы регистрируются у Subject, и **Subject знает всех своих Observer'ов**. Listener'ы слушают события через глобальный экземпляр Events … **Объект, публикующий событие, не знает, кто его слушает.**

Refactoring.Guru называет Observer также **Event-Subscriber** и **Listener** — как синонимы **идеи** push-уведомления:

**Источник:** https://refactoring.guru/design-patterns/observer

**Цитата:**
> Observer — Also known as: **Event-Subscriber, Listener**.

**Перевод:**
> Observer — также известен как: **Event-Subscriber, Listener**.

Spring использует термин Observer в этом широком смысле. На собеседовании и в коде Spring Events точнее говорить **Listener + Mediator**, а не «Subject вызвал Observer».

### Mediator — недостающий кусок на схеме

`ApplicationEventMulticaster` — центральный посредник: издатель с ним не общается напрямую как со списком подписчиков, контекст передаёт событие multicaster'у, а тот решает, кого вызвать.

**Источник:** https://refactoring.guru/design-patterns/observer

**Цитата:**
> There's a popular implementation of the Mediator pattern that relies on Observer. The mediator object plays the role of publisher, and the components act as subscribers … When Mediator is implemented this way, it may look very similar to Observer.

**Перевод:**
> Есть популярная реализация Mediator на базе Observer: посредник играет роль издателя, компоненты — подписчиков. В таком виде Mediator очень похож на Observer.

Spring Events — как раз этот случай: **Observer по идее, Mediator по доставке**.

### Одна фраза — три уровня понимания

| Уровень | Формулировка |
|---------|--------------|
| **Документация Spring** | «Это Observer» — push + `ApplicationListener` получает уведомление |
| **Ваш код** | Publisher (`publishEvent`) + Listener (`@EventListener`) — без multicaster в коде |
| **Runtime (breakpoint'ы)** | Publisher → Context → **Multicaster** → **Adapter** → Listener |

> **`publishEvent()`** — то, что пишет разработчик; **`ApplicationEventMulticaster`** — то, через что Spring реально доставляет событие listener'ам.

### Практический вывод лаборатории

1. Цитата Spring **не ошибается** — она описывает **концепцию** Observer (уведомление подписчиков без жёсткой связи).
2. Цитата **не описывает структуру GoF** — в GoF Subject знает observers и сам их обходит; у Spring это делает multicaster.
3. Отладка (#6–#8) **доказывает** наличие посредника и адаптера — без них до `onBlocked` не дойти.
4. На собеседовании: *«Spring Events — push-модель Observer по идее, но архитектурно это Listener + Mediator; издатель не хранит и не вызывает listener'ы»*.

Подробнее: [`Observer и Listener — паттерны в Java и Spring`](../../interview/pattern/Observer%20и%20Listener%20-%20паттерны%20в%20Java%20и%20Spring.md), §3–§4.

---

## Источники

| Тема | Документ |
|------|----------|
| Application Events | [Spring Framework — Context Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events) |
| ApplicationListener | [Javadoc — ApplicationListener](https://docs.spring.io/spring-framework/docs/7.0.6/javadoc-api/org/springframework/context/ApplicationListener.html) |
| SimpleApplicationEventMulticaster | [Javadoc — SimpleApplicationEventMulticaster](https://docs.spring.io/spring-framework/docs/7.0.6/javadoc-api/org/springframework/context/event/SimpleApplicationEventMulticaster.html) |
| ApplicationListenerMethodAdapter | [Javadoc — ApplicationListenerMethodAdapter](https://docs.spring.io/spring-framework/docs/7.0.6/javadoc-api/org/springframework/context/event/ApplicationListenerMethodAdapter.html) |

**Утверждение:** издатель не знает listener'ов; обработка идёт через `ApplicationEvent` + `ApplicationListener`.

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events

**Цитата:**
> Event handling in the ApplicationContext is provided through the ApplicationEvent class and the ApplicationListener interface. If a bean that implements the ApplicationListener interface is deployed into the context, every time an ApplicationEvent gets published to the ApplicationContext, that bean is notified. Essentially, this is the standard Observer design pattern.

**Перевод:**
> Обработка событий в ApplicationContext идёт через ApplicationEvent и ApplicationListener. Если bean с ApplicationListener развёрнут в контексте, при каждой публикации ApplicationEvent он получает уведомление. По сути, это стандартный паттерн Observer.

**Утверждение:** `SimpleApplicationEventMulticaster` рассылает событие всем зарегистрированным listener'ам.

**Источник:** https://docs.spring.io/spring-framework/docs/7.0.6/javadoc-api/org/springframework/context/event/SimpleApplicationEventMulticaster.html

**Цитата:**
> Simple implementation of the ApplicationEventMulticaster interface. Multicasts all events to all registered listeners, leaving it up to the listeners to ignore events that they are not interested in.

**Перевод:**
> Простая реализация ApplicationEventMulticaster. Рассылает все события всем зарегистрированным listener'ам; игнорировать неинтересные события — задача самих listener'ов.
