

# Архитурный подход к выбору реализации в Spring без `@Qualifier`, `@Primary`, `@Profile`

## Оглавление

- [1. Основной принцип](#1-%D0%BE%D1%81%D0%BD%D0%BE%D0%B2%D0%BD%D0%BE%D0%B9-%D0%BF%D1%80%D0%B8%D0%BD%D1%86%D0%B8%D0%BF)
- [2. Почему аннотационная маршрутизация — плохой подход](#2-%D0%BF%D0%BE%D1%87%D0%B5%D0%BC%D1%83-%D0%B0%D0%BD%D0%BD%D0%BE%D1%82%D0%B0%D1%86%D0%B8%D0%BE%D0%BD%D0%BD%D0%B0%D1%8F-%D0%BC%D0%B0%D1%80%D1%88%D1%80%D1%83%D1%82%D0%B8%D0%B7%D0%B0%D1%86%D0%B8%D1%8F--%D0%BF%D0%BB%D0%BE%D1%85%D0%BE%D0%B9-%D0%BF%D0%BE%D0%B4%D1%85%D0%BE%D0%B4)
- [3. Правильный паттерн: self-describing strategy + registry](#3-%D0%BF%D1%80%D0%B0%D0%B2%D0%B8%D0%BB%D1%8C%D0%BD%D1%8B%D0%B9-%D0%BF%D0%B0%D1%82%D1%82%D0%B5%D1%80%D0%BD-self-describing-strategy--registry)
- [4. Полный пример](#4-%D0%BF%D0%BE%D0%BB%D0%BD%D1%8B%D0%B9-%D0%BF%D1%80%D0%B8%D0%BC%D0%B5%D1%80)
- [5. Архитектурные правила](#5-%D0%B0%D1%80%D1%85%D0%B8%D1%82%D0%B5%D0%BA%D1%82%D1%83%D1%80%D0%BD%D1%8B%D0%B5-%D0%BF%D1%80%D0%B0%D0%B2%D0%B8%D0%BB%D0%B0)

***

## 1. Основной принцип

Если у одного интерфейса существует несколько реализаций, то правильнее не маршрутизировать их через `@Qualifier`, `@Primary`, `@Profile` и аналогичные аннотации, а использовать возможности IoC-контейнера Spring: собрать все реализации контракта, присвоить каждой реализации явный доменный признак и построить реестр стратегий для дальнейшего выбора по константе.

Источник: https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html

> "You can also instruct Spring to provide all beans of a particular type from the `ApplicationContext` by adding the `@Autowired` annotation to a field or method that expects an array of that type, as the following example shows:"

Ru:

> «Можно указать Spring предоставить все бины определённого типа из `ApplicationContext`, добавив `@Autowired` к полю или методу, который ожидает массив этого типа.»

Источник: https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html

> "The same applies for typed collections, as the following example shows:"

Ru:

> «То же самое относится и к типизированным коллекциям.»

Источник: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-autowire.html

> "With `byType` or `constructor` autowiring mode, you can wire arrays and typed collections."

Ru:

> «При автосвязывании по типу или через конструктор можно внедрять массивы и типизированные коллекции.»

Иными словами, Spring уже умеет собрать все реализации одного контракта. Значит, задача выбора нужной реализации должна решаться не через аннотационный роутинг, а через отдельный уровень композиции — registry или factory.

***

## 2. Почему аннотационная маршрутизация — плохой подход

`@Qualifier`, `@Primary`, `@Profile` и похожие механизмы в этом сценарии являются слабым архитектурным решением, потому что они подменяют модель предметной области технической маршрутизацией контейнера.

Источник: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-autowire.html

> "Autowiring is less exact than explicit wiring."

Ru:

> «Автосвязывание менее точно, чем явное связывание.»

Источник: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-autowire.html

> "The relationships between your Spring-managed objects are no longer documented explicitly."

Ru:

> «Связи между объектами, управляемыми Spring, больше не документируются явно.»

Источник: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-autowire.html

> "Multiple bean definitions within the container may match the type specified by the setter method or constructor argument to be autowired."

Ru:

> «Несколько определений бинов в контейнере могут соответствовать типу, указанному в setter-методе или аргументе конструктора для автосвязывания.»

Источник: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-autowire.html

> "If no unique bean definition is available, an exception is thrown."

Ru:

> «Если уникального определения бина нет, выбрасывается исключение.»

Проблема здесь не в том, что Spring не умеет разрешить неоднозначность. Проблема в том, что выбор конкретной реализации начинает жить либо в месте инъекции, либо в профиле окружения, либо в аннотационной метаинформации. Это означает, что потребитель знает слишком много о механике выбора, а не просто работает с абстракцией.

***

## 3. Правильный паттерн: self-describing strategy + registry

Правильнее дать каждой реализации собственный константный признак через метод контракта, затем на этапе инициализации приложения собрать все реализации в единый реестр и уже через этот реестр выбирать нужную стратегию.

Источник: https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html

> "Even typed `Map` instances can be autowired as long as the expected key type is `String`."

Ru:

> «Даже типизированные `Map` можно внедрять, если ожидаемый тип ключа — `String`.»

Источник: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-autowire.html

> "An autowired `Map` instance’s values consist of all bean instances that match the expected type, and the `Map` instance’s keys contain the corresponding bean names."

Ru:

> «Значениями автосвязанного `Map` являются все экземпляры бинов, соответствующие ожидаемому типу, а ключами — соответствующие имена бинов.»

Практически это означает следующее:

- контракт содержит метод получения доменного признака;
- каждая реализация сама возвращает свой константный ключ;
- Spring собирает список всех реализаций;
- на старте приложения строится `Map<ДоменныйКлюч, Реализация>`;
- потребитель работает только с registry.

Такой подход лучше соответствует IoC, потому что потребитель не выбирает бин по аннотации, а делегирует выбор специализированному полиморфному объекту.

***

## 4. Полный пример

Ниже пример, оформленный в соответствии с этим подходом.

```java
public enum NotificationType {
    EMAIL,
    SMS,
    PUSH
}
```

```java
public interface NotificationSender {
    NotificationType getType();
    Mono<Void> send(Notification notification);
}
```

```java
@Component
public class EmailNotificationSender implements NotificationSender {

    @Override
    public NotificationType getType() {
        return NotificationType.EMAIL;
    }

    @Override
    public Mono<Void> send(Notification notification) {
        return Mono.empty();
    }
}
```

```java
@Component
public class SmsNotificationSender implements NotificationSender {

    @Override
    public NotificationType getType() {
        return NotificationType.SMS;
    }

    @Override
    public Mono<Void> send(Notification notification) {
        return Mono.empty();
    }
}
```

```java
@Component
public class PushNotificationSender implements NotificationSender {

    @Override
    public NotificationType getType() {
        return NotificationType.PUSH;
    }

    @Override
    public Mono<Void> send(Notification notification) {
        return Mono.empty();
    }
}
```

```java
@Configuration
public class NotificationSenderConfiguration {

    @Bean
    public Map<NotificationType, NotificationSender> notificationSenderMap(
            List<NotificationSender> senders) {

        return senders.stream()
                .collect(Collectors.toUnmodifiableMap(
                        NotificationSender::getType,
                        Function.identity()
                ));
    }

    @Bean
    public NotificationSenderRegistry notificationSenderRegistry(
            Map<NotificationType, NotificationSender> notificationSenderMap) {
        return new NotificationSenderRegistry(notificationSenderMap);
    }
}
```

```java
public class NotificationSenderRegistry {

    private final Map<NotificationType, NotificationSender> registry;

    public NotificationSenderRegistry(
            Map<NotificationType, NotificationSender> registry) {
        this.registry = registry;
    }

    public NotificationSender get(NotificationType type) {
        NotificationSender sender = registry.get(type);
        if (sender == null) {
            throw new IllegalArgumentException(
                    "No sender registered for type: " + type);
        }
        return sender;
    }
}
```

```java
@Service
public class NotificationService {

    private final NotificationSenderRegistry registry;

    public NotificationService(NotificationSenderRegistry registry) {
        this.registry = registry;
    }

    public Mono<Void> send(NotificationType type, Notification notification) {
        return registry.get(type).send(notification);
    }
}
```

Этот вариант хорош тем, что `NotificationService` не знает о конкретных реализациях и не зависит от механизма выбора кандидата Spring. Он зависит только от полиморфного реестра.

Источник: https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html

> "You can also instruct Spring to provide all beans of a particular type from the `ApplicationContext`..."

Ru:

> «Можно указать Spring предоставить все бины определённого типа из `ApplicationContext`...»

***

## 5. Архитектурные правила

Для таких задач можно зафиксировать следующие правила.

1. Никогда не использовать `@Qualifier`, `@Primary`, `@Profile` и аналогичные аннотации как средство маршрутизации нескольких реализаций одного полиморфного типа.
2. Всегда по возможности придерживаться принципа IoC, на котором построен Spring Boot, и использовать механизмы автоматической сборки всех реализаций контейнером.
3. Контракт должен содержать метод, возвращающий константный доменный признак реализации.
4. Каждая реализация обязана сама возвращать свой ключ, а не получать его через внешнюю техническую конфигурацию.
5. На этапе инициализации приложения должен строиться единый реестр реализаций.
6. В месте использования должен инжектироваться не “нужный бин”, а полиморфный registry или factory, который уже выбирает реализацию по доменному ключу.
7. Если появляется необходимость в `@Qualifier`, это повод сначала проверить, не нарушены ли границы контракта и не смешаны ли разные роли в одном интерфейсе.

Источник: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-autowire.html

> "Changing the default setting is not recommended for larger deployments, because specifying collaborators explicitly gives greater control and clarity."

Ru:

> «Изменять настройку по умолчанию не рекомендуется для крупных систем, потому что явное указание зависимостей даёт больший контроль и ясность.»

 Смысл цитаты 
  - она про то, что в больших системах плохо, когда контейнер слишком много решает **неявно**. 
Источник: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-autowire.html

То есть мысль Spring такая: чем меньше “магии” в выборе зависимостей, тем лучше читается архитектура. 

Источник: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-autowire.html

> "Autowiring is less exact than explicit wiring."

Ru:

> «Автосвязывание менее точно, чем явное связывание.»

## Плохой пример

Здесь выбор реализации спрятан в контейнере и аннотациях:

```java
@Service
public class PaymentService {

    private final PaymentGateway gateway;

    public PaymentService(@Qualifier("stripeGateway") PaymentGateway gateway) {
        this.gateway = gateway;
    }
}
```

Почему это плохо:

- сервис знает, **какую именно** реализацию ему дали;
- выбор реализации захардкожен в точке инъекции;
- при добавлении новых реализаций логика выбора остаётся размазанной по коду.

Это и есть тот случай, где архитектура становится менее явной.

## Хороший пример

Здесь Spring только собирает все реализации, а выбор идёт через доменный ключ:

```java
public interface PaymentGateway {
    GatewayType getType();
    void pay();
}
```

```java
@Component
class StripeGateway implements PaymentGateway {
    public GatewayType getType() { return GatewayType.STRIPE; }
    public void pay() {}
}
```

```java
@Component
class PaypalGateway implements PaymentGateway {
    public GatewayType getType() { return GatewayType.PAYPAL; }
    public void pay() {}
}
```

```java
@Component
class PaymentGatewayRegistry {

    private final Map<GatewayType, PaymentGateway> registry;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways) {
        this.registry = gateways.stream()
                .collect(Collectors.toMap(PaymentGateway::getType, g -> g));
    }

    public PaymentGateway get(GatewayType type) {
        return registry.get(type);
    }
}
```

```java
@Service
public class PaymentService {

    private final PaymentGatewayRegistry registry;

    public PaymentService(PaymentGatewayRegistry registry) {
        this.registry = registry;
    }

    public void process(GatewayType type) {
        registry.get(type).pay();
    }
}
```

Почему это хорошо:

- сервис не знает конкретные классы;
- выбор реализации выражен **явно** через `GatewayType`;
- Spring используется по IoC-модели: он собирает набор стратегий, а не маршрутизирует бизнес-логику через аннотации.

Коротко: плохой вариант — **container-driven routing**, хороший вариант — **domain-driven selection through registry**. 

Источник: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-autowire.html

