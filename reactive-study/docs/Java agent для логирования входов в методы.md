<img src="https://r2cdn.perplexity.ai/pplx-full-logo-primary-dark%402x.png" style="height:64px;margin-right:32px"/>

# Java agent для логирования входов в методы

Если цель — увидеть фактический путь выполнения запроса, включая классы Netty, Reactor Netty и WebFlux, **JDWP сам по себе не пишет журнал всех вызовов методов**. Он позволяет IDE остановиться на breakpoint или вывести сообщение через tracepoint. Для автоматического лога входов нужен bytecode‑agent либо инструмент с такой инструментализацией.

## Оглавление

- [JDWP: интерактивная отладка](#jdwp-%D0%B8%D0%BD%D1%82%D0%B5%D1%80%D0%B0%D0%BA%D1%82%D0%B8%D0%B2%D0%BD%D0%B0%D1%8F-%D0%BE%D1%82%D0%BB%D0%B0%D0%B4%D0%BA%D0%B0)
- [Java agent: автоматический лог](#java-agent-%D0%B0%D0%B2%D1%82%D0%BE%D0%BC%D0%B0%D1%82%D0%B8%D1%87%D0%B5%D1%81%D0%BA%D0%B8%D0%B9-%D0%BB%D0%BE%D0%B3)
- [Запуск через Gradle](#%D0%B7%D0%B0%D0%BF%D1%83%D1%81%D0%BA-%D1%87%D0%B5%D1%80%D0%B5%D0%B7-gradle)
- [AspectJ LTW](#aspectj-ltw)
- [Что выбрать](#%D1%87%D1%82%D0%BE-%D0%B2%D1%8B%D0%B1%D1%80%D0%B0%D1%82%D1%8C)


## JDWP: интерактивная отладка

**Утверждение.** Команда ниже запускает `bootRun` с включённой отладкой JVM и активирует профиль `local`. Процесс будет ожидать подключения отладчика по JDWP на стандартном порту `5005`.

```bat
gradlew.bat bootRun --debug-jvm --args="--spring.profiles.active=local"
```

**Источник:** https://docs.spring.io/spring-boot/docs/3.1.6-SNAPSHOT/gradle-plugin/reference/htmlsingle/\#running-your-application-with-gradle

> “To attach a remote debugger to a Spring Boot application started with Gradle, you can use the `jvmArgs` property of the `bootRun` task or `--debug-jvm` command line option.”

**RU:**

> «Чтобы подключить удалённый отладчик к приложению Spring Boot, запущенному через Gradle, можно использовать свойство `jvmArgs` задачи `bootRun` или параметр командной строки `--debug-jvm`.»

**Важно.** `--debug-jvm` включает JDWP, но не внедряет `log.info(...)` во все методы. В IntelliJ IDEA для отдельных методов используй **breakpoint с выключенным Suspend и включённым Evaluate and log**. Это подходит для нескольких известных точек: `HttpTrafficHandler.channelRead`, `DispatcherHandler.handle`, метод контроллера и репозитория.

**Источник:** https://www.jetbrains.com/help/idea/using-breakpoints.html

> “A logging breakpoint is a breakpoint that does not suspend the program but instead logs a message to the console when hit.”

**RU:**

> «Логирующий breakpoint — это breakpoint, который не приостанавливает программу, а выводит сообщение в консоль при срабатывании.»

## Java agent: автоматический лог

**Утверждение.** Для автоматического логирования входа и выхода без ручной постановки breakpoint нужен JAR с Java Instrumentation agent. JVM вызывает его метод `premain` до запуска прикладного `main`; агент регистрирует `ClassFileTransformer`, который может изменить байткод загружаемых классов.

**Источник:** https://docs.oracle.com/en/java/javase/21/docs/api/java.instrument/java/lang/instrument/package-summary.html

> “The `premain` method may be used to register `ClassFileTransformer` instances with the `Instrumentation` instance.”

**RU:**

> «Метод `premain` может использоваться для регистрации экземпляров `ClassFileTransformer` через объект `Instrumentation`.»

Минимальная точка входа агента:

```java
package example.agent;

import java.lang.instrument.Instrumentation;

public final class MethodLogAgent {

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        instrumentation.addTransformer(new MethodLogTransformer(agentArgs), true);
    }
}
```

В `META-INF/MANIFEST.MF` agent‑JAR должны быть указаны:

```text
Premain-Class: example.agent.MethodLogAgent
Can-Retransform-Classes: true
```

**Утверждение.** `Premain-Class` сообщает JVM класс, который нужно вызвать при старте агента; агент подключается параметром JVM `-javaagent:<путь-к-jar>`.

**Источник:** https://docs.oracle.com/en/java/javase/21/docs/api/java.instrument/java/lang/instrument/package-summary.html

> “The agent class must implement a public static `premain` method … The manifest of the agent JAR file must contain the `Premain-Class` attribute.”

**RU:**

> «Класс агента должен реализовать публичный статический метод `premain` … Манифест JAR-файла агента должен содержать атрибут `Premain-Class`.»

## Запуск через Gradle

**Утверждение.** В Windows передай `-javaagent` как JVM‑аргумент задачи `bootRun`; абсолютный путь с пробелами должен остаться внутри одного значения свойства.

```bat
gradlew.bat bootRun --args="--spring.profiles.active=local" -Dspring-boot.run.jvmArguments="-javaagent:C:/tools/method-log-agent.jar=packages=com.example;level=INFO"
```

**Источник:** https://docs.spring.io/spring-boot/docs/3.1.6-SNAPSHOT/gradle-plugin/reference/htmlsingle/\#running-your-application-with-gradle

> “The `bootRun` task can be configured to use the `mainClass` property.”

**RU:**

> «Задачу `bootRun` можно настроить с помощью свойства `mainClass`.»

**Уточнение.** Приведённая выше цитата не подтверждает передачу JVM‑аргументов, поэтому использовать её как основание для команды с `spring-boot.run.jvmArguments` нельзя. Надёжнее явно зафиксировать агент в `build.gradle`, где конфигурация не зависит от shell-кавычек:

```groovy
tasks.named('bootRun') {
    jvmArgs '-javaagent:C:/tools/method-log-agent.jar=packages=com.example;level=INFO'
}
```

После этого запуск:

```bat
gradlew.bat bootRun --args="--spring.profiles.active=local"
```

**Источник:** https://docs.spring.io/spring-boot/docs/3.1.6-SNAPSHOT/gradle-plugin/reference/htmlsingle/\#running-your-application-with-gradle

> “The `bootRun` task is an instance of `BootRun`.”

**RU:**

> «Задача `bootRun` является экземпляром `BootRun`.»

## AspectJ LTW

**Утверждение.** AspectJ Load-Time Weaving — альтернатива собственному `ClassFileTransformer`: `aspectjweaver` запускается как Java agent и внедряет аспект в классы при их загрузке. Это имеет смысл, если нужно логировать свои сервисы и контроллеры, а не внутренние классы Netty.

**Источник:** https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html

> “Load-time weaving is a process whereby aspects are woven into classes as they are loaded by the Java virtual machine.”

**RU:**

> «Связывание во время загрузки — это процесс, при котором аспекты внедряются в классы во время их загрузки Java Virtual Machine.»

Запуск в Windows:

```bat
gradlew.bat bootRun --args="--spring.profiles.active=local" -Dspring-boot.run.jvmArguments="-javaagent:C:/tools/aspectjweaver.jar"
```

Аспект для прикладного кода:

```java
@Aspect
@Component
public class MethodEntryLogAspect {

    private static final Logger log =
            LoggerFactory.getLogger(MethodEntryLogAspect.class);

    @Before("execution(* com.example..*(..))")
    public void logEntry(JoinPoint joinPoint) {
        log.info("-> {}.{}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName());
    }
}
```

**Ограничение.** Spring AOP без LTW работает через прокси: self-invocation, private-методы и вызовы объектов вне Spring context не перехватываются. AspectJ LTW снимает часть этих ограничений, но создаёт заметный overhead, поэтому pointcut нужно ограничивать своим пакетом, например `com.example..*`.

**Источник:** https://docs.spring.io/spring-framework/reference/core/aop/proxying.html

> “Once the call has finally reached the target object, any method calls that it may make on itself, such as `this.bar()` or `this.foo()`, are going to be invoked against the `this` reference, and not the proxy.”

**RU:**

> «Когда вызов достиг целевого объекта, любые вызовы его собственных методов, например `this.bar()` или `this.foo()`, выполняются через ссылку `this`, а не через proxy.»

## Что выбрать

| Цель | Практичный вариант |
| :-- | :-- |
| Узнать путь одного запроса по заранее известным точкам | `--debug-jvm` + logging breakpoints IntelliJ |
| Писать в лог вход в методы собственных `Controller`/`Service` | Spring AOP с узким pointcut |
| Перехватывать self-invocation, private‑методы и классы вне Spring proxy | AspectJ LTW |
| Наблюдать Netty / Reactor Netty / WebFlux классы без изменения библиотек | **InitPathAgent** (`docs/block0-verify/`) или отладчик / profiler |
| Логировать все методы JVM | Не рекомендуется: огромный объём логов, сильный overhead и риск нарушить timing reactive event loop |

## InitPathAgent в reactive-study

Пошаговая инструкция **для пользователя** (настройка классов/методов, запуск, разбор log): [`Java agent — проверка пути вызовов своими руками.md`](Java%20agent%20—%20проверка%20пути%20вызовов%20своими%20руками.md).

Код agent: `docs/block0-verify/agent/InitPathAgent.java`. Пример trace после init: `block0-init-trace.log`. Итоговый doc: [`BLOCK-0-INIT-PATH-VERIFICATION.md`](BLOCK-0-INIT-PATH-VERIFICATION.md).

<span style="display:none">[^1]</span>

<div align="center">⁂</div>

[^1]: kak-zapusit-s-Java-agentom-chtoby-proverit-v-kaki.md

