# Java agent — проверка пути вызовов своими руками

Как **самому** настроить agent, указать классы и методы, запустить приложение, получить trace-лог и **разобрать**, через какие методы прошёл сценарий (init, HTTP-запрос и т.д.).

Теория (JDWP vs agent): [`Java agent для логирования входов в методы.md`](Java%20agent%20для%20логирования%20входов%20в%20методы.md).

---

## 1. Что вы получите

Agent **не останавливает** программу. При входе в выбранные методы он дописывает в файл строки вида:

```text
>>> ENTER reactor.netty.http.server.HttpTrafficHandler#channelRead
    at ...
    at org.springframework.web.reactive.DispatcherHandler.handle(...)
    ...
```

Вы сами:

1. Задаёте **какие** классы и методы логировать.
2. Запускаете приложение с agent.
3. Выполняете действие (старт app, один `curl`, …).
4. Открываете log-файл и смотрите **порядок** `>>> ENTER`.

Agent собирает «сырой» trace; **анализ** — за вами.

---

## 2. Где лежит agent в проекте

```text
reactive-study/docs/block0-verify/
├── agent/
│   ├── InitPathAgent.java      ← правите фильтры здесь
│   └── build/init-path-agent.jar   ← после сборки
├── build_agent.py              ← сборка (рекомендуется)
└── build-agent.cmd             ← альтернатива
```

---

## 3. Шаг 1 — указать классы и методы

Откройте `InitPathAgent.java`. Два списка:

**`TARGETS`** — internal name класса (слэши вместо точек):

```java
"org/springframework/web/reactive/DispatcherHandler",
"reactor/netty/http/server/HttpTrafficHandler",
"com/example/reactivestudy/web/UserController",
```

**`METHODS`** — имена методов **без** аргументов:

```java
"handle",
"channelRead",
"getUser",
"findById",
```

Правила:

- Чем уже список — тем проще читать log.
- Не добавляйте `<init>` без нужды.
- FQCN для breakpoint в IDE ищите через **Navigate → Declaration** или `javap`; abstract-класс в доке может отличаться от runtime (например `HttpServer` vs `HttpServerBind`).

### Пример A: init транспорта (уже в agent)

Классы Spring Boot + Reactor Netty + Netty; методы `start`, `bindNow`, `doBeginRead`, …

### Пример B: один HTTP-запрос

Добавьте в `TARGETS`:

```java
"reactor/netty/transport/ServerTransport$Acceptor",
"reactor/netty/http/server/HttpTrafficHandler",
"org/springframework/web/reactive/DispatcherHandler",
"org/springframework/web/reactive/result/method/annotation/RequestMappingHandlerAdapter",
// ваш пакет:
"com/example/reactivestudy/web/UserController",
```

В `METHODS`:

```java
"channelRead",
"handle",
"handleRequest",   // если есть в вашей версии Spring
"getUser",         // метод контроллера
```

---

## 4. Шаг 2 — собрать agent

```bat

cd D:\Project_infra\greeting-service-infra\reactive-study\docs\block0-verify
python build_agent.py
```

Должен появиться: `agent\build\init-path-agent.jar`.

---

## 5. Шаг 3 — запустить приложение с agent

### Вариант A — IntelliJ IDEA (удобнее)

1. Run Configuration → `ReactiveStudyApplication` (или `bootRun`).
2. **VM options:**

```text
-javaagent:D:\Project_infra\greeting-service-infra\reactive-study\docs\block0-verify\agent\build\init-path-agent.jar
-Dblock0.agent.log=D:\Project_infra\greeting-service-infra\reactive-study\docs\block0-verify\agent\my-trace.log
```

3. Active profiles: `local`.
4. Run → дождаться `Started ReactiveStudyApplication`.
5. Для HTTP: выполнить один запрос, например:

```bat

curl http://localhost:8083/api/users/1
```

6. Остановить приложение (Stop). Trace дописан в `my-trace.log`.

### Вариант B — JAR из командной строки

```bat

cd D:\Project_infra\greeting-service-infra\reactive-study
gradlew.bat bootJar

"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe" ^
  -javaagent:D:\Project_infra\greeting-service-infra\reactive-study\docs\block0-verify\agent\build\init-path-agent.jar ^
  -Dblock0.agent.log=D:\Project_infra\greeting-service-infra\reactive-study\docs\block0-verify\agent\my-trace.log ^
  -jar build\libs\reactive-study.jar --spring.profiles.active=local
```

В другом окне — `curl` после старта. Остановить: Ctrl+C.

### Вариант C — скрипт `run-with-agent.cmd`

Опционально: автоматизирует сборку + один прогон init. Для **своего** фильтра и HTTP лучше A или B — вы контролируете момент `curl`.

---

## 6. Шаг 4 — открыть log

Путь задан в `-Dblock0.agent.log=...`. По умолчанию (если свойство не задано): `block0-init-trace.log` в текущей директории JVM.

Только маркеры входа:

```bat

findstr /R /C:"^>>> ENTER" agent\my-trace.log
```

---

## 7. Как читать trace

| Строка | Значение |
|--------|----------|
| `InitPathAgent started, log=...` | agent подключился, путь к файлу |
| `>>> ENTER class#method` | вы **вошли** в метод |
| `at ...` под ENTER | стек вызовов — **кто вызвал** этот метод |
| пустая строка | конец одного события |

**Порядок `>>> ENTER` сверху вниз** — порядок вызовов (если сценарий в основном однопоточный; при HTTP boss/worker порядок может перемешиваться между потоками — смотрите stack под каждым ENTER).

**Если метода нет в log** — он не вызывался в вашем сценарии, или класс/имя не в фильтре, или опечатка в `TARGETS`.

**Дубликаты** одного `#method` — нормально (overload, повторный bind и т.д.).

---

## 8. Пример фрагмента log (init транспорта)

```text
>>> ENTER org.springframework.boot.reactor.netty.NettyWebServer#start
>>> ENTER reactor.netty.transport.ServerTransport#bindNow
>>> ENTER reactor.netty.http.server.HttpServerBind#bind
>>> ENTER reactor.netty.transport.TransportConnector#bind
>>> ENTER reactor.netty.resources.DefaultLoopResources#onServerSelect
>>> ENTER reactor.netty.transport.TransportConnector#doInitAndRegister
>>> ENTER io.netty.channel.nio.AbstractNioChannel#doBeginRead
```

По trace видно, что bind в этом приложении идёт через `ServerTransport` / `HttpServerBind` и `TransportConnector#bind` — эти FQCN сверяйте с [Reactor Netty API](https://projectreactor.io/docs/netty/release/api/) и javap по JAR 1.3.4.

Полный пример: `block0-verify/agent/block0-init-trace.log`.

---

## 9. Типичные проблемы

| Проблема | Решение |
|----------|---------|
| Приложение падает с `IllegalAccessError: logEntry` | в `InitPathAgent.java` метод `logEntry` должен быть `public static`, пересобрать JAR |
| В log только `InitPathAgent started` | класс не в `TARGETS` или метод не в `METHODS`; проверить internal name |
| Trace обрывается до конца | порт занят, БД недоступна — смотреть консоль Spring |
| Огромный файл | сузить `TARGETS` / `METHODS` |
| Не тот класс в ENTER | runtime-класс другой (impl); скорректировать фильтр и документ |

---

## 10. Связь с документацией

После разбора log можно заполнить таблицу stop points в учебном markdown (класс, метод, срабатывает при boot / при curl).

Пример готового doc по init: [`BLOCK-0-INIT-PATH-VERIFICATION.md`](../../reactive-study/docs/BLOCK-0-INIT-PATH-VERIFICATION.md).
