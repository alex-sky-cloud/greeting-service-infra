# К какой фазе Spring bean Life - отнести запуск Netty

**Коротный ответ:** фактическая инициализация транспорта Netty — bind порта, запуск event loop и создание listening socket — относится к фазе **запуска приложения после создания и инициализации всех singleton-bean’ов**. В терминах типового «жизненного цикла Spring» это отдельная фаза **`SmartLifecycle.start()`**, а **не** фаза создания/инициализации конкретного bean (`@PostConstruct`, `afterPropertiesSet()`, `init-method`).

## Оглавление

- [Ответ для собеседования](#ответ-для-собеседования)
- [Фазы жизненного цикла](#фазы-жизненного-цикла)
- [Где создаётся транспорт](#где-создаётся-транспорт)
- [Что именно происходит](#что-именно-происходит)
- [Правильная формулировка](#правильная-формулировка)

## Ответ для собеседования

**Утверждение.** Если на собеседовании перечисляют именно жизненный цикл *одного Spring bean*, то Netty transport нельзя честно поместить ни в `@PostConstruct`, ни в `afterPropertiesSet()`, ни в custom `init-method`. Эти callbacks относятся к инициализации **конкретного bean после DI**, а не к запуску встроенного веб-сервера.

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

EN:

> “Multiple lifecycle mechanisms configured for the same bean, with different initialization methods, are called as follows: Methods annotated with `@PostConstruct`; `afterPropertiesSet()` as defined by the `InitializingBean` callback interface; a custom configured `init()` method.”

RU:

> «Несколько механизмов жизненного цикла, настроенных для одного bean с разными методами инициализации, вызываются в следующем порядке: методы с `@PostConstruct`; `afterPropertiesSet()` из интерфейса `InitializingBean`; пользовательский метод `init()`.»

**Вывод.** Запуск Netty — это не «инициализация bean», а следующий этап: Spring уже создал и инициализировал singleton-bean’ы, после чего запускает инфраструктурные компоненты приложения.

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

EN:

> “When the context is refreshed (after all objects have been instantiated and initialized), that callback is invoked.”

RU:

> «Когда контекст завершает подготовку, после того как все объекты были созданы и инициализированы, вызывается этот callback.»

## Фазы жизненного цикла

Для практического ответа удобно разделять lifecycle на такие фазы:

| Фаза | Что происходит | Netty transport |
| :-- | :-- | :-- |
| 1. Создание bean | Spring создаёт экземпляр | Нет |
| 2. DI / настройка свойств | Заполняются зависимости и конфигурация | Нет |
| 3. Инициализация bean | `@PostConstruct` → `afterPropertiesSet()` → `init-method` | Нет |
| 4. Все singleton-bean’ы готовы | Завершена обычная инициализация объектов | Ещё нет фактического bind |
| 5. **Запуск lifecycle-компонентов** | Spring автоматически запускает `SmartLifecycle` | **Да: здесь запускается Netty transport** |
| 6. Работа приложения | Сервер принимает TCP/HTTP-запросы | Да |
| 7. Остановка | Остановка transport, затем `@PreDestroy` / `destroy()` | Transport закрывается |

**Утверждение.** `SmartLifecycle` предназначен для компонентов, которые контейнер должен запускать автоматически после завершения создания и инициализации объектов приложения. Метод `isAutoStartup()` определяет, будет ли компонент запущен автоматически.

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

EN:

> “When the context is refreshed (after all objects have been instantiated and initialized), that callback is invoked. At that point, the default lifecycle processor checks the boolean value returned by each `SmartLifecycle` object’s `isAutoStartup()` method. If `true`, that object is started at that point…”

RU:

> «Когда контекст завершает подготовку, после создания и инициализации всех объектов вызывается этот callback. В этот момент стандартный lifecycle processor проверяет значение `isAutoStartup()` каждого объекта `SmartLifecycle`. Если оно равно `true`, объект запускается на этом этапе…»

## Где создаётся транспорт

**Утверждение.** В Spring Boot объект `NettyWebServer` может быть подготовлен раньше, однако клиент не должен иметь возможности подключиться к серверу, пока не вызван `WebServer.start()`. Значит именно `start()` — граница реального запуска транспорта: до неё сервер не слушает порт.

**Источник:** https://docs.spring.io/spring-boot/4.1.0/api/java/org/springframework/boot/web/server/reactive/ReactiveWebServerFactory.html

EN:

> “Gets a new fully configured but paused `WebServer` instance. Clients should not be able to connect to the returned server until `WebServer.start()` is called.”

RU:

> «Возвращает новый полностью настроенный, но приостановленный экземпляр `WebServer`. Клиенты не должны иметь возможности подключиться к возвращённому серверу до вызова `WebServer.start()`.»

**Утверждение.** Поэтому нужно различать два действия:

- **Создание конфигурации сервера:** Spring создаёт `HttpServer` и `NettyWebServer`; это подготовка infrastructure object.
- **Инициализация транспорта:** `NettyWebServer.start()` вызывает Reactor Netty `bindNow()`; после этого порт `8083` слушается, созданы server `Channel` и event loops.

**Источник:** https://docs.spring.io/spring-boot/api/java/org/springframework/boot/reactor/netty/NettyWebServer.html

EN:

> “Starts the web server.”

RU:

> «Запускает веб-сервер.»

**Источник:** https://projectreactor.io/docs/netty/release/api/reactor/netty/transport/ServerTransport.html

EN:

> “Bind the `ServerTransport` and return a `Mono` of `DisposableServer`.”

RU:

> «Привязывает `ServerTransport` и возвращает `Mono` с `DisposableServer`.»

## Что именно происходит

**Утверждение.** После обычной инициализации bean’ов Spring запускает lifecycle-компоненты. В Spring Boot lifecycle-компонент веб-сервера вызывает `NettyWebServer.start()`, а затем Reactor Netty выполняет bind и поднимает реальный сетевой транспорт.

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

EN:

> “For fine-grained control over auto-startup of a specific bean (including startup phases), consider implementing the extended `org.springframework.context.SmartLifecycle` interface instead.”

RU:

> «Для точного управления автоматическим запуском конкретного bean, включая фазы старта, следует использовать расширенный интерфейс `org.springframework.context.SmartLifecycle`.»

**Утверждение.** У `SmartLifecycle` существуют фазы запуска. Они задают порядок запуска уже готовых инфраструктурных компонентов; это не фазы создания bean.

**Источник:** https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/SmartLifecycle.html

EN:

> “This interface extends `Phased`, and the `getPhase()` method’s return value indicates the phase within which this `Lifecycle` component should be started and stopped.”

RU:

> «Этот интерфейс расширяет `Phased`, а значение `getPhase()` указывает фазу, в которой компонент `Lifecycle` должен запускаться и останавливаться.»

## Правильная формулировка

Для конспекта или ответа на собеседовании сформулируй так:

> **Netty transport запускается не в lifecycle-callback конкретного bean (`@PostConstruct` / `afterPropertiesSet`), а после завершения создания и инициализации всех singleton-bean’ов — в фазе автозапуска `SmartLifecycle`. В Spring Boot `WebServerStartStopLifecycle` вызывает `NettyWebServer.start()`, а тот выполняет bind порта и запускает Netty event loops.**

Или совсем кратко:

> **Фаза: startup lifecycle (`SmartLifecycle.start`) после полной инициализации bean’ов.**
