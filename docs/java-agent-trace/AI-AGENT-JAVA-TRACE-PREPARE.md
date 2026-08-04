# Runtime-проверка пути вызовов — выжимка для AI-агентов

Документ для **другого AI-агента**: что мы уже прошли, какие ошибки повторять не нужно, как помогать пользователю **без** бесконечных перезапусков приложения.

Инструкция **для пользователя** (как самому запустить agent и копать лог): [`Java agent — проверка пути вызовов своими руками.md`](Java%20agent%20—%20проверка%20пути%20вызовов%20своими%20руками.md).

---

## 1. Роль агента vs роль пользователя

| Кто | Что делает |
|-----|------------|
| **Пользователь** | правит `TARGETS`/`METHODS`, собирает agent, запускает app с `-javaagent`, шлёт curl / перезапускает app, **сам читает** trace-лог |
| **AI-агент** | помогает составить список классов/методов, проверить FQCN через `javap`, исправить agent при ошибках сборки, **интерпретировать** лог если попросят — **не** гонять `bootRun` часами в цикле |

CMD-скрипты в `block0-verify/` (`run-with-agent.cmd`) — **вспомогательная автоматизация**, не главный продукт. Пользователю достаточно IntelliJ + `-javaagent` или одной команды `java -jar`.

---

## 2. Типичная задача пользователя

- Зафиксировать **фактический** путь вызовов в running application (init, HTTP-запрос, вызов API библиотеки).
- Сверить классы и методы с **официальной API-документацией** и с **javap** по JAR той версии, что реально в проекте.
- Задокументировать stop points (FQCN + метод) только с опорой на **runtime trace + официальный API / javap**.

---

## 3. Порядок работы (не переставлять)

```text
1. официальный API + javap  →  FQCN, сигнатуры, artifact (статика)
2. правка InitPathAgent (TARGETS + METHODS)
3. build_agent.py  →  init-path-agent.jar
4. ОДИН прогон пользователя  →  trace.log
5. разбор >>> ENTER  →  сверка с API/javap  →  правка markdown
```

**Не делать:** писать FQCN в документ → потом «проверить» десять раз через `bootRun`.  
**Не делать:** каждый раз поднимать приложение на 2+ часа — `bootRun` без stop зависает; для init достаточно одного старта до `Started ...`.

---

## 4. Ошибки, которые мы уже ловили (не повторять)

### 4.1 FQCN в документе без проверки (исправлено javap + trace)

| Ошибка в черновике | Что показали javap / trace |
|--------------------|----------------------------|
| `reactor.netty.http.server.HttpServer#bindNow` | `reactor.netty.transport.ServerTransport#bindNow`; runtime: `HttpServerBind` |
| `reactor.netty.http.server.HttpResources#get` | `reactor.netty.http.HttpResources#get` |
| `TransportConnector#bind` как instance | **public static** |
| breakpoint на abstract `HttpServer` | в trace: `ServerTransport` / `HttpServerBind` |

Метод часто в **родительском классе** или **другом JAR** (`reactor-netty-core` vs `reactor-netty-http`). `javap_verify.py` → `NOT FOUND` = искать в API родителя / другом artifact, не выдумывать.

### 4.2 InitPathAgent

| Ошибка | Fix |
|--------|-----|
| `IllegalAccessError: logEntry` | `logEntry` → **`public static`** |
| Падение при старте с `<init>` в METHODS | убрать `<init>` из фильтра |
| `build-agent.cmd` падает | использовать `build_agent.py` (очистка `classes/`) |

### 4.3 Окружение

| Ошибка | Следствие |
|--------|-----------|
| Порт 8083 занят | bind fail, trace обрывается до `doBeginRead` |
| PostgreSQL :5434 недоступен | Flyway не даёт дойти до Netty bind |
| `bootRun` 2h+ → exit 1 | процесс убит; app **успевал** стартовать — смотреть лог, не перезапускать слепо |

---

## 5. Что даёт trace-лог (источник истины для порядка)

Формат: `>>> ENTER fqcn#method` + stack trace.  
Порядок строк = хронология (в одном потоке сценария).

**Пример init (проверено trace + javap, SB 4.0.5 / RN 1.3.4):**  
`getWebServer` → `start` → `startHttpServer` → `ServerTransport#bindNow` → `HttpServerBind#bind` → `childEventLoopGroup`/`onServer` → `TransportConnector#bind` → `onServerSelect` → `doInitAndRegister` → `doBeginRead`.

Если класс/метод **есть в официальном API**, но **нет** `>>> ENTER` в trace для данного сценария — в документе помечать «не вызывается при этом trigger», а не «устаревший tutoriaл».

Полный лог: `docs/block0-verify/agent/block0-init-trace.log`.

---

## 6. Инструменты в репозитории

| Путь | Назначение |
|------|------------|
| `block0-verify/agent/InitPathAgent.java` | шаблон agent; править TARGETS/METHODS |
| `block0-verify/build_agent.py` | сборка JAR (предпочтительно) |
| `block0-verify/javap_verify.py` | статика по dependency JAR |
| `block0-verify/run-with-agent.cmd` | опционально; пользователь может не использовать |
| `BLOCK-0-INIT-PATH-VERIFICATION.md` | пример итогового doc после trace |

---

## 7. HTTP-запрос (следующий сценарий)

Тот же agent, другой фильтр + trigger:

- **TARGETS:** `HttpTrafficHandler`, `DispatcherHandler`, `*Controller`, `*Service`, …
- **METHODS:** `channelRead`, `handle`, методы контроллера
- **Trigger:** один `curl` после `Started ...`

Пользователь сам пересобирает agent и копает лог — агент только подсказывает список классов и сверяет с javap.

---

## 8. Чеклист для AI-агента перед правкой документации

- [ ] FQCN сверены с javap и официальным API, не по памяти
- [ ] Есть runtime trace от пользователя (один controlled run)
- [ ] Таблица stop points: trace ✅/❌ + ссылка на API/javap

---

## 9. Чего не делать

- Не запускать `bootRun` / `run-with-agent.cmd` многократно «пока не угадаю».
- Не подменять runtime-проверку пользователя автоматическими CMD без запроса.
- Не писать инструкцию пользователю в виде «агент сам всё прогонит» — пользователь **владелец** trace-лога.
- Не трогать `HTTP-REQUEST-DEBUG-BREAKPOINTS - correct v5.md` без разрешения.
