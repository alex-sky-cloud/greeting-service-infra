# Аннотации @Primary и @Profile в Spring

## Оглавление

- [Аннотация @Primary](#primary)
- [Аннотация @Profile](#profile)
- [Отличие от реестра стратегий](#registry)

<a id="primary"></a>

## Аннотация `@Primary`

### Что означает и когда используется

- Указывает предпочтительный бин, если для внедрения одной зависимости подходят несколько кандидатов.
- Используется, когда одна реализация интерфейса должна быть вариантом по умолчанию.
- Если среди подходящих кандидатов ровно один отмечен `@Primary`, Spring внедрит его.

- Источник: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/Primary.html

EN:

> Indicates that a bean should be given preference when multiple candidates are qualified to autowire a single-valued dependency. If exactly one 'primary' bean exists among the candidates, it will be the autowired value.

RU:

> Указывает, что бину следует отдавать предпочтение, когда несколько кандидатов подходят для автоматического внедрения одной зависимости. Если среди кандидатов существует ровно один основной бин, именно он будет внедрён.

### Важное ограничение

- `@Primary` не ограничивает набор реализаций при внедрении коллекции: в неё включаются все подходящие по типу бины.
- Поэтому основной бин можно использовать одновременно со сбором реализаций в реестр стратегий.

- Источник: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/Primary.html

EN:

> All type-matching beans are included when autowiring arrays, collections, maps, or ObjectProvider streams.

RU:

> При автоматическом внедрении массивов, коллекций, карт или потоков `ObjectProvider` включаются все бины, соответствующие типу.

<a id="profile"></a>

## Аннотация `@Profile`

### Что означает и когда используется

- Определяет, при каких активных профилях компонент может быть зарегистрирован в контейнере.
- Используется для разделения конфигураций приложения: например, заглушка в `dev` и рабочая реализация в `prod`.
- В отличие от `@Primary`, управляет условием регистрации компонента, а не предпочтением среди уже зарегистрированных кандидатов.

- Источник: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/Profile.html

EN:

> Indicates that a component is eligible for registration when one or more specified profiles are active.

RU:

> Указывает, что компонент может быть зарегистрирован, когда активен один или несколько заданных профилей.

### Пример применения

```java
@Component
@Profile("dev")
class StubPaymentService implements PaymentService {}

@Component
@Profile("prod")
class RealPaymentService implements PaymentService {}
```

Это иллюстрация правила выше: при активном только `dev` условию соответствует первая реализация, при активном только `prod` — вторая.

<a id="registry"></a>

## Отличие от реестра стратегий

Архитектурный вывод из описанного поведения аннотаций:

- `@Primary` отвечает на вопрос: «Какую реализацию предпочесть при внедрении одной зависимости?»
- `@Profile` отвечает на вопрос: «При какой конфигурации зарегистрировать компонент?»
- Реестр стратегий из исходного тезиса отвечает на вопрос: «Какую доступную реализацию выбрать по доменному признаку при обработке запроса?»

Поэтому тезис точнее сформулировать так:

> Для выбора реализации по бизнес-признаку используйте реестр стратегий. `@Primary` и `@Profile` применяйте для настройки внедрения зависимостей и состава контекста. Эти механизмы не исключают друг друга.

Это архитектурная интерпретация, а не запрет Spring на использование аннотаций.