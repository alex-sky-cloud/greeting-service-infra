# Observer и Listener: паттерны в Java и Spring

> Отдельное руководство к [project-reactor-interview-guide.md](../project-reactor-interview-guide.md), §2.  
> **Формат:** схема → кто есть кто → как выглядит код → источник → цитата EN/RU.

**Перегенерация PNG:** `python docs/Images-docs/gen_reactor_diagrams.py`.

---

## Оглавление

0. [Коротко: что сказать на собеседовании](#interview-summary)
    - [Пояснение к рисунку: почему на схеме есть `ApplicationEventMulticaster`, а в коде его нет](#figure-explanation)
    - [Кто заполняет реестр listeners и как multicaster находит нужных](#multicaster-registry)
1. [Observer в Java — `Observable` / `Observer`](#1-observer-в-java--observable--observer)
2. [Listener в Spring — `ApplicationListener` / `@EventListener`](#2-слушатель-в-spring--applicationlistener--eventlistener)
    - [Пример: `ApplicationReadyEvent`](#application-ready-event)
    - [Любой объект как event](#event-any-object)
    - [Какой объект лежит в поле publisher](#publisher-field)
    - [Публикация event в WebFlux](#event-webflux)
3. [Паттерн Mediator — что это и при чём тут Spring](#3-паттерн-mediator--что-это-и-при-чём-тут-spring)
4. [Observer, Listener, Mediator — три синонима или три разных паттерна?](#4-observer-listener-mediator--три-синонима-или-три-разных-паттерна)
5. [GoF-Observer в Spring без `ApplicationEventPublisher`](#5-gof-observer-в-spring-без-applicationeventpublisher)
6. [Observer и Listener — сравнительная таблица](#6-observer-и-listener--сравнительная-таблица)

---

<a id="interview-summary"></a>

## 0. Коротко: что сказать на собеседовании

**Классический Observer (GoF).** 

**Subject** (наблюдаемый объект) хранит список наблюдателей, даёт операции `attach` / `detach` для подписки и отписки и при изменении состояния сам оповещает каждого наблюдателя. 
**Subject** напрямую знает всех своих подписчиков.

**Spring Events.** Между publisher и подписчиками стоит **посредник** — `ApplicationEventMulticaster`:

- Класс, который публикует event, хранит `ApplicationEventPublisher` и вызывает у него `publishEvent`. Этот `ApplicationEventPublisher` один на всё приложение: его получает каждый такой класс. 
  - Интерфейс реализует `ApplicationContext`, поэтому объект в поле — `ApplicationContext`. Класс, который публикует **event**, не знает, какие **listeners** его получат.
- **ApplicationContext** передаёт event в `ApplicationEventMulticaster` (по умолчанию это `SimpleApplicationEventMulticaster`).
- **Multicaster** хранит реестр **listener**'ов, выбирает подходящих по типу **event** и вызывает их.
- **Listener** — это **bean**, реализующий `ApplicationListener`, или метод с аннотацией `@EventListener`.

**Синхронно или асинхронно.**

**По умолчанию — синхронно.** 
- **Listener** выполняется в том же потоке, в котором **publisher** вызвал `publishEvent()`. Для **publisher** это выглядит как обычный вызов метода: управление вернётся к нему **только после того**, как все listener'ы закончат работу.

_Пример_: `OrderService.createOrder()` в транзакции сохраняет заказ и публикует `OrderCreatedEvent`, а **listener** на этот **event** отправляет клиенту письмо. 

Что из этого следует:

- **Listener** работает внутри **той же транзакции**, что и **publisher.** Если **listener** тоже пишет в базу (например, сохраняет запись **аудита**), эта запись сохранится или откатится вместе с заказом. Это полезно, когда данные должны быть согласованы.
   - `Медленный` **listener** задерживает **publisher.** Например, если письмо отправляется 3 секунды, `createOrder()` завершится на 3 секунды позже, и пользователь дольше ждёт ответа.
  
- **Исключение** (возникновение ошибки в момент обработки события) в listener'е прерывает **publisher.** 
  - **Исключение** долетает до `createOrder()`, и если его не перехватить, транзакция откатывается: из-за ошибки при отправке письма не сохранится и сам заказ.

**createOrder()** сохраняет заказ и в этом же вызове вызывает **publishEvent**.

**Listener** по умолчанию выполняется в этом же вызове. Если при отправке письма, **listener** бросает **exception**, оно выходит из **publishEvent** в **createOrder()**. 

Если **createOrder()** не перехватывает данный **exception** и метод помечен **@Transactional**, Spring откатывает транзакцию: заказ в базе не остаётся.

Это верно, пока **listener** синхронный. Если на нём стоит **@Async**, исключение в **createOrder()** не приходит, и заказ остаётся сохранённым.

-----

**Асинхронно — через `@Async`.** Если пометить метод listener'а аннотацией `@Async` и включить `@EnableAsync` в конфигурации, Spring выполнит listener в отдельном потоке из пула. `publishEvent()` вернёт управление сразу, не дожидаясь listener'а. Так делают для действий, которые не должны задерживать основную операцию: отправка писем, обновление кэша, запись аудита. Чем за это платят:
- **Listener работает вне транзакции publisher.** Если ему нужна транзакция, он открывает свою.
- **Publisher не узнает об ошибке listener'а.** Исключение возникает в другом потоке и до `createOrder()` не доходит: заказ сохранится, даже если письмо не ушло. Поэтому ошибку нужно обработать или записать в лог в самом listener'е.

> «По умолчанию events Spring синхронные: listener выполняется в потоке publisher и внутри его транзакции. Поэтому медленный listener задерживает publisher, а его исключение может откатить всю операцию. Если listener не должен влиять на основную операцию, я помечаю его `@Async` — он выполняется в отдельном потоке.»

**Частые заблуждения.**

- **«Event публикует multicaster».** Нет. Publisher — тот, кто вызывает `publishEvent()`, а multicaster только рассылает event подходящим listeners. Неформально его можно назвать «шиной events Spring» (event bus), но официального термина Spring Bus нет: в Spring Framework он называется event multicaster.
- **«Multicaster подписывается на publisher».** Нет. `ApplicationContext` сам вызывает у multicaster метод `multicastEvent(...)` и передаёт ему event.
- **«Multicaster сам собирает реестр listeners».** Нет. Реестр хранится внутри multicaster, но заполняет его `ApplicationContext`: `registerListeners()`, `ApplicationListenerDetector` и `EventListenerMethodProcessor` (подробно — [«Кто заполняет реестр listeners»](#multicaster-registry)).
- **«Multicaster хранит список publishers и список типов events».** Нет. Он хранит только listeners. Типа event в реестре нет: при `publishEvent(...)` multicaster читает тип у объекта event и по нему оставляет подходящих listeners.
- **«Класс, который публикует event, сам реализует `ApplicationEventPublisher`».** Нет. Интерфейс реализует `ApplicationContext`. Класс только хранит этот общий `ApplicationEventPublisher` и вызывает у него `publishEvent`. Подробно — [«Какой объект лежит в поле publisher»](#publisher-field).
- **«Event должен наследовать `ApplicationEvent`».** Нет. С Spring 4.2 `publishEvent` принимает любой объект, а метод с `@EventListener` принимает этот объект параметром. В `implements ApplicationListener<OrderCreated>` такой класс не подставить: параметр типа должен быть наследником `ApplicationEvent`. Подробно — [«Любой объект как event»](#event-any-object).
- **«В WebFlux listener выполняется в другом потоке».** Нет. `publishEvent` и в WebFlux вызывает listeners в том потоке, который его вызвал. Подробно — [«Публикация event в WebFlux»](#event-webflux).
- **«Достаточно написать класс с `implements ApplicationListener`».** Нет. Класс должен быть bean в контексте (например, с `@Component`), иначе Spring его не зарегистрирует и event до него не дойдёт.

**Какой это паттерн: Observer или Publish-Subscribe?**

Коротко: оба названия верны, но на разных уровнях. **Внутри Spring** механизм устроен как Observer, а **для ваших компонентов** он работает как publish-subscribe. Ниже по шагам.

**Шаг 1. Как устроен классический Observer.**
 - Есть наблюдаемый объект (Subject), например заказ.
 - Заказ сам хранит список наблюдателей.
 - Наблюдателя подписывают на конкретный заказ вызовом `attach()`, отписывают вызовом `detach()`.
 - Когда заказ меняется, он сам обходит свой список и у каждого наблюдателя вызывает `update()`.
 - Итог: заказ знает всех своих наблюдателей, а наблюдатель знает, на какой заказ подписан.

**Шаг 2. Как устроены events Spring.**
Действия те же — хранить список, подписать, разослать, — но выполняет их не ваш сервис:
 - **Список подписчиков** хранит `ApplicationEventMulticaster` внутри `ApplicationContext`. Он один на всё приложение.
 - **Подписывает listeners сам Spring** при создании bean, а отписывает при его уничтожении. Ваш код только объявляет listener через `implements ApplicationListener` или `@EventListener`.
 - **Рассылает тоже multicaster.** Ваш сервис вызывает `publishEvent()`, контекст передаёт event multicaster'у, а тот находит listeners по **типу event** и вызывает их.
 - Итог: сервис-publisher не знает listeners, listeners не знают publisher. Их связывает только тип event.

**Шаг 3. Сравнение.**

| Действие | Классический Observer | Spring Events |
|----------|-----------------------|----------------|
| Кто хранит список подписчиков | Сам наблюдаемый объект | `ApplicationEventMulticaster`, один на контекст |
| Кто подписывает | Код приложения вызывает `attach()` | Spring при создании bean |
| На что подписываются | На конкретный объект | На тип event (`BlockedListEvent`) |
| Кто рассылает | Сам наблюдаемый объект | Multicaster |
| Знают ли publisher и подписчики друг о друге | Да | Нет |

**Шаг 4. Почему Spring называет это Observer.**
Если считать наблюдаемым объектом не ваш сервис, а контекст вместе с multicaster'ом, все роли Observer на месте: есть список, подписка, отписка и рассылка. Listener — это наблюдатель, а его `onApplicationEvent()` — это `update()`. Поэтому документация пишет «essentially the standard Observer design pattern» — «по сути это стандартный Observer».

**Шаг 5. Почему это одновременно Publish-Subscribe.**
Посмотрим на тот же механизм глазами ваших компонентов. Сервис-publisher отдаёт event в общий канал, listeners получают events нужного типа, и друг о друге они ничего не знают. Именно так в книге _Enterprise Integration Patterns_ описан **Publish-Subscribe Channel**.

**Итог: два уровня, два названия.**
 - **Уровень реализации — Observer.** У контекста с multicaster'ом есть список listeners, подписка, отписка и рассылка.
 - **Уровень взаимодействия компонентов — Publish-Subscribe.** Ваш сервис и listeners связаны только типом event и не знают друг о друге.

Поэтому выбирать одно из двух названий не нужно. Путаница возникает, когда Subject'ом считают ваш сервис-publisher: у него действительно нет ни списка подписчиков, ни рассылки, и кажется, что Observer здесь нет. Но Subject в Spring — это контекст вместе с multicaster'ом (шаг 4).

> Технически Spring подписывает listeners двумя компонентами: класс с `implements ApplicationListener` регистрирует `ApplicationListenerDetector`, а метод с `@EventListener` — `EventListenerMethodProcessor`.

_Mediator_ («Посредник») — паттерн, при котором объекты взаимодействуют не напрямую, а через отдельный объект, управляющий их взаимодействием.

**Почему Multicater не является классическим паттерном Mediator:** 
 - **Mediator по GoF** — это посредник, который содержит логику взаимодействия участников: 
   - он решает, кто и как должен отреагировать на действие другого участника. 
 - **Multicaster** такой логики не содержит: он ничего не решает, а **доставляет** event всем listeners подходящего типа. 

 - Сходство только в том, что в обоих случаях участники общаются ч**ерез промежуточный объект** и не ссылаются друг на друга. 
 - Поэтому **multicaster** — канал доставки, а не координатор (подробно — §3).

---

Вы говорите, что мультикастер «не содержит логики маршрутизации». Это **не совсем так**: он выбирает listeners по типу event (`supportsEventType`), то есть простая фильтрация по подписке у него есть. 
 - Нет другого: логики, которая координирует участников между собой, а это **и отличает** _Mediator_. 
 - Так и стоит сказать на собеседовании.

**Итог для собеседования.** 
 - Можно сказать так: «Внутри мультикастер устроен как **Observer** (список listeners, рассылка), но по архитектуре Spring Events — это Publish-Subscribe: publisher и **Listeners** развязаны через канал-мультикастер, а настоящий Subject с состоянием здесь **не нужен**».

---

«При старте контекст регистрирует всех listeners в мультикастере. Когда publisher вызывает `publishEvent`, контекст передаёт event мультикастеру. Мультикастер по типу event выбирает подписанных listeners из своего реестра и вызывает каждого».

Одно уточнение: 
 - по умолчанию listeners вызываются синхронно, в том же потоке, что и publisher. Асинхронность включается отдельно, например через `@Async` или через `TaskExecutor` в мультикастере.

---

**Ответ одной фразой:**

> «Внутри events Spring устроены как Observer: `ApplicationEventMulticaster` хранит listeners и рассылает им events. Для моих компонентов это publish-subscribe: publisher и listeners не знают друг о друге и связаны только типом event. Это не Mediator: multicaster ничего не координирует, а только доставляет events.»

Источники и цитаты: Spring и EIP — §4, GoF Mediator — §3.

---

![Observer и Listener — схема](../../Images-docs/observer-vs-Listener.png)

**Как читать рисунок:** 
  - слева — **классический Observer** (Subject **сам хранит** список наблюдателей и **сам вызывает** `update()`). 
  - Справа — **events Spring** (publisher публикует event в контекст, а `ApplicationEventMulticaster` — центральный посредник — находит нужные listener'ы по типу event и вызывает их).

**ApplicationEventMulticaster** имеет методы регистрации и удаления listeners.

> **Уточнение:** Spring в официальной документации называет `ApplicationListener` реализацией «standard Observer design pattern».
> 
> Это верно **концептуально** (push-уведомление, ослабленная связанность). 
>  - Точнее, **структурно** это вариант Observer — **Publish–Subscribe** (публикация–подписка) с каналом events посередине: 
>   - publisher (Publisher) не знает listeners (**Listeners**) и не вызывает их напрямую — за это отвечает `ApplicationEventMulticaster`, который играет роль **канала events** (event channel, event bus). Паттерном Mediator он **не** является, хотя внешне на него частично похож. Подробно — в §3 и §4.


<a id="figure-explanation"></a>

# Пояснение к рисунку: почему на схеме есть `ApplicationEventMulticaster`, а в коде его нет

## В чём возникает путаница

На рисунке справа показана цепочка `Publisher → ApplicationEventMulticaster → Listener`, а в примере кода разработчик видит только `ApplicationEvent`, `ApplicationEventPublisher`, `ApplicationListener` и `@EventListener`. 
 - Из-за этого может возникнуть ощущение, что схема и код противоречат друг другу.

На самом деле противоречия нет: 
 - код показывает **публичный API Spring**, 
 - а рисунок показывает **внутреннюю архитектурную цепочку доставки events**.

---

## Что пишет разработчик в коде

В обычном Spring-коде разработчик работает только со следующими сущностями:

- `ApplicationEvent` — объект events.
- `ApplicationEventPublisher` — API для публикации event.
- `ApplicationListener<E>` или `@EventListener` — обработчик events.

Именно поэтому в минимальном примере нет явного `ApplicationEventMulticaster`: его обычно **не создают и не вызывают вручную**.

---

### Минимальный пример (доменный event — из документации Spring)

**1. Event:**

```java
/**
 * Доменный event: адрес добавлен в чёрный список.
 * <p>Расширяет {@link ApplicationEvent}: Spring автоматически
 * передаст объект всем listener'ам, подписанным на этот тип.</p>
 */
public class BlockedListEvent extends ApplicationEvent {

    private final String address;

    /**
     * @param source  объект, опубликовавший event (обычно {@code this})
     * @param address e-mail адрес, попавший в чёрный список
     */
    public BlockedListEvent(Object source, String address) {
        super(source);
        this.address = address;
    }

    /** @return заблокированный адрес */
    public String getAddress() { return address; }
}
```

**2. Публикация** (сервис реализует `ApplicationEventPublisherAware`):

```java
/**
 * Сервис рассылки.
 * <p>Реализует {@link ApplicationEventPublisherAware}, чтобы Spring
 * автоматически внедрил {@link ApplicationEventPublisher} — через него
 * сервис публикует events, <em>не зная</em>, кто на них подписан.</p>
 */
@Service
public class EmailService implements ApplicationEventPublisherAware {

    /** Внедряется Spring'ом автоматически через setApplicationEventPublisher. */
    private ApplicationEventPublisher publisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Добавляет адрес в чёрный список и публикует event.
     * <p>Publisher не вызывает listener'ы напрямую —
     * он лишь «бросает» event в контекст.</p>
     *
     * @param address адрес для блокировки
     */
    public void blockAddress(String address) {
        publisher.publishEvent(new BlockedListEvent(this, address));
    }
}
```

**3. Listener** — стиль `ApplicationListener`:

```java
/**
 * Listener, отправляющий уведомление при блокировке адреса.
 * <p>Реализует {@link ApplicationListener}: Spring найдёт этот bean
 * в контексте и вызовет {@code onApplicationEvent} при каждой
 * публикации {@link BlockedListEvent}.</p>
 */
@Component
public class BlockedListNotifier implements ApplicationListener<BlockedListEvent> {

    /**
     * Вызывается Spring'ом (через {@code ApplicationEventMulticaster}),
     * <em>не</em> вызывается publisher напрямую.
     *
     * @param event опубликованный event
     */
    @Override
    public void onApplicationEvent(BlockedListEvent event) {
        System.out.println("Заблокирован адрес: " + event.getAddress());
    }
}
```

**4. Тот же listener** — стиль `@EventListener` (рекомендуемый):

```java
/**
 * Listener в аннотационном стиле.
 * <p>{@link EventListener} заменяет реализацию интерфейса —
 * Spring сам определяет тип events по параметру метода.</p>
 */
@Component
public class BlockedListNotifier {

    /**
     * Вызывается контекстом при публикации {@link BlockedListEvent}.
     *
     * @param event опубликованный event
     */
    @EventListener
    public void onBlocked(BlockedListEvent event) {
        System.out.println("Заблокирован адрес: " + event.getAddress());
    }
}
```
---
.... для метода с `@EventListener` Spring создаёт отдельный объект-adapter, который реализует `ApplicationListener` и передаёт event в ваш метод.

... Каждый такой метод становится отдельным ApplicationListener.

**Ловушка, о которой стоит знать.** Bean с `@EventListener` **нельзя** делать **lazy**, иначе его метод **не будет зарегистрирован** как `listener`.

---

## Что происходит внутри Spring

Когда код вызывает:

```java
publisher.publishEvent(new BlockedListEvent(this, address));
```

- это не означает, что `EmailService` сам ищет нужные **listener**'ы и вызывает их. 
  - На практике **вызов** уходит внутрь `ApplicationContext`, а дальше **event** передаётся компоненту `ApplicationEventMulticaster`, который уже находит подходящие **listener**'ы и вызывает их.

```java
/**
* Сервис рассылки.
* <p>Реализует {@link ApplicationEventPublisherAware}, чтобы Spring
* автоматически внедрил {@link ApplicationEventPublisher} — через него
* сервис публикует events, <em>не зная</em>, кто на них подписан.</p>
  */
  @Service
  public class EmailService implements ApplicationEventPublisherAware {

  /** Внедряется Spring'ом автоматически через setApplicationEventPublisher. */
  private ApplicationEventPublisher publisher;

  @Override
  public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
  this.publisher = publisher;
  }

  /**
    * Добавляет адрес в чёрный список и публикует event.
    * <p>Publisher не вызывает listener'ы напрямую —
    * он лишь «бросает» event в контекст.</p>
    *
    * @param address адрес для блокировки
      */
      public void blockAddress(String address) {
      publisher.publishEvent(new BlockedListEvent(this, address));
      }
  }
```

То есть фактическая цепочка такая:

```text
EmailService
  → ApplicationEventPublisher.publishEvent(...)
    → ApplicationContext
      → ApplicationEventMulticaster
        → ApplicationListener / @EventListener
```

- Именно эту внутреннюю цепочку рисунок и показывает.

<a id="multicaster-registry"></a>

### Кто заполняет реестр listeners и как multicaster находит нужных

**Коротко.**

- **Реестр listeners.** Он хранится внутри `ApplicationEventMulticaster`. Заполняет его не сам multicaster, а `ApplicationContext` и его помощники: `registerListeners()`, `ApplicationListenerDetector`, `EventListenerMethodProcessor`.
- **Реестра publishers нет.** `ApplicationContext` не ведёт список тех, кто вызывает `publishEvent(...)`. Вызвать этот метод может любой код, которому внедрили `ApplicationEventPublisher`.
- **Списка типов events нет.** Multicaster не хранит каталог типов events. Тип он читает из объекта event в момент `publishEvent(...)`, затем перебирает реестр listeners и оставляет тех, чей параметр принимает этот тип.

Каталога типов нет, и сравнение типов при этом есть: тип не ищут в заранее составленном списке. Его берут у конкретного объекта event, который только что опубликовали.

**Шаг 1. Создание пустого канала.** В методе `AbstractApplicationContext.refresh()` контекст вызывает `initApplicationEventMulticaster()`. Если в контексте есть bean с именем `applicationEventMulticaster`, контекст берёт его, иначе создаёт `SimpleApplicationEventMulticaster`. На этом шаге реестр listeners пустой.

**Шаг 2. Регистрация bean-listeners по имени.** Сразу после этого контекст вызывает `registerListeners()`:
- передаёт в multicaster listeners, которых добавили вручную через `addApplicationListener(...)` до запуска контекста, методом `multicaster.addApplicationListener(listener)`;
- ищет имена всех bean, которые реализуют `ApplicationListener`, и передаёт каждое имя методом `multicaster.addApplicationListenerBean(beanName)`. Передаются **имена bean**, а не объекты: сами bean на этом шаге ещё не созданы;
- рассылает «ранние» events (early events), опубликованные до появления multicaster'а.

**Шаг 3. Регистрация готовых объектов listeners.** Когда контейнер создаёт bean, который реализует `ApplicationListener`, `ApplicationListenerDetector` (это `BeanPostProcessor`) добавляет готовый объект в multicaster через `addApplicationListener(...)`.

**Шаг 4. Регистрация методов с `@EventListener`.** `registerListeners()` такие методы **не** регистрирует. Это делает `EventListenerMethodProcessor` в методе `afterSingletonsInstantiated()`, то есть после создания всех singleton-bean. Для каждого метода с `@EventListener` он создаёт объект-адаптер `ApplicationListenerMethodAdapter` и добавляет его в multicaster через `context.addApplicationListener(...)`.

**Что хранит multicaster после старта.** Две коллекции: `Set<ApplicationListener<?>>` с объектами-listeners и `Set<String>` с именами bean-listeners. Плюс кэш `Map<ListenerCacheKey, CachedListenerRetriever>`, который заполняется позже, при публикации (шаг 6).

**Чего multicaster не хранит:**
- **Реестра publishers.** Публиковать event может любой код, у которого есть `ApplicationEventPublisher`; заранее его никто не регистрирует.
- **Списка типов events.** Тип multicaster читает из объекта event только в момент вызова `publishEvent(...)`. Заранее составленного списка типов у него нет.

**Шаг 5. Публикация.** Publisher вызывает `publishEvent(event)`. Если `event` не наследует `ApplicationEvent` (например, это обычный объект `OrderCreated`), контекст оборачивает его в `PayloadApplicationEvent<OrderCreated>`. Затем контекст определяет тип event (`ResolvableType`) и вызывает `multicaster.multicastEvent(event, eventType)`.

**Шаг 6. Подбор listeners по типу event.** Внутри `multicastEvent(...)` multicaster вызывает `getApplicationListeners(event, eventType)`:
- строит ключ кэша `ListenerCacheKey` из двух частей: тип event и класс источника этого event (`event.getSource().getClass()`);
- если по этому ключу в кэше уже есть готовый список listeners, сразу берёт его;
- если нет — перебирает весь реестр и для каждого listener вызывает `supportsEvent(...)`. Для обычного `ApplicationListener<OrderCreatedEvent>` тип event берётся из generic-параметра через `GenericApplicationListenerAdapter`; для метода с `@EventListener` — из параметра метода через `ApplicationListenerMethodAdapter`;
- сортирует подходящих listeners по `@Order` и кладёт список в кэш.

**Шаг 7. Вызов.** Multicaster по очереди вызывает `onApplicationEvent(event)` у каждого listener из списка: по умолчанию в потоке publisher, а если multicaster'у задан `TaskExecutor` — в потоках этого executor'а.

Когда в реестр добавляют или из него удаляют listener (`addApplicationListener`, `removeApplicationListener`), multicaster очищает кэш, и при следующей публикации список подбирается заново.

**Схема:**

```text
AbstractApplicationContext.refresh()
  ├── initApplicationEventMulticaster()   создать пустой SimpleApplicationEventMulticaster
  ├── registerListeners()                 добавить имена bean с implements ApplicationListener
  ├── finishBeanFactoryInitialization()   создать singleton-bean:
  │     ├── ApplicationListenerDetector   добавить готовые объекты listeners
  │     └── EventListenerMethodProcessor  добавить адаптеры для методов с @EventListener
  └── ...

publishEvent(new OrderCreated(42))
  → PayloadApplicationEvent<OrderCreated>
  → multicaster.multicastEvent(event, eventType)
  → getApplicationListeners: ключ (тип event, класс источника) → кэш или перебор реестра + supportsEvent
  → onApplicationEvent(...) у каждого подходящего listener
```

**Источник:** [Spring Framework, исходный код — `AbstractApplicationContext.registerListeners()`](https://github.com/spring-projects/spring-framework/blob/main/spring-context/src/main/java/org/springframework/context/support/AbstractApplicationContext.java)

> **EN:** «Add beans that implement ApplicationListener as listeners. Doesn't affect other listeners, which can be added without being beans.» / «Do not initialize FactoryBeans here: We need to leave all regular beans uninitialized to let post-processors apply to them!»

> **RU:** «Добавляет bean, реализующие ApplicationListener, как listeners. Не затрагивает других listeners, которые можно добавить, не делая их bean.» / «Не инициализировать здесь FactoryBean: все обычные bean нужно оставить неинициализированными, чтобы к ним применились post-processor'ы!»

**Источник:** [Spring Framework, исходный код — `AbstractApplicationEventMulticaster`](https://github.com/spring-projects/spring-framework/blob/main/spring-context/src/main/java/org/springframework/context/event/AbstractApplicationEventMulticaster.java)

> **EN:** «Return a Collection of ApplicationListeners matching the given event type. Non-matching listeners get excluded early.» / «Cache key for ListenerRetrievers, based on event type and source type.»

> **RU:** «Возвращает коллекцию ApplicationListener, подходящих под данный тип event. Неподходящие listeners отсеиваются заранее.» / «Ключ кэша для ListenerRetriever, основанный на типе event и типе источника.»

**Источник:** [Javadoc — `EventListenerMethodProcessor`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/event/EventListenerMethodProcessor.html)

> **EN:** «Registers EventListener methods as individual ApplicationListener instances.»

> **RU:** «Регистрирует методы с EventListener как отдельные экземпляры ApplicationListener.»

> **На собеседовании:** «Реестр listeners лежит внутри multicaster, а заполняет его `ApplicationContext`. Реестра publishers нет, и каталога типов events тоже нет. При `publishEvent(...)` multicaster читает тип у объекта event, перебирает listeners, проверяет `supportsEvent` и кэширует результат по ключу „тип event + класс источника“.»

---

## Почему `ApplicationEventMulticaster` есть на схеме

Он нужен на рисунке не потому, что разработчик обязан его писать в коде, а потому что без него трудно объяснить, 
 - **почему Spring Events структурно — это Observer в варианте Publish–Subscribe (с каналом events посередине), а не классический GoF Observer**.

В GoF Observer:

- Subject **сам хранит** список наблюдателей.
- Subject **сам вызывает** `update()` у каждого Observer.

В Spring Events:

- publisher **не хранит** список listener'ов;
- publisher **не вызывает** listener'ы напрямую;
- эту работу делает `ApplicationEventMulticaster` как центральный посредник — канал events (event bus), а не Mediator в смысле GoF.

Именно поэтому на рисунке его показывать правильно.

---

## На что опираться при чтении примера

Нужно опираться сразу на **два уровня понимания**:

### 1. Уровень прикладного кода

Если задача — писать Spring-приложение, достаточно помнить следующее:

- event создаётся как `ApplicationEvent` или обычный объект;
- event публикуется через `ApplicationEventPublisher`;
- обработка делается через `ApplicationListener` или `@EventListener`.

На этом уровне `ApplicationEventMulticaster` можно вообще не трогать.

### 2. Уровень архитектуры

Если задача — **понять паттерн**, тогда важно знать внутреннюю роль `ApplicationEventMulticaster`:

- он выступает **каналом events** (посредником-доставщиком) между _publisher_ и _listener'ами_;
- он решает, какие **listener'ы** подходят _по типу_ **events**;
- он делает реальную dispatch-**рассылку** внутри Spring.

---

## Как связать код и рисунок одной фразой

Самая удобная формулировка такая:

> В примере кода показан **внешний API**, с которым работает разработчик, а на рисунке показан **внутренний механизм доставки events** внутри Spring.

Или ещё короче:

> `publishEvent()` — это то, что пишет разработчик; `ApplicationEventMulticaster` — это то, через что Spring реально доставляет event listener'ам.

---

## Практический вывод

- Если документ ориентирован на **объяснение паттернов**, `ApplicationEventMulticaster` на схеме нужен. 
- Если документ ориентирован только на **прикладное использование API**, его можно было бы не рисовать.

Для текущего документа логичнее оставить `ApplicationEventMulticaster` на рисунке, но рядом с примером кода добавить короткое пояснение в отдельной теме:

> `ApplicationEventMulticaster` не участвует в пользовательском коде напрямую, но участвует во внутренней доставке events внутри `ApplicationContext`.

Так схема и код перестают конфликтовать: 
  - один показывает **что пишет разработчик**, другой — **как это реально работает внутри Spring**.

---

## 1. Observer в Java — `Observable` / `Observer`

### Как выглядит паттерн

| Роль в GoF | В JDK (до reactive) | Что происходит |
|------------|---------------------|----------------|
| **Subject** (наблюдаемый) | `java.util.Observable` | Хранит **набор** `Observer`. При изменении состояния вызывает `setChanged()` → `notifyObservers()`. |
| **Observer** (наблюдатель) | `java.util.Observer` | Реализует `update(Observable o, Object arg)` — его вызывают **все** подписанные наблюдатели. |
| **Подписка** | `observable.addObserver(observer)` | Subject **знает** каждого Observer в своём списке. |
| **Уведомление** | `notifyObservers(arg)` | Subject **сам обходит** список и вызывает `update()` у каждого. |

**Цепочка в одну строку:** изменили данные в Subject → `notifyObservers()` → `update()` у каждого Observer.

* GoF — это «Gang of Four», четыре автора книги “Design Patterns: Elements of Reusable Object-Oriented Software” (Gamma, Helm, Johnson, Vlissides), где формализованы классические паттерны проектирования, включая Observer

### пример

```java
import java.util.ArrayList;
import java.util.List;

/**
 * ROLE: Observer
 *
 * <p>Общий контракт для всех наблюдателей.</p>
 * <p>Любой Observer обязан реализовать метод {@link #update(String)},
 * чтобы Subject мог уведомить его об изменении состояния.</p>
 */
interface OrderObserver {

    /**
     * Вызывается Subject'ом при изменении статуса заказа.
     *
     * @param newStatus новое состояние Subject'а
     */
    void update(String newStatus);
}

/**
 * ROLE: Subject
 *
 * <p>Наблюдаемый объект из GoF Observer.</p>
 * <p><b>Главный признак Subject:</b> он сам хранит список Observer'ов
 * и сам вызывает их метод {@code update()}.</p>
 */
class OrderStatus {

    /**
     * 1. Subject ХРАНИТ список подписчиков.
     * <p>Это ключевая часть GoF Observer:
     * список наблюдателей находится внутри Subject.</p>
     */
    private final List<OrderObserver> observers = new ArrayList<>();

    /**
     * Текущее состояние Subject'а.
     */
    private String status;

    /**
     * 2. Подписка Observer на Subject.
     *
     * @param observer наблюдатель, которого нужно добавить
     */
    public void addObserver(OrderObserver observer) {
        observers.add(observer);
    }

    /**
     * 3. Отписка Observer от Subject.
     *
     * @param observer наблюдатель, которого нужно удалить
     */
    public void removeObserver(OrderObserver observer) {
        observers.remove(observer);
    }

    /**
     * 4. Изменение состояния Subject'а.
     * <p>Как только состояние изменилось, Subject сам запускает
     * механизм уведомления всех подписчиков.</p>
     *
     * @param newStatus новый статус заказа
     */
    public void setStatus(String newStatus) {
        this.status = newStatus;
        notifyObservers();
    }

    /**
     * 5. Subject САМ обходит список Observer'ов
     * и САМ вызывает {@code update()} у каждого.
     *
     * <p>Именно это и есть суть GoF Observer.</p>
     * <p>Здесь нет Spring, нет EventBus, нет Mediator,
     * нет скрытой логики в родительском классе.</p>
     */
    private void notifyObservers() {
        for (OrderObserver observer : observers) {
            observer.update(status);
        }
    }
}

/**
 * ROLE: ConcreteObserver
 *
 * <p>Конкретная реализация Observer.</p>
 * <p>Получает уведомление от Subject'а и выполняет свою реакцию.</p>
 */
class OrderLogger implements OrderObserver {

    /**
     * Реакция на изменение состояния Subject'а.
     *
     * @param newStatus новый статус заказа
     */
    @Override
    public void update(String newStatus) {
        System.out.println("OrderLogger: статус заказа = " + newStatus);
    }
}

/**
 * ROLE: ConcreteObserver
 *
 * <p>Ещё один конкретный Observer.</p>
 * <p>Показывает, что у одного Subject может быть несколько подписчиков.</p>
 */
class OrderNotifier implements OrderObserver {

    /**
     * Реакция на изменение статуса.
     *
     * @param newStatus новый статус заказа
     */
    @Override
    public void update(String newStatus) {
        System.out.println("OrderNotifier: отправляем уведомление о статусе " + newStatus);
    }
}

/**
 * ROLE: Client
 *
 * <p>Клиентский код собирает паттерн:</p>
 * <ol>
 *   <li>создаёт Subject;</li>
 *   <li>создаёт ConcreteObserver'ов;</li>
 *   <li>подписывает Observer'ов на Subject;</li>
 *   <li>меняет состояние Subject'а.</li>
 * </ol>
 */
public class OrderObserverDemo {

    public static void main(String[] args) {

        // Шаг 1. Создаём Subject
        OrderStatus subject = new OrderStatus();

        // Шаг 2. Создаём ConcreteObserver'ов
        OrderObserver logger = new OrderLogger();
        OrderObserver notifier = new OrderNotifier();

        // Шаг 3. Подписываем Observer'ов на Subject
        subject.addObserver(logger);
        subject.addObserver(notifier);

        // Шаг 4. Меняем состояние Subject'а
        // Subject сам вызовет update() у каждого подписчика
        subject.setStatus("PAID");
        subject.setStatus("SHIPPED");

        // Шаг 5. Можно отписать одного Observer'а
        subject.removeObserver(logger);

        // Теперь уведомление получит только оставшийся Observer
        subject.setStatus("DELIVERED");
    }
}
```

> `Observable` / `Observer` помечены **`@Deprecated` с Java 9** — для нового кода JDK их не рекомендует, но они остаются **каноническим примером** паттерна в Java API.

### Когда встречается сегодня

- Учебные и legacy-коды на `Observable` / `Observer`.
- **Reactor / Reactive Streams** — та же **push-идея** («источник уведомляет подписчика»), но через `Publisher` / `Subscriber` и с backpressure (§7).

**Источник:** [Oracle — `Observable` (Java SE 8)](https://docs.oracle.com/javase/8/docs/api/java/util/Observable.html) · [`Observer`](https://docs.oracle.com/javase/8/docs/api/java/util/Observer.html)

> **EN:** «This class represents an observable object … It can have one or more observers. An observer may be any object that implements interface Observer. After an observable instance changes, an application calling the Observable's notifyObservers method causes all of its observers to be notified of the change by a call to their update method.»

> **RU:** «Этот класс представляет наблюдаемый объект … У него может быть один или несколько observers. Observer — любой объект, реализующий интерфейс Observer. После изменения observable вызов notifyObservers приводит к тому, что все observers получают update.»

---

<a id="pattern-listener"></a>

<a id="2-слушатель-в-spring--applicationlistener--eventlistener"></a>

## 2. Listener в Spring — `ApplicationListener` / `@EventListener`

### Как выглядит паттерн в Spring

Здесь **не** `addObserver()` на вашем объекте. Модель такая:

| Роль | Класс / API в Spring | Что происходит |
|------|----------------------|----------------|
| **Event** | `ApplicationEvent` (или любой объект, с 4.2) | Объект-сообщение: «произошло X». |
| **Publisher** | `ApplicationEventPublisher` | Вызывает `publishEvent(event)` — **не** знает список listener'ов и **не** вызывает их напрямую. |
| **Listener** | `ApplicationListener<E>` или метод с `@EventListener` | Bean в контексте; Spring **регистрирует** его и **вызывает** при совпадении типа event. |
| **Раздача** | `ApplicationEventMulticaster` (внутри контекста) | **Канал events** (event bus): хранит listener'ы, находит подходящие по типу event и вызывает их (по умолчанию это `SimpleApplicationEventMulticaster`, вызов **синхронный**, в потоке publisher). |

**Цепочка в одну строку:** `publishEvent(event)` → `ApplicationEventMulticaster` → `onApplicationEvent(event)` / метод с `@EventListener`.

### Минимальный пример (доменный event — из документации Spring)

**1. Event:**

```java
/**
 * Доменный event: адрес добавлен в чёрный список.
 * <p>Расширяет {@link ApplicationEvent}: Spring автоматически
 * передаст объект всем listener'ам, подписанным на этот тип.</p>
 */
public class BlockedListEvent extends ApplicationEvent {

    private final String address;

    /**
     * @param source  объект, опубликовавший event (обычно {@code this})
     * @param address e-mail адрес, попавший в чёрный список
     */
    public BlockedListEvent(Object source, String address) {
        super(source);
        this.address = address;
    }

    /** @return заблокированный адрес */
    public String getAddress() { return address; }
}
```

**2. Публикация** (сервис реализует `ApplicationEventPublisherAware`):

```java
/**
 * Сервис рассылки.
 * <p>Реализует {@link ApplicationEventPublisherAware}, чтобы Spring
 * автоматически внедрил {@link ApplicationEventPublisher} — через него
 * сервис публикует events, <em>не зная</em>, кто на них подписан.</p>
 */
@Service
public class EmailService implements ApplicationEventPublisherAware {

    /** Внедряется Spring'ом автоматически через setApplicationEventPublisher. */
    private ApplicationEventPublisher publisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Добавляет адрес в чёрный список и публикует event.
     * <p>Publisher не вызывает listener'ы напрямую —
     * он лишь «бросает» event в контекст.</p>
     *
     * @param address адрес для блокировки
     */
    public void blockAddress(String address) {
        publisher.publishEvent(new BlockedListEvent(this, address));
    }
}
```

**3. Listener** — стиль `ApplicationListener`:

```java
/**
 * Listener, отправляющий уведомление при блокировке адреса.
 * <p>Реализует {@link ApplicationListener}: Spring найдёт этот bean
 * в контексте и вызовет {@code onApplicationEvent} при каждой
 * публикации {@link BlockedListEvent}.</p>
 */
@Component
public class BlockedListNotifier implements ApplicationListener<BlockedListEvent> {

    /**
     * Вызывается Spring'ом (через {@code ApplicationEventMulticaster}),
     * <em>не</em> вызывается publisher напрямую.
     *
     * @param event опубликованный event
     */
    @Override
    public void onApplicationEvent(BlockedListEvent event) {
        System.out.println("Заблокирован адрес: " + event.getAddress());
    }
}
```

**4. Тот же listener** — стиль `@EventListener` (рекомендуемый):

```java
/**
 * Listener в аннотационном стиле.
 * <p>{@link EventListener} заменяет реализацию интерфейса —
 * Spring сам определяет тип events по параметру метода.</p>
 */
@Component
public class BlockedListNotifier {

    /**
     * Вызывается контекстом при публикации {@link BlockedListEvent}.
     *
     * @param event опубликованный event
     */
    @EventListener
    public void onBlocked(BlockedListEvent event) {
        System.out.println("Заблокирован адрес: " + event.getAddress());
    }
}
```

> `ApplicationListener` **extends** `java.util.EventListener` — это **Java-интерфейс-маркер** для callback-модели (Swing, servlet events и т.д.). Spring строит на нём свой механизм events `ApplicationContext`.

**Источник:** [Spring Framework — Application Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events) · [Javadoc — `ApplicationListener`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/ApplicationListener.html)

> **EN (Reference):** «Event handling in the ApplicationContext is provided through the ApplicationEvent class and the ApplicationListener interface. If a bean that implements the ApplicationListener interface is deployed into the context, every time an ApplicationEvent gets published to the ApplicationContext, that bean is notified. Essentially, this is the standard Observer design pattern.»

> **EN (Javadoc):** «Interface to be implemented by application event listeners. Based on the standard EventListener interface for the Observer design pattern.»

> **RU:** «Обработка events в ApplicationContext идёт через ApplicationEvent и ApplicationListener. Если bean с ApplicationListener развёрнут в ApplicationContext, при каждой публикации ApplicationEvent он получает уведомление. По сути, это стандартный паттерн Observer.» / «Интерфейс listener events приложения. Основан на стандартном EventListener — интерфейсе для паттерна Observer.»

<a id="event-any-object"></a>

### Любой объект как event

Коротко.

- Аннотация на класс event не нужна. `publishEvent` с Spring 4.2 принимает любой объект, например `new OrderCreated("A-1")`.
- Listener нужен по-прежнему. Без метода `@EventListener` или без `ApplicationListener` event никто не получит.
- `implements ApplicationListener<OrderCreated>` не компилируется, если `OrderCreated` не наследует `ApplicationEvent`. Параметр типа этого интерфейса — наследник `ApplicationEvent`.

Как `ApplicationContext` выбирает listener.

1. Publisher вызывает `publisher.publishEvent(new OrderCreated("A-1"))`.
2. `ApplicationContext` видит, что `OrderCreated` не наследует `ApplicationEvent`, и оборачивает объект в `PayloadApplicationEvent<OrderCreated>`.
3. `ApplicationEventMulticaster` смотрит реестр listeners. У каждого listener записан тип, который он принимает: у метода `@EventListener` это тип параметра метода.
4. У метода `void onCreated(OrderCreated event)` записан тип `OrderCreated`. Multicaster сравнивает его с типом объекта внутри `PayloadApplicationEvent`. Типы совпадают, метод вызывается, в параметр приходит `OrderCreated`, не обёртка.
5. `ApplicationListener<BlockedListEvent>` этот вызов не получает: у него записан тип `BlockedListEvent`.

Для обычного объекта через интерфейс, а не через `@EventListener`, listener объявляют как `ApplicationListener<PayloadApplicationEvent<OrderCreated>>`. С Spring 5.3 то же самое делает `ApplicationListener.forPayload`.

```java

public class OrderCreated {

    private final String id;

    public OrderCreated(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}

@Component
public class OrderCreatedListener {

    @EventListener
    public void onCreated(OrderCreated event) {
        System.out.println(event.getId());
    }
}

```

**Источник:** [Spring Framework — Application Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events) · [Javadoc — `ApplicationListener`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/ApplicationListener.html)

> **EN:** «As of Spring 4.2, the event infrastructure … offers … the ability to publish any arbitrary event (that is, an object that does not necessarily extend from ApplicationEvent). When such an object is published, we wrap it in an event for you.»

> **EN (Javadoc):** «E - the specific ApplicationEvent subclass to listen to.»

> **RU:** «С Spring 4.2 можно публиковать любой объект, который не обязательно наследует `ApplicationEvent`. Когда такой объект публикуют, Spring оборачивает его в event.» / «E — конкретный подкласс `ApplicationEvent`, который listener принимает.»

<a id="publisher-field"></a>

### Какой объект лежит в поле publisher

`ApplicationEventPublisher` — один общий объект. `ApplicationContext` подставляет его в каждый класс, который публикует events. Класс `ApplicationContext` реализует интерфейс `ApplicationEventPublisher`, поэтому этот объект — сам `ApplicationContext`.

Класс, который публикует event, например `EmailService`, хранит `ApplicationEventPublisher` в поле. Публикация — это вызов `publishEvent` на этом поле. `EmailService` интерфейс `ApplicationEventPublisher` не реализует.

Когда `ApplicationContext` создаёт bean `EmailService`, он вызывает `setApplicationEventPublisher` и передаёт туда себя. То же самое он передаёт в `OrderService` и в любой другой такой класс: во всех полях один объект.

<a id="event-webflux"></a>

### Публикация event в WebFlux

Коротко.

- WebFlux не подменяет `ApplicationEventMulticaster`. Метод `publishEvent` остаётся методом `void`.
- Listener выполняется в том потоке, который вызвал `publishEvent`. Если вызов идёт из контроллера WebFlux, это поток event loop. Долгая работа в listener занимает этот поток.
- Если метод `@EventListener` возвращает `Mono` или `Flux`, порядок такой. `ApplicationListenerMethodAdapter` сразу вызывает `subscribe` и не ждёт, пока `Mono` завершится. Затем `publishEvent` возвращает управление в класс, который его вызвал. Этот класс продолжает работу и `Mono` не ждёт. Элементы, которые `Mono` выдаст позже, адаптер публикует как новые events.
- Если этот `Mono` позже завершается ошибкой, исключение возникает уже после возврата из `publishEvent`. Класс, который вызвал `publishEvent`, исключение не получает и не может его поймать. `ApplicationListenerMethodAdapter` в `onError` пишет в лог текст `Unexpected error occurred in asynchronous listener` и само исключение.

**Источник:** [ApplicationListenerMethodAdapter](https://github.com/spring-projects/spring-framework/blob/v6.2.11/spring-context/src/main/java/org/springframework/context/event/ApplicationListenerMethodAdapter.java)

> **EN:** «if (reactiveStreamsPresent && new ReactiveResultHandler().subscribeToPublisher(result))» / «adapter.toPublisher(result).subscribe(new EventPublicationSubscriber());» / «public void onNext(Object o) { publishEvents(o); }» / «logger.error("Unexpected error occurred in asynchronous listener", t);»

> **RU:** «Если метод listener вернул `Mono`, адаптер сразу вызывает `subscribe` и не ждёт завершения. `publishEvent` после этого возвращается в класс, который его вызвал. Если `Mono` позже завершается ошибкой, адаптер пишет исключение в лог. Класс, который вызвал `publishEvent`, это исключение не получает.»

---

<a id="application-ready-event"></a>

### Пример: `ApplicationReadyEvent`

Типичный **Spring Boot** listener — реакция на **жизненный цикл** приложения (не Reactor).

![Sequence: старт Spring Boot → ApplicationReadyEvent](../../Images-docs/reactor-seq-spring-boot-startup.png)

**Цепочка:** `SpringApplication.run()` → контекст поднят → runners → **`ApplicationReadyEvent`** → ваш `@EventListener`.

```java
/**
 * Listener жизненного цикла: выполняет действия после полного старта приложения.
 * <p>Spring Boot публикует {@link ApplicationReadyEvent} последним при запуске —
 * после того, как все {@code ApplicationRunner} и {@code CommandLineRunner}
 * уже отработали. Использовать для прогрева кешей, регистрации в service
 * discovery и т.п.</p>
 */
@Component
@Slf4j
public class AppStartupListener {

    /**
     * Вызывается контекстом, когда приложение готово обслуживать запросы.
     *
     * @param event event готовности приложения; содержит ссылку
     *              на {@link org.springframework.boot.SpringApplication}
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onAppReady(ApplicationReadyEvent event) {
        log.info("Приложение готово обслуживать запросы");
    }
}
```

| Шаг | Кто | Действие |
|-----|-----|----------|
| 1 | Spring Boot | Публикует `ApplicationReadyEvent` **после** runners |
| 2 | `ApplicationContext` | Передаёт event `ApplicationEventMulticaster` |
| 3 | Ваш bean | Метод `onAppReady` вызывается **контекстом**, не вами |

**Источник:** [Spring Boot — Application events](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.application-events-and-listeners) · [Javadoc — `ApplicationReadyEvent`](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/context/event/ApplicationReadyEvent.html)

> **EN:** «An ApplicationReadyEvent is sent after any application and command-line runners have been called.» / «Event published as late as conceivably possible to indicate that the application is ready to service requests.»

> **RU:** «ApplicationReadyEvent отправляется после выполнения всех ApplicationRunner и CommandLineRunner.» / «Event публикуется максимально поздно при старте — приложение готово обслуживать запросы.»

---

<a id="pattern-mediator"></a>

## 3. Паттерн Mediator — что это и при чём тут Spring

### Идея простыми словами

Представьте диспетчерскую вышку аэропорта. Самолёты не договариваются друг с другом напрямую — каждый говорит только с вышкой, а она решает, кому что передать. Убрать вышку — и все начнут кричать друг на друга.

Именно это и есть **Mediator (Посредник)**: объект, через который общаются все остальные. Участники **не знают** друг о друге — они знают только о посреднике.

### Определение GoF

**Источник:** Gamma E., Helm R., Johnson R., Vlissides J. *Design Patterns: Elements of Reusable Object-Oriented Software*. Addison-Wesley, 1994 — глава Mediator, раздел Intent.

> **EN:** «Define an object that encapsulates how a set of objects interact. Mediator promotes loose coupling by keeping objects from referring to each other explicitly, and it lets you vary their interaction independently.»

> **RU:** «Определите объект, который инкапсулирует то, как взаимодействует набор объектов. Mediator ослабляет связанность, избавляя объекты от необходимости явно ссылаться друг на друга, и позволяет независимо изменять их взаимодействие.»

Ключевые слова — **«how a set of objects interact»**: Mediator хранит **логику взаимодействия** (сценарий), а не просто передаёт сообщения. Классический пример GoF — диалоговое окно выбора шрифта: виджеты (список, поле ввода, кнопки) не знают друг о друге и сообщают директору (`DialogDirector`) «я изменился», а конкретный директор (`FontDialogDirector`) **решает**, что из этого следует: например, при выборе шрифта в списке подставляет его название в поле ввода. Каждый виджет при этом и **отправитель**, и **получатель**.

### Когда нужен Mediator

Без посредника при `N` взаимодействующих объектах связей может быть \( N \times (N-1) \) — каждый знает каждого. Mediator сводит это к `N` связям: все знают только посредника.

### Минимальный пример паттерна

```java
/**
 * Посредник: знает всех участников чата и маршрутизирует сообщения.
 * <p>Ни один {@link ChatUser} не хранит ссылок на других пользователей —
 * все сообщения проходят только через {@code ChatRoom}.</p>
 */
class ChatRoom {

    /** Список всех участников, известных посреднику. */
    private final List<ChatUser> users = new ArrayList<>();

    /**
     * Регистрирует участника в посреднике.
     *
     * @param user новый участник чата
     */
    public void join(ChatUser user) {
        users.add(user);
    }

    /**
     * Рассылает сообщение всем участникам, кроме отправителя.
     * <p>Отправитель не знает, кто получит сообщение —
     * это решение посредника.</p>
     *
     * @param sender  участник, отправивший сообщение
     * @param message текст сообщения
     */
    public void send(ChatUser sender, String message) {
        for (ChatUser user : users) {
            if (user != sender) {              // не отправлять самому себе
                user.receive(sender.getName(), message);
            }
        }
    }
}

/**
 * Участник чата.
 * <p>Знает только посредника {@link ChatRoom} —
 * не знает других {@code ChatUser} напрямую.</p>
 */
class ChatUser {

    private final String name;
    private final ChatRoom room; // единственная зависимость — посредник

    /**
     * @param name имя участника
     * @param room посредник (чат-комната)
     */
    public ChatUser(String name, ChatRoom room) {
        this.name = name;
        this.room = room;
        room.join(this); // регистрация у посредника
    }

    /**
     * Отправляет сообщение через посредника.
     *
     * @param message текст сообщения
     */
    public void send(String message) {
        room.send(this, message); // делегируем посреднику
    }

    /**
     * Получает сообщение от посредника.
     *
     * @param from    имя отправителя
     * @param message текст сообщения
     */
    public void receive(String from, String message) {
        System.out.println(name + " получил от " + from + ": " + message);
    }

    public String getName() { return name; }
}

// ── Использование ──────────────────────────────────────────────────────
ChatRoom room = new ChatRoom();        // посредник
ChatUser alice = new ChatUser("Alice", room);
ChatUser bob   = new ChatUser("Bob",   room);
ChatUser carol = new ChatUser("Carol", room);

alice.send("Привет всем!");
// → Bob получил от Alice: Привет всем!
// → Carol получил от Alice: Привет всем!
// Alice и Bob не знают ничего друг о друге — только о ChatRoom.
```

### Почему `ApplicationEventMulticaster` — не Mediator

Сходство есть: и в Mediator, и в Spring Events участники **не ссылаются друг на друга**, а общаются через промежуточный объект. Эта общая идея — indirection (косвенность: связь через третий объект) — и создаёт впечатление, что `ApplicationEventMulticaster` «играет роль `ChatRoom`». Но по сути это разные паттерны:

| Критерий | Mediator (GoF) | `ApplicationEventMulticaster` |
|----------|----------------|-------------------------------|
| **Что хранит посредник** | **Логику сценария**: кто, на что и как должен отреагировать (`FontDialogDirector`; даже в `ChatRoom` есть правило «не отправлять самому себе») | Только **реестр listener'ов**; бизнес-логики нет |
| **Роли участников** | Каждый коллега — и **отправитель**, и **получатель** (`ChatUser` и `send()`, и `receive()`) | Publisher только **публикует**, listener только **получает** — доставка **однонаправленная** |
| **Как выбирается получатель** | Решает посредник — в зависимости от сценария | Только по **типу event** (плюс `condition` у `@EventListener`) |
| **Знает ли посредник конкретных участников** | Да — обычно держит ссылки на конкретных коллег и вызывает их специфичные методы | Нет — работает с абстракцией `ApplicationListener`, ему безразлично, кто подписан |
| **Точное название** | Mediator (посредник) | Publish–Subscribe Channel / event bus (канал / шина events) |

**Аналогия для собеседования:**
- **Mediator — диспетчерская вышка аэропорта.** Вышка знает каждый самолёт и решает за них: «борт 1 садится, борт 2 ждёт, борт 3 уходит на второй круг». Самолёты общаются только через вышку и по её правилам.
- **Multicaster — радиоэфир.** В эфир передают «рейс 42 приземлился». Все, кто настроен на эту частоту (такси, уборка, табло), сами решают, что делать. Эфир никого не координирует и не знает, кто его слушает.

**Итог:** `ApplicationEventMulticaster` **частично похож на Mediator** (общая идея — участники не знают друг друга), но точный термин для него — **канал events** (event channel, event bus) из модели Publish–Subscribe. Подробно — в §4.

> **Нюанс из GoF:** в главе Observer (раздел Implementation) книга описывает отдельный объект `ChangeManager`, который хранит связи Subject → Observer, определяет стратегию обновления и рассылает уведомления, и называет его примером паттерна Mediator. Простейший его вариант (`SimpleChangeManager`) просто уведомляет всех наблюдателей — по смыслу это близко к `ApplicationEventMulticaster`. Отсюда и источник сходства; однако для Spring Events общепринятое и более точное название — Publish–Subscribe с каналом events.

Откуда берётся сходство Mediator и Observer, хорошо объясняет Refactoring.Guru:

**Источник:** [Refactoring.Guru — Mediator vs Observer](https://refactoring.guru/design-patterns/observer)

> **EN:** «The primary goal of Mediator is to eliminate mutual dependencies among a set of system components. Instead, these components become dependent on a single mediator object … There's a popular implementation of the Mediator pattern that relies on Observer. The mediator object plays the role of publisher, and the components act as subscribers … When Mediator is implemented this way, it may look very similar to Observer.»

> **RU:** «Цель Mediator — устранить прямые зависимости между компонентами, сделав их зависимыми от единственного объекта-посредника … Существует популярная реализация паттерна Mediator, основанная на Observer: объект-посредник играет роль publisher, а компоненты — подписчиков. В таком виде Mediator очень похож на Observer.»

---

<a id="observer-listener-mediator-diff"></a>

## 4. Observer, Listener, Mediator — три синонима или три разных паттерна?

### Как связаны термины

На практике эти три термина часто используют как взаимозаменяемые, хотя между ними есть структурные отличия. Разобраться помогает цитата из [Refactoring.Guru — Observer](https://refactoring.guru/design-patterns/observer):

> **EN:** «Observer — Also known as: **Event-Subscriber, Listener**.»

> **RU:** «Observer — также известен как: **Event-Subscriber, Listener**.»

Это значит: Refactoring.Guru считает все три названия **синонимами одного паттерна**. Spring придерживается той же позиции, называя `ApplicationListener` реализацией Observer.

### Почему же мы говорим «в Spring это Listener, а не Observer»?

Потому что **идея** и **структура** — разные вещи:

| Критерий | GoF Observer | Spring Events (Listener) |
|----------|-------------|--------------------------|
| **Идея** | Push-уведомление при изменении состояния | Push-уведомление при наступлении events |
| **Кто хранит список получателей** | Сам Subject | `ApplicationEventMulticaster` (посредник) |
| **Знает ли publisher получателей** | **Да** — Subject хранит `List<Observer>` | **Нет** — publisher знает только `ApplicationEventPublisher` |
| **Посредник** | Отсутствует | `ApplicationEventMulticaster` — канал events (event bus) модели Publish–Subscribe, **не** Mediator |
| **Триггер** | Изменение **состояния** объекта | Любой **факт** (event) |

**Итог:** Spring называет свою систему Observer по **концепции** (push + слабая связанность). Структурно же это **Observer в варианте Publish–Subscribe**: между publisher и listener'ами стоит канал events — `ApplicationEventMulticaster`. Слово «listener» пришло из интерфейса-маркера `java.util.EventListener`, который расширяет `ApplicationListener`; именно поэтому в разговорной практике Spring-код с `@EventListener` принято называть listener'ом. Паттерна Mediator здесь **нет** — см. §3.

[blog.beezwax.net](https://blog.beezwax.net/did-you-hear-something-observer-pattern-vs-event-listeners/) формулирует практическое разграничение:

> **EN:** «Observers are registered to the subjects they are observing, and **a subject knows all the observers it notifies**. Listeners, on the other hand, are listening for events on a global Events instance … **Any objects that are triggering events will broadcast their events without knowing who they are broadcasting to.**»

> **RU:** «Наблюдатели регистрируются у конкретного Subject, и **Subject знает всех своих Observer'ов**. Listener'ы же слушают events через глобальный экземпляр Events … **Любой объект, публикующий event, не знает, кто его слушает.**»

### Классический GoF Observer — кто что делает

- **Subject** (наблюдаемый объект) хранит список наблюдателей и предоставляет операции `attach()` / `detach()` (подписка / отписка).
- При изменении состояния Subject вызывает `notify()`, который **сам обходит** список и у каждого наблюдателя вызывает `Observer.update()`.
- Посредника нет: Subject напрямую (через абстрактный интерфейс `Observer`) знает каждого подписчика.

> **Путаница в названиях JDK:** в `java.util` роль **Subject** играет класс **`Observable`** (наблюдаемый: `addObserver()` / `deleteObserver()` / `notifyObservers()`), а наблюдатели реализуют интерфейс **`Observer`** с методом `update()`. То есть `Observable` — это **наблюдаемый**, а не наблюдатель. Оба типа помечены `@Deprecated` с Java 9 (см. §1).

### Spring Events — три роли

| Роль | Тип в Spring | Что делает |
|------|--------------|------------|
| **Publisher** | `ApplicationEventPublisher` (обычно это сам `ApplicationContext`) | Вызывает `publishEvent()`; **не знает** подписчиков |
| **Канал events** | `ApplicationEventMulticaster` (по умолчанию `SimpleApplicationEventMulticaster`) | **Хранит** listener'ы, **подбирает** их по типу event и **вызывает** |
| **Подписчики** | `ApplicationListener<E>` / методы с `@EventListener` | Получают event; publisher не знают |

**Источник:** [Javadoc — `ApplicationEventPublisher`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/ApplicationEventPublisher.html) · [Javadoc — `ApplicationEventMulticaster`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/event/ApplicationEventMulticaster.html) · [Spring Framework — Standard and Custom Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)

> **EN (`ApplicationEventPublisher`):** «Interface that encapsulates event publication functionality. Serves as a super-interface for ApplicationContext.» / «Such an event publication step is effectively a hand-off to the multicaster and does not imply synchronous/asynchronous execution or even immediate execution at all.»

> **EN (`ApplicationEventMulticaster`):** «Interface to be implemented by objects that can manage a number of ApplicationListener objects and publish events to them. An ApplicationEventPublisher, typically a Spring ApplicationContext, can use an ApplicationEventMulticaster as a delegate for actually publishing events.»

> **EN (Reference):** «Finally, as with classic ApplicationListener implementations, the actual multicasting happens via a context-wide ApplicationEventMulticaster at runtime. By default, this is a SimpleApplicationEventMulticaster with synchronous event publication in the caller thread.»

> **RU:** «Интерфейс, инкапсулирующий функциональность публикации events. Служит суперинтерфейсом для ApplicationContext.» / «Такая публикация — по сути передача events multicaster'у; она не означает ни синхронного, ни асинхронного, ни даже немедленного выполнения.» / «Интерфейс для объектов, которые управляют набором объектов ApplicationListener и публикуют им events. ApplicationEventPublisher — обычно Spring ApplicationContext — может использовать ApplicationEventMulticaster как делегата для фактической публикации events.» / «Как и для классических реализаций ApplicationListener, фактическая рассылка во время выполнения идёт через общий для контекста ApplicationEventMulticaster. По умолчанию это SimpleApplicationEventMulticaster с синхронной публикацией в потоке вызывающего кода.»

### Правильное название — Publish–Subscribe

Модель, в которой publisher не знает подписчиков, а между ними стоит канал, называется **Publish–Subscribe** (публикация–подписка). В книге *Enterprise Integration Patterns* (Gregor Hohpe, Bobby Woolf, 2003) она описана как паттерн **Publish-Subscribe Channel** (канал публикации–подписки).

**Источник:** [Enterprise Integration Patterns — Publish-Subscribe Channel](https://www.enterpriseintegrationpatterns.com/patterns/messaging/PublishSubscribeChannel.html)

> **EN:** «Send the event on a Publish-Subscribe Channel, which delivers a copy of a particular event to each receiver.»

> **RU:** «Отправьте event в Publish-Subscribe Channel, который доставляет копию этого event каждому получателю.»

Внутри одного процесса (одного `ApplicationContext`) роль такого канала играет `ApplicationEventMulticaster`. Поэтому его можно назвать **шиной events** (event bus), а всю систему — неформально «Spring event bus». Это **неофициальное** название: типа с именем `EventBus` в Spring Framework нет (не путать, например, с классом `EventBus` из Guava).

Отличие от EIP: там канал — это инфраструктура обмена сообщениями между приложениями (брокер, топик), а в Spring это обычный объект в памяти, который по умолчанию вызывает listener'ы синхронно в потоке publisher.

### Почему Spring сам называет это Observer

**Источник:** [Spring Framework — Standard and Custom Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)

> **EN:** «Essentially, this is the standard Observer design pattern.»

> **RU:** «По сути, это стандартный паттерн проектирования Observer.»

Это **упрощение**, а не ошибка. Publish–Subscribe — вариант Observer, в котором между Subject и наблюдателями появляется посредник-канал: идея та же (push-уведомление подписчиков), меняется лишь то, **кто хранит список и рассылает уведомления**. GoF и сами приводят Publish-Subscribe среди альтернативных названий Observer (раздел Also Known As). Поэтому оба утверждения верны каждое на своём уровне: «это Observer» — по идее, «это Publish–Subscribe с каналом events» — по структуре.

### Формулировка для собеседования

> Spring Events — это Observer в варианте Publish–Subscribe: publisher (`ApplicationEventPublisher`) не знает подписчиков, а `ApplicationEventMulticaster` работает как канал events (event bus) и доставляет их listener-ам по типу event.

---

<a id="gof-observer-spring"></a>

## 5. GoF-Observer в Spring без `ApplicationEventPublisher`

### Зачем это нужно

Если вам нужен именно **GoF-Observer** — где Subject **сам** хранит список и **сам** обходит его — это можно сделать в Spring вручную, не используя механизм `ApplicationEvent`. Такой подход используют, когда хотят явно управлять подпиской: добавлять и удалять наблюдателей в runtime, или когда Subject должен знать своих конкретных подписчиков.

### Ключевое отличие от Spring Events (перед кодом)

В Spring Events publisher вызывает `publishEvent()` и **не знает**, кто получит event. В GoF-Observer ниже `StockPriceTracker` **сам хранит** `List<StockObserver>` и **сам вызывает** каждого — никакого `ApplicationEventMulticaster` посередине нет.

### Пример

```java
/**
 * Интерфейс Observer'а (наблюдателя).
 * <p>Любой объект, желающий следить за ценой акции,
 * должен реализовать этот интерфейс.</p>
 */
public interface StockObserver {

    /**
     * Вызывается Subject'ом при изменении цены.
     *
     * @param ticker   тикер акции, например {@code "AAPL"}
     * @param newPrice новая цена
     */
    void onPriceChanged(String ticker, double newPrice);
}

/**
 * Subject (наблюдаемый объект) — GoF-Observer в рамках Spring-бина.
 *
 * <p><b>Ключевое отличие от Spring Events:</b> этот класс
 * <em>сам хранит</em> {@code List<StockObserver>} и <em>сам вызывает</em>
 * каждого наблюдателя. Нет никакого {@code ApplicationEventPublisher},
 * нет {@code ApplicationEventMulticaster} — чистый GoF-паттерн.</p>
 */
@Component
public class StockPriceTracker {

    /**
     * Список наблюдателей.
     * <p>Subject <em>знает</em> каждого Observer'а — это главный
     * признак GoF-Observer в отличие от Spring Events (Listener).</p>
     */
    private final List<StockObserver> observers = new ArrayList<>();

    /**
     * Регистрирует нового наблюдателя.
     *
     * @param observer наблюдатель, которого нужно уведомлять об изменениях
     */
    public void addObserver(StockObserver observer) {
        observers.add(observer);
    }

    /**
     * Удаляет наблюдателя (отписка в runtime).
     *
     * @param observer наблюдатель для удаления
     */
    public void removeObserver(StockObserver observer) {
        observers.remove(observer);
    }

    /**
     * Обновляет цену и уведомляет всех наблюдателей.
     * <p>Subject <em>сам обходит</em> список — аналог
     * {@code notifyObservers()} из {@link java.util.Observable}.</p>
     *
     * @param ticker   тикер акции
     * @param newPrice новая цена
     */
    public void updatePrice(String ticker, double newPrice) {
        // Subject сам идёт по списку — это и есть GoF Observer
        observers.forEach(observer -> observer.onPriceChanged(ticker, newPrice));
    }
}

/**
 * Конкретный Observer: отправляет алерт при изменении цены.
 * <p>Реализует {@link StockObserver}: ничего не знает о других
 * Observer'ах и не знает, когда именно будет вызван — Subject решает.</p>
 */
@Component
public class AlertService implements StockObserver {

    /**
     * Вызывается Subject'ом ({@link StockPriceTracker}) напрямую.
     *
     * @param ticker   тикер акции
     * @param newPrice новая цена
     */
    @Override
    public void onPriceChanged(String ticker, double newPrice) {
        System.out.println("ALERT: " + ticker + " → " + newPrice);
    }
}

/**
 * Конфигурация подписок при старте приложения.
 *
 * <p>В GoF-Observer подписку настраивают явно: здесь
 * {@link AlertService} регистрируется у {@link StockPriceTracker}.
 * Subject знает конкретного Observer'а — в отличие от Spring Events,
 * где publisher не знает listener'ов.</p>
 */
@Component
public class AppSetup {

    /**
     * @param tracker      Subject — будет хранить ссылку на alertService
     * @param alertService Observer — регистрируется у Subject'а вручную
     */
    @Autowired
    public AppSetup(StockPriceTracker tracker, AlertService alertService) {
        tracker.addObserver(alertService); // Subject теперь ЗНАЕТ alertService
    }
}

// ── Использование ──────────────────────────────────────────────────────
// tracker.updatePrice("AAPL", 195.50);
// → "ALERT: AAPL → 195.5"
//
// Хотите отписаться в runtime? Просто:
// tracker.removeObserver(alertService);
```

### Что здесь отличает от Spring Events

| | GoF-Observer выше | Spring `@EventListener` |
|---|---|---|
| Кто хранит список | `StockPriceTracker` сам | `ApplicationEventMulticaster` |
| Знает ли publisher получателей | **Да** — `observers` список | **Нет** |
| Отписка в runtime | `removeObserver()` | Возможна, если listener добавлен в контекст вручную через `addApplicationListener()`: тогда его удаляют через `removeApplicationListener()` (методы `ConfigurableApplicationContext`, удаление — с Spring 6.0). Listeners, которых Spring зарегистрировал сам (bean с `ApplicationListener`, методы с `@EventListener`), из кода приложения обычно не отписывают |
| Посредник | Нет | `ApplicationEventMulticaster` |

---

<a id="observer-listener-diff"></a>

## 6. Observer и Listener — сравнительная таблица

| | **Observer (GoF / JDK)** | **Listener (Spring Events)** |
|---|---|---|
| **API** | `Observable` + `Observer` | `ApplicationEvent` + `ApplicationListener` / `@EventListener` |
| **Кто хранит подписчиков** | **Сам Subject** (`addObserver`) | **Контекст** (`ApplicationEventMulticaster`) |
| **Как уведомляют** | Subject вызывает `notifyObservers()` → `update()` напрямую | Publisher вызывает `publishEvent()` → **канал events** (`ApplicationEventMulticaster`) вызывает listener'ы |
| **Связь publisher ↔ получатель** | Subject **знает** и **хранит** список Observer'ов | Publisher **не знает** listener'ов — только публикует event |
| **Триггер** | Изменение **состояния** объекта (`setChanged`) | Факт **events** (`BlockedListEvent`, `ApplicationReadyEvent`, …) |
| **Посредник / паттерн** | Отсутствует — классический GoF Observer | `ApplicationEventMulticaster` — канал events (event bus); паттерн Publish–Subscribe, **не** Mediator |
| **Отписка в runtime** | `removeObserver()` | Возможна, если listener добавлен в контекст вручную через `addApplicationListener()`: тогда его удаляют через `removeApplicationListener()` (с Spring 6.0). Listeners, которых Spring зарегистрировал сам, из кода приложения обычно не отписывают |
| **Типичный кейс** | Модель MVC, push-потоки данных, legacy | Доменные и lifecycle-events в Spring-приложении |

### Практическое правило

- **Observer (GoF)** — «мой объект **сам ведёт** список и **сам зовёт** `update()` у каждого наблюдателя».
- **Listener (Spring)** — «я **публикую event** в контекст, канал events (`ApplicationEventMulticaster`) **сам найдёт** bean'ы и **вызовет** обработчик».

---

## Источники (сводка)

| Тема | Документ |
|------|----------|
| Observer в JDK | [Observable](https://docs.oracle.com/javase/8/docs/api/java/util/Observable.html), [Observer](https://docs.oracle.com/javase/8/docs/api/java/util/Observer.html) |
| GoF Observer / Mediator / синонимы | [Refactoring.Guru — Observer](https://refactoring.guru/design-patterns/observer) |
| Observer vs Listener (практика) | [blog.beezwax.net — Did You Hear Something?](https://blog.beezwax.net/did-you-hear-something-observer-pattern-vs-event-listeners/) |
| Spring Events | [Application Events (Reference)](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events) |
| ApplicationListener | [Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/ApplicationListener.html) |
| ApplicationEventPublisher / ApplicationEventMulticaster | [Javadoc — Publisher](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/ApplicationEventPublisher.html), [Javadoc — Multicaster](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/event/ApplicationEventMulticaster.html) |
| Реестр listeners и подбор по типу event | [AbstractApplicationContext (исходный код)](https://github.com/spring-projects/spring-framework/blob/main/spring-context/src/main/java/org/springframework/context/support/AbstractApplicationContext.java), [AbstractApplicationEventMulticaster (исходный код)](https://github.com/spring-projects/spring-framework/blob/main/spring-context/src/main/java/org/springframework/context/event/AbstractApplicationEventMulticaster.java), [Javadoc — EventListenerMethodProcessor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/event/EventListenerMethodProcessor.html) |
| Publish-Subscribe Channel | [Enterprise Integration Patterns](https://www.enterpriseintegrationpatterns.com/patterns/messaging/PublishSubscribeChannel.html) (Hohpe, Woolf) |
| GoF Mediator (Intent) | Gamma, Helm, Johnson, Vlissides — *Design Patterns: Elements of Reusable Object-Oriented Software*, Addison-Wesley, 1994 |
| ApplicationReadyEvent | [Spring Boot](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.application-events-and-listeners), [Javadoc](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/context/event/ApplicationReadyEvent.html) |
| Любой объект как event, поле publisher, WebFlux | [Application Events (Reference)](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events), [ApplicationListenerMethodAdapter](https://github.com/spring-projects/spring-framework/blob/v6.2.11/spring-context/src/main/java/org/springframework/context/event/ApplicationListenerMethodAdapter.java) |
