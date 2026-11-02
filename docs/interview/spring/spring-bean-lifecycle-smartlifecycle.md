# Полный разбор жизненного цикла Spring Bean, сборки приложения и SmartLifecycle

> Документ перепроверён по первоисточникам: официальная документация Spring Framework и Spring Boot, javadoc Spring Kafka, исходный код Spring Boot. Найдены и исправлены три фактические ошибки исходника (в частях 3 и 4) и уточнены рекомендации части 2.

## Оглавление

- [Результат проверки](#результат-проверки)
- [Часть 1. От исходного кода до сборки приложения](#часть-1-от-исходного-кода-до-сборки-приложения)
- [Часть 2. Полный жизненный цикл Spring Bean](#часть-2-полный-жизненный-цикл-spring-bean)
- [Часть 3. Где поднимается веб-сервер](#часть-3-где-поднимается-веб-сервер)
- [Часть 4. SmartLifecycle](#часть-4-smartlifecycle)

## Результат проверки

| Утверждение исходного документа | Вердикт |
|---|---|
| Часть 1: сборка в fat JAR, порядок bootstrap (Environment → контекст) | Верно, подтверждено |
| Часть 2: порядок шагов 1–8 (Instantiation → DI → Aware → BPP → init → BPP → ready → destroy) | Верен; исправлена рекомендация по `@PostConstruct` |
| Часть 3: «onRefresh() — финальная стадия refresh(), к этому моменту все бины уже созданы» | **Неверно**: сервер *создаётся* в `onRefresh()` ещё до создания остальных синглтонов, но *стартует* позже — в `finishRefresh()` через SmartLifecycle |
| Часть 3: «сервер принимает трафик только после инициализации всех бинов» | Верно по эффекту, но механизм другой |
| Часть 4: «веб-контекст — фаза 0» | **Неверно**: фаза 0 — дефолт для обычных `Lifecycle`; старт/стоп сервера — специальная фаза чуть ниже `Integer.MAX_VALUE` |
| Часть 4: консьюмеры (фаза `Integer.MAX_VALUE`) стартуют после веб-сервера, останавливаются раньше | Верно |
| Цитата-сноска [2] (Spring docs) | Дословно подтверждена |
| Цитата-сноска [1] (HowToDoInJava) | В статье такой фразы **нет**, реальная формулировка приведена в Части 2 |

## Часть 1. От исходного кода до сборки приложения

### Build Phase

**Утверждение:** Maven/Gradle собирают приложение в один исполняемый архив, содержащий все зависимости; запуск — командой `java -jar`.

**Источник:** https://docs.spring.io/spring-boot/maven-plugin/packaging.html

EN:

> "The plugin can create executable archives (jar files and war files) that contain all of an application's dependencies and can then be run with `java -jar`."

RU:

> «Плагин может создавать исполняемые архивы (jar- и war-файлы), содержащие все зависимости приложения, которые затем можно запускать командой `java -jar`.»

**Утверждение (уточнение к «практической пользе»):** изоляция зависимостей — это не «просто JAR», а собственный формат Spring Boot: Java стандартно не умеет загружать вложенные jar-файлы, поэтому Boot использует вложенную структуру (`BOOT-INF/classes`, `BOOT-INF/lib`) и собственный загрузчик классов.

**Источник:** https://docs.spring.io/spring-boot/specification/executable-jar/nested-jars.html

EN:

> "Java does not provide any standard way to load nested jar files (i.e. jar files that are themselves contained within a jar). ... Spring Boot takes a different approach and lets you actually nest jars directly."

RU:

> «Java не предоставляет стандартного способа загрузки вложенных jar-файлов (то есть jar-файлов, которые сами находятся внутри jar). ... Spring Boot идёт другим путём и позволяет вкладывать jar-файлы напрямую.»

### Runtime Phase

**Утверждение:** `Environment` с конфигурацией из `application.yml` готов **до** создания `ApplicationContext` — это часть последовательности событий запуска `SpringApplication.run()`.

**Источник:** https://docs.spring.io/spring-boot/reference/features/spring-application.html

EN:

> "An `ApplicationEnvironmentPreparedEvent` is sent when the `Environment` to be used in the context is known but before the context is created."

RU:

> «Событие `ApplicationEnvironmentPreparedEvent` отправляется, когда `Environment`, который будет использоваться в контексте, уже известен, но до создания самого контекста.»

**Утверждение (дополнение к фазе разрушения):** вызывать `context.close()` вручную в Spring Boot не обязательно — каждый `SpringApplication` сам регистрирует shutdown hook в JVM, поэтому `@PreDestroy`/`DisposableBean` срабатывают и при обычном завершении процесса (в т.ч. при SIGTERM в Kubernetes).

**Источник:** https://docs.spring.io/spring-boot/reference/features/spring-application.html

EN:

> "Each `SpringApplication` registers a shutdown hook with the JVM to ensure that the `ApplicationContext` closes gracefully on exit. All the standard Spring lifecycle callbacks (such as the `DisposableBean` interface or the `@PreDestroy` annotation) can be used."

RU:

> «Каждый `SpringApplication` регистрирует в JVM shutdown hook, чтобы гарантировать корректное закрытие `ApplicationContext` при выходе. Можно использовать все стандартные колбэки жизненного цикла Spring (интерфейс `DisposableBean`, аннотацию `@PreDestroy` и т.д.).»

## Часть 2. Полный жизненный цикл Spring Bean

### Шаги 1–8: что подтверждено

**Утверждение (порядок инициализационных колбэков, шаг 5):**

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

EN:

> "Multiple lifecycle mechanisms configured for the same bean, with different initialization methods, are called as follows: 1. Methods annotated with `@PostConstruct` 2. `afterPropertiesSet()` as defined by the `InitializingBean` callback interface 3. A custom configured `init()` method"

RU:

> «Несколько механизмов жизненного цикла, настроенных для одного бина с разными методами инициализации, вызываются в следующем порядке: 1. Методы с аннотацией `@PostConstruct` 2. `afterPropertiesSet()` из интерфейса `InitializingBean` 3. Пользовательский сконфигурированный метод `init()`.»

**Утверждение (порядок колбэков разрушения, шаг 8): `@PreDestroy` → `DisposableBean.destroy()` → кастомный `destroy-method` — верен.**

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

EN:

> "Destroy methods are called in the same order: 1. Methods annotated with `@PreDestroy` 2. `destroy()` as defined by the `DisposableBean` callback interface 3. A custom configured `destroy()` method"

RU:

> «Методы разрушения вызываются в том же порядке: 1. Методы с аннотацией `@PreDestroy` 2. `destroy()` из интерфейса `DisposableBean` 3. Пользовательский сконфигурированный метод `destroy()`.»

**Утверждение (шаг 3, Aware):** момент вызова `BeanNameAware`/`ApplicationContextAware` — после внедрения обычных свойств, но до колбэков инициализации.

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

EN:

> "The callback is invoked after population of normal bean properties but before an initialization callback such as `InitializingBean.afterPropertiesSet()` or a custom init-method."

RU:

> «Колбэк вызывается после заполнения обычных свойств бина, но до колбэка инициализации, такого как `InitializingBean.afterPropertiesSet()` или пользовательский init-метод.»

**Утверждение (шаг 6, прокси/AOP):** `postProcessAfterInitialization()` действительно используется для обёртки бина в AOP-прокси — именно поэтому init-колбэки работают на «сыром» объекте, а не на прокси.

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

EN:

> "The initialization callback is called on the raw bean reference, which means that AOP interceptors and so forth are not yet applied to the bean. A target bean is fully created first and then an AOP proxy (for example) with its interceptor chain is applied."

RU:

> «Колбэк инициализации вызывается на ссылке на исходный бин, то есть AOP-перехватчики и прочее ещё не применены к бину. Целевой бин сначала полностью создаётся, и лишь затем к нему применяется AOP-прокси (например) со своей цепочкой перехватчиков.»

**Утверждение (шаг 7, уточнение):** бин публикуется для других бинов как «полностью инициализированный» только после завершения `@PostConstruct`.

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

EN:

> "The bean instance is only considered as fully initialized and ready to be published to others after returning from the `@PostConstruct` method."

RU:

> «Экземпляр бина считается полностью инициализированным и готовым к публикации другим бинам только после возврата из метода `@PostConstruct`.»

### Исправления и дополнения к части 2

**Исправление к шагу 5 («идеальное место для грелки кэша»):** рекомендация завышена. Методы инициализации выполняются **под блокировкой создания синглтонов**, и тяжёлая работа (обращения к другим бинам, внешние API) там официально не рекомендуется — есть риск deadlock инициализации. Для этого предназначены `SmartInitializingSingleton`, `ContextRefreshedEvent`, `SmartLifecycle` или раннеры.

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

EN:

> "Be aware that `@PostConstruct` and initialization methods in general are executed within the container's singleton creation lock. ... Such individual initialization methods are only meant for validating the configuration state and possibly preparing some data structures based on the given configuration but no further activity with external bean access. Otherwise there is a risk for an initialization deadlock."

RU:

> «Имейте в виду, что `@PostConstruct` и методы инициализации в целом выполняются под блокировкой создания синглтонов контейнера. ... Такие методы инициализации предназначены только для валидации состояния конфигурации и, возможно, подготовки структур данных на её основе, но не для какой-либо активности с обращением к внешним бинам. Иначе есть риск deadlock при инициализации.»

**Источник:** https://docs.spring.io/spring-boot/reference/features/spring-application.html

EN:

> "Tasks expected to run during startup should be executed by `CommandLineRunner` and `ApplicationRunner` components instead of using Spring component lifecycle callbacks such as `@PostConstruct`."

RU:

> «Задачи, которые должны выполняться при старте, следует выполнять в компонентах `CommandLineRunner` и `ApplicationRunner`, а не в колбэках жизненного цикла Spring-компонентов вроде `@PostConstruct`.»

**Дополнение к шагу 8:** для prototype-бинов destroy-колбэки не вызываются вообще — это ответственность приложения.

**Источник:** https://howtodoinjava.com/spring-core/spring-bean-life-cycle/

EN:

> "In Spring, the initialization life-cycle callback methods will be called on objects regardless of bean scope, while for beans with prototype scope, the destruction life-cycle callback methods will not be called."

RU:

> «В Spring методы инициализации вызываются для объектов независимо от scope бина, тогда как для бинов со scope prototype методы разрушения вызваны не будут.»

**Замечание о сноске [1]:** фраза «The Spring bean life cycle is the sequence of steps the Spring container runs for every bean…» в статье HowToDoInJava дословно отсутствует. Реальная формулировка статьи:

EN:

> "The lifecycle of a bean in the Spring Framework refers to the series of steps and methods that a bean goes through from its instantiation to its eventual destruction."

RU:

> «Жизненный цикл бина в Spring Framework — это последовательность шагов и методов, через которые проходит бин от своего создания до окончательного уничтожения.»

## Часть 3. Где поднимается веб-сервер

### Исправление главного утверждения

«onRefresh() — финальная стадия refresh(), к этому моменту все бизнес-бины уже созданы» — **неверно**. В `refresh()` контекста вызов `onRefresh()` происходит **до** `finishBeanFactoryInitialization()` (создание остальных синглтонов). `ServletWebServerApplicationContext` в `onRefresh()` лишь **создаёт** `WebServer` (Tomcat), когда большинство бизнес-бинов ещё не существует; фактический **старт** (bind порта, приём трафика) происходит позже — в `finishRefresh()` через `LifecycleProcessor`, запускающий SmartLifecycle-бин `WebServerStartStopLifecycle`.

**Источник (создание сервера в onRefresh, регистрация lifecycle-бина):** https://dev.to/silver_dev/spring-boot-under-the-hood-part-7-from-embedded-tomcat-to-your-restcontroller-33kk

EN:

> "Once the `WebServer` is created, a lifecycle bean is registered:
> `getBeanFactory().registerSingleton("webServerStartStop", new WebServerStartStopLifecycle(this, this.webServer));`"

RU:

> «Когда `WebServer` создан, регистрируется lifecycle-бин:
> `getBeanFactory().registerSingleton("webServerStartStop", new WebServerStartStopLifecycle(this, this.webServer));`»

**Источник (фактический старт через SmartLifecycle):** https://dev.to/silver_dev/spring-boot-under-the-hood-part-7-from-embedded-tomcat-to-your-restcontroller-33kk

EN:

> "SmartLifecycle callbacks fire in `finishRefresh()` → `lifecycleProcessor.onRefresh()`."

RU:

> «Колбэки SmartLifecycle срабатывают в `finishRefresh()` → `lifecycleProcessor.onRefresh()`.»

### Официальное подтверждение момента старта

Колбэк «refresh» процессора жизненного цикла — который и запускает веб-сервер — вызывается строго после создания и инициализации **всех** объектов.

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

EN:

> "When the context is refreshed (after all objects have been instantiated and initialized), that callback is invoked."

RU:

> «Когда контекст обновляется (после того как все объекты созданы и инициализированы), вызывается этот колбэк.»

**Вывод:** практический эффект исходного документа **верен** — веб-сервер начинает принимать трафик только после полной инициализации всех бинов, — но гарантирует это не «финальность onRefresh()», а то, что старт сервера выполняется как SmartLifecycle-фаза в `finishRefresh()`. Важное следствие: веб-сервер — это **сам** SmartLifecycle-бин, т.е. части 3 и 4 документа связаны напрямую.

### Дополнение (для Kubernetes)

Готовность принимать трафик на уровне платформы определяется ещё позже — после выполнения раннеров, что отражается в событии `ApplicationReadyEvent` / readiness-пробах.

**Источник:** https://docs.spring.io/spring-boot/reference/features/spring-application.html

EN:

> "An application is considered ready as soon as application and command-line runners have been called."

RU:

> «Приложение считается готовым, как только вызваны раннеры приложения и командной строки.»

## Часть 4. SmartLifecycle

### Подтверждение определения

**Утверждение (подтверждено):** SmartLifecycle — это не «фаза жизненного цикла бина», а расширение интерфейса `Lifecycle` для упорядоченного старта/остова компонентов на уровне всего контекста.

**Источник:** https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/SmartLifecycle.html

EN:

> "An extension of the `Lifecycle` interface for those objects that require to be started upon `ApplicationContext` refresh and/or shutdown in a particular order."

RU:

> «Расширение интерфейса `Lifecycle` для объектов, которые должны запускаться при обновлении `ApplicationContext` и/или останавливаться в определённом порядке.»

### Исправление фаз («веб-контекст — фаза 0»)

Фаза 0 — дефолт для **обычных** `Lifecycle`-бинов. У `SmartLifecycle` фаза по умолчанию — `Integer.MAX_VALUE`, а у старта/остова веб-сервера — своя константа, расположенная **чуть ниже** `Integer.MAX_VALUE` (в Boot 4 — `WebServerApplicationContext.START_STOP_LIFECYCLE_PHASE`).

**Источник:** https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/context/SmartLifecycle.html

EN:

> "The default phase for `SmartLifecycle`: `Integer.MAX_VALUE`. This is different from the common phase `0` associated with regular `Lifecycle` implementations, putting the typically auto-started `SmartLifecycle` beans into a later startup phase and an earlier shutdown phase."

RU:

> «Фаза по умолчанию для `SmartLifecycle`: `Integer.MAX_VALUE`. Она отличается от общей фазы `0`, связанной с обычными реализациями `Lifecycle`, из-за чего типично автозапускаемые бины `SmartLifecycle` попадают в более позднюю фазу старта и более раннюю фазу остановки.»

**Источник:** https://docs.spring.io/spring-boot/4.0/api/java/org/springframework/boot/web/server/context/WebServerApplicationContext.html

EN:

> "`START_STOP_LIFECYCLE_PHASE` — `SmartLifecycle` phase in which starting and stopping of the web server is performed."

RU:

> «`START_STOP_LIFECYCLE_PHASE` — фаза `SmartLifecycle`, в которой выполняется запуск и остановка веб-сервера.»

**Источник (конкретное значение для Boot 3.1+):** https://stackoverflow.com/questions/78347412/springboot-changing-smartlifecycle-phase-of-the-embedded-container

EN:

> "In all currently supported versions (3.1.x and later) the phase is `SmartLifecycle.DEFAULT_PHASE - 1024`."

RU:

> «Во всех поддерживаемых версиях (3.1.x и новее) фаза [graceful shutdown веб-сервера] равна `SmartLifecycle.DEFAULT_PHASE - 1024`.»

### Правило порядка фаз

**Утверждение:** старт — от меньшей фазы к большей, остановка — в обратном порядке.

**Источник:** https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/SmartLifecycle.html

EN:

> "The startup process begins with the lowest phase value and ends with the highest phase value (`Integer.MIN_VALUE` is the lowest possible, and `Integer.MAX_VALUE` is the highest possible). The shutdown process will apply the reverse order."

RU:

> «Процесс запуска начинается с наименьшего значения фазы и заканчивается наибольшим (`Integer.MIN_VALUE` — минимально возможное, `Integer.MAX_VALUE` — максимально возможное). Процесс остановки применяется в обратном порядке.»

### Kafka

**Утверждение:** контейнеры слушателей Spring Kafka действительно реализуют `SmartLifecycle`.

**Источник:** https://docs.spring.io/spring-kafka/api/org/springframework/kafka/listener/MessageListenerContainer.html

EN:

> "public interface MessageListenerContainer extends SmartLifecycle, DisposableBean"

RU:

> «public interface MessageListenerContainer extends SmartLifecycle, DisposableBean» (интерфейс контейнера слушателей расширяет `SmartLifecycle`).

### Исправленная итоговая схема для примера «Kafka + HTTP»

Старт:

1. Все синглтоны созданы (`finishBeanFactoryInitialization`).
2. SmartLifecycle по возрастанию фазы: … → веб-сервер (фаза чуть ниже `MAX`) → консьюмеры Kafka (фаза по умолчанию `Integer.MAX_VALUE`).
3. Раннеры (`CommandLineRunner`/`ApplicationRunner`) → `ApplicationReadyEvent` → readiness.

Стоп (по убыванию фазы):

1. Консьюмеры Kafka (`MAX`) останавливаются первыми.
2. Graceful shutdown и остановка веб-сервера.
3. Destroy-колбэки бинов (`@PreDestroy` → `DisposableBean` → `destroy-method`) → закрытие пулов БД.

То есть практический вывод исходного документа (консьюмеры стартуют после веб-контекста, а при выключении сначала корректно останавливается Kafka и только потом закрывается БД) — **верен**, но достигается схемой фаз «чуть ниже MAX» (сервер) против `MAX` (консьюмеры), а не «фазой 0».

### Стоп до destroy, таймауты, автозапуск

**Утверждение (stop до destroy):** stop-сигналы `SmartLifecycle` гарантированно предшествуют destroy-колбэкам, и контейнер дожидается завершения stop-обработки.

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

EN:

> "For extended shutdown phases, you may implement the `Lifecycle` interface and receive an early stop signal before the destroy methods of any singleton beans are called. You may also implement `SmartLifecycle` for a time-bound stop step where the container will wait for all such stop processing to complete before moving on to destroy methods."

RU:

> «Для расширенных фаз завершения можно реализовать интерфейс `Lifecycle` и получить ранний stop-сигнал до вызова destroy-методов любых синглтон-бинов. Можно также реализовать `SmartLifecycle` для ограниченного по времени шага остановки, при котором контейнер дождётся завершения всей такой stop-обработки, прежде чем перейти к destroy-методам.»

**Утверждение (механика асинхронного стопа и таймаут):** реализация `stop(Runnable)` обязана вызвать колбэк по завершении; `DefaultLifecycleProcessor` ждёт каждую фазу до таймаута (по умолчанию 30 секунд на фазу).

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

EN:

> "Any implementation must invoke that callback's `run()` method after that implementation's shutdown process is complete. ... the default implementation of the `LifecycleProcessor` interface, `DefaultLifecycleProcessor`, waits up to its timeout value for the group of objects within each phase to invoke that callback. The default per-phase timeout is 30 seconds."

RU:

> «Любая реализация обязана вызвать метод `run()` этого колбэка после завершения своего процесса остановки. ... реализация `LifecycleProcessor` по умолчанию, `DefaultLifecycleProcessor`, ожидает в пределах своего таймаута, пока группа объектов в каждой фазе вызовет этот колбэк. Таймаут по умолчанию — 30 секунд на фазу.»

**Утверждение (автозапуск):** бины `SmartLifecycle` запускаются автоматически при refresh (если `isAutoStartup() == true`), без явного `context.start()`.

**Источник:** https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html

EN:

> "When the context is refreshed (after all objects have been instantiated and initialized), that callback is invoked. At that point, the default lifecycle processor checks the boolean value returned by each `SmartLifecycle` object's `isAutoStartup()` method. If `true`, that object is started at that point rather than waiting for an explicit invocation of the context's or its own `start()` method."

RU:

> «Когда контекст обновляется (после того как все объекты созданы и инициализированы), вызывается этот колбэк. В этот момент процессор жизненного цикла по умолчанию проверяет булево значение, возвращаемое методом `isAutoStartup()` каждого объекта `SmartLifecycle`. Если оно `true`, объект запускается в этот момент, не дожидаясь явного вызова `start()` самого контекста или самого объекта.»

Полезная деталь для практики: некоторые компоненты Spring имеют собственную дефолтную фазу — например, исполнители/шедулеры используют `Integer.MAX_VALUE / 2`, то есть стартуют раньше дефолтных `SmartLifecycle`-бинов и останавливаются позже них.
