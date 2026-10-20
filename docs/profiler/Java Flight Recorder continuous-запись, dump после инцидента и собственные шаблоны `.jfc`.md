# Java Flight Recorder continuous-запись, dump после инцидента и собственные шаблоны `.jfc`

## Оглавление

- [1. Короткий ответ](#1-короткий-ответ)
- [2. Что происходит во время работы JVM](#2-что-происходит-во-время-работы-jvm)
- [3. Как расследовать инцидент из Grafana](#3-как-расследовать-инцидент-из-grafana)
- [4. Какие бывают записи JFR](#4-какие-бывают-записи-jfr)
- [5. Что такое шаблон `.jfc`](#5-что-такое-шаблон-jfc)
- [6. Готовые шаблоны: `default` и `profile`](#6-готовые-шаблоны-default-и-profile)
- [7. Как создать собственный шаблон](#7-как-создать-собственный-шаблон)
- [8. Настройка событий: что именно меняется](#8-настройка-событий-что-именно-меняется)
- [9. Пример production-конфигурации](#9-пример-production-конфигурации)
- [10. Практическая схема для микросервиса](#10-практическая-схема-для-микросервиса)
- [11. Границы применимости JFR](#11-границы-применимости-jfr)

## 1. Короткий ответ

Да: Java Flight Recorder (далее — JFR; `GFR` в вопросе, вероятно, оговорка) можно запустить вместе с микросервисом как **постоянную кольцевую запись**. JVM будет заранее собирать разрешённые события: GC, сэмплы исполнения, ожидания блокировок, I/O-события, аллокации — только те, которые включены шаблоном. Она хранит не бесконечную историю, а последнее ограниченное окно, например 30 минут.

Когда Grafana показывает рост latency, вы не «записываете прошлое заново». Вы делаете **dump уже накопленного окна**. Получаете бинарный файл `.jfr`, например с событиями последних 30 минут, и открываете его в JMC.

- Источник: https://docs.oracle.com/en/java/java-components/jdk-mission-control/8/user-guide/using-jdk-flight-recorder.html

> "Continuous recordings: A continuous recording is a recording that is always on and saves, for example, the last six hours of data. During this recording, JFR collects events and writes data to the global buffer. When the global buffer fills up, the oldest data is discarded. The data currently in the buffer is written to the specified file whenever you request a dump, or if the dump is triggered by a rule."

RU:

> «Непрерывная запись всегда включена и, например, хранит последние шесть часов данных. Во время неё JFR собирает события и записывает данные в глобальный буфер. Когда буфер заполняется, самые старые данные отбрасываются. Данные, находящиеся в буфере, записываются в указанный файл по запросу dump или по правилу-триггеру.»

## 2. Что происходит во время работы JVM

Ниже — упрощённая, но правильная схема.

```text
Запуск Java-процесса
        │
        ├─ стартует continuous JFR recording
        │
        ├─ JFR записывает включённые события с временными метками
        │  ├─ GC и memory pools
        │  ├─ CPU execution samples
        │  ├─ lock contention / park
        │  ├─ File Read/Write и Socket Read/Write, обычно выше threshold
        │  └─ другие включённые JDK или custom events
        │
        ├─ события попадают в внутренние буферы и disk repository JFR
        │
        └─ старые данные удаляются при достижении maxage или maxsize
```

JFR — event-based recorder. Он записывает timestamped events, а не журналирует каждый вызов каждого метода. Например, method profiling строится на сэмплах; события блокировок и I/O обычно записываются при выполнении условий конкретного события, например после превышения порога длительности.

- Источник: https://docs.oracle.com/en/java/java-components/jdk-mission-control/8/user-guide/using-jdk-flight-recorder.html

> "The data is recorded as time-stamped data points called events. Events are categorized as follows: ... Duration events ... Instant events ... Sample events ... Custom events."

RU:

> «Данные записываются как точки с временными метками, называемые событиями. События делятся на события длительности, мгновенные события, события-сэмплы и пользовательские события.»

JFR использует repository для временного дискового хранения chunk-данных. Это внутреннее рабочее хранилище JFR, а не конечный `.jfr`-артефакт, который обычно копируют и открывают в JMC.

- Источник: https://dev.java/learn/jvm/jfr/configure/

> "repository=path: Specifies the repository (a directory) for temporary disk storage. By default, the system's temporary directory is used."

RU:

> «`repository=path` задаёт репозиторий — каталог для временного хранения на диске. По умолчанию используется системный временный каталог.»

## 3. Как расследовать инцидент из Grafana

### Ситуация

Предположим:

- микросервис работает с постоянной JFR-записью;
- retention: `maxage=30m`, лимит: `maxsize=512m`;
- в 14:03 Grafana показывает рост p99 HTTP latency;
- в 14:08 вы получили alert;
- в 14:10 вы выполняете dump.

Если лимиты позволяют, файл dump содержит историю примерно с 13:40 до 14:10. В JMC выделяете только проблемный интервал, например 14:03–14:08, и сравниваете его с нормальным периодом до 14:03.

```text
14:00  нормальная работа
14:03  начинается рост latency
14:08  alert
14:10  JFR.dump
       └─ файл содержит уже записанные события из retention window
```

### Что именно искать в JMC

| Наблюдение в Grafana | Что исследовать в JMC / JFR |
|---|---|
| Резко выросла HTTP latency | I/O и Threads: длительные `Socket Read`, `Socket Write`, parked/blocked threads |
| Высокий CPU процесса | Method Profiling / Flame View: execution samples, горячие методы и call path |
| Рост heap и частый GC | Memory и Garbage Collections: allocation sites, heap usage, pause duration |
| Запросы ждут пул или lock | Java Monitor Blocked, Thread Park, thread states, stack traces |
| Медленно отвечает PostgreSQL / внешний сервис | Socket Read/Write, stacks JDBC/HTTP-клиента; затем корреляция с DB-метриками и traces |

JMC умеет анализировать области code, memory, threads, locks и I/O после открытия `.jfr`.

- Источник: https://docs.oracle.com/en/java/java-components/jdk-mission-control/8/user-guide/using-jdk-flight-recorder.html

> "Once the flight recording file opens in the JMC, you can look at a number of different areas like code, memory, threads, locks and I/O and analyze various aspects of runtime behavior of your application."

RU:

> «После открытия файла flight recording в JMC можно исследовать код, память, потоки, блокировки и I/O и анализировать различные аспекты поведения приложения во время работы.»

### Как сделать dump

Через JMC по JMX: найдите активную continuous recording в JVM Browser, нажмите правой кнопкой мыши и выберите **Dump**. JMC позволяет выгрузить всю доступную запись, её последнюю часть либо конкретный временной интервал.

- Источник: https://docs.oracle.com/en/java/java-components/jdk-mission-control/8/user-guide/using-jdk-flight-recorder.html

> "Select to dump the whole recording, only the last part of the recording, or a specified interval of recording."

RU:

> «Можно выгрузить всю запись, только её последнюю часть или указанный интервал записи.»

Через shell на сервере это обычно выглядит так:

```bash
# Посмотреть PID Java-процесса и активные JFR-recording
jcmd
jcmd <PID> JFR.check

# Создать snapshot текущего доступного окна записи
jcmd <PID> JFR.dump name=continuous filename=/var/tmp/incident-2026-08-28-1410.jfr
```

Команда dump не останавливает continuous recording: после создания файла JFR продолжает собирать новые события, пока запись активна.

> Важно: если JFR не работал до инцидента, прошлые CPU samples, аллокации и ожидания блокировок получить уже нельзя. JFR начнёт собирать лишь события, произошедшие после старта записи.

## 4. Какие бывают записи JFR

### Continuous recording

Это production-видеорегистратор: запись работает долго, имеет лимит возраста/размера и хранит скользящее окно истории. Обычно берут low-overhead шаблон `default` либо его аккуратно изменённую копию.

### Time-fixed / profiling recording

Это подробная временная запись: например, на 2–10 минут, когда проблема воспроизводится прямо сейчас. В ней обычно включают больше событий и уменьшают пороги, поэтому накладные расходы могут быть выше. После окончания она формирует `.jfr`.

- Источник: https://docs.oracle.com/en/java/java-components/jdk-mission-control/8/user-guide/using-jdk-flight-recorder.html

> "A time fixed recording is also known as a profiling recording that runs for a set amount of time, and then stops. Usually, a time fixed recording has more events enabled and may have a slightly bigger performance effect."

RU:

> «Запись с фиксированным временем также называется profiling recording: она работает заданный период, а затем останавливается. Обычно в ней включено больше событий, поэтому влияние на производительность может быть несколько больше.»

Практический паттерн:

```text
Всегда: continuous + settings=default (или свой облегчённый .jfc)
Инцидент воспроизводится: краткая recording + settings=profile (или свой подробный .jfc)
После инцидента: dump continuous recording, даже если проблема уже исчезла
```

## 5. Что такое шаблон `.jfc`

Файл `.jfc` — XML-конфигурация JFR. Он **не содержит Java-код и не описывает бизнес-правила приложения**. Он отвечает на вопросы:

- какие типы JFR-событий включать;
- как часто снимать sample/periodic event;
- записывать ли stack trace у события;
- какой threshold длительности считать достойным записи;
- какие control/options показывать в JMC при создании recording.

То есть вы не пишете правило вида «если endpoint `/orders` отвечает дольше 1 секунды — сделай JFR dump» непосредственно в `.jfc`. `.jfc` настраивает **сбор событий**. Сам dump запускается человеком, скриптом, JMX/Diagnostic Command или внешним alerting/runbook.

- Источник: https://dev.java/learn/jvm/jfr/configure/

> "Configuration settings are defined in JFR configuration files (.jfc) and are supplied to JFR through the settings option. They contain the event settings as well as controls that JDK Mission Control will use."

RU:

> «Настройки конфигурации задаются в файлах JFR `.jfc` и передаются JFR через параметр `settings`. Они содержат настройки событий, а также элементы управления, используемые JDK Mission Control.»

## 6. Готовые шаблоны: `default` и `profile`

В JDK есть два базовых шаблона в `<JAVA_HOME>/lib/jfr`:

| Шаблон | Цель | Когда применять |
|---|---|---|
| `default.jfc` | Хороший баланс данных и нагрузки | Continuous recording в production |
| `profile.jfc` | Больше событий и диагностических деталей | Короткая profiling recording при воспроизводимой проблеме |

`default.jfc` рекомендован для continuous recording и, согласно документации, обычно даёт менее 1% overhead. Однако это ориентир, не гарантия: собственные JFR events, частые события, очень низкие thresholds и особенности нагрузки меняют цену записи. Проверяйте конфигурацию на реальной нагрузке.

- Источник: https://docs.oracle.com/en/java/javase/26/jfapi/flight-recorder-configurations.html

> "The `default.jfc` configuration is recommended for continuous recordings. It gives a good balance between data and performance (typically, less than 1% overhead). The `profile.jfc` configuration records more events and is useful while profiling an application."

RU:

> «Конфигурация `default.jfc` рекомендуется для непрерывных записей. Она обеспечивает хороший баланс данных и производительности (обычно менее 1% накладных расходов). `profile.jfc` записывает больше событий и полезна при профилировании приложения.»

Не редактируйте файлы внутри `<JAVA_HOME>/lib/jfr`: обновление JDK заменит их, и вы потеряете изменения. Создайте копию с понятным именем, например `company-continuous.jfc` или `company-incident.jfc`.

- Источник: https://docs.oracle.com/en/java/javase/26/jfapi/flight-recorder-configurations.html

> "Make a copy of an existing configuration and then modify it. Don't modify the default configurations."

RU:

> «Создайте копию существующей конфигурации и изменяйте её. Не изменяйте конфигурации по умолчанию.»

## 7. Как создать собственный шаблон

Есть два нормальных пути.

### Вариант A. JMC Template Manager

Подходит, если вы хотите увидеть дерево событий и безопасно менять флажки/threshold/stack trace через UI.

1. Откройте JMC.
2. При создании Flight Recording откройте **Template Manager**.
3. Создайте копию `Continuous` / `default` или `Profiling` / `profile`.
4. Дайте имя, например `company-continuous`.
5. Меняйте только события, для которых понимаете цель и стоимость данных.
6. Экспортируйте шаблон в файл `company-continuous.jfc`.
7. Положите файл в версионируемую директорию рядом с deployment-артефактами.
8. Передайте его при старте recording параметром `settings=/opt/service/jfr/company-continuous.jfc`.

JMC позволяет создать собственный template через Template Manager, а затем настраивать event options и event details для recording.

- Источник: https://docs.oracle.com/en/java/java-components/jdk-mission-control/8/user-guide/using-jdk-flight-recorder.html

> "Templates define the events that you want to record. To create your own templates, click Template Manager."

RU:

> «Шаблоны определяют события, которые нужно записывать. Чтобы создать собственные шаблоны, нажмите Template Manager.»

### Вариант B. `jfr configure` (JDK 19+)

Для современных JDK предпочтителен интерактивный генератор конфигурации:

```bash
$JAVA_HOME/bin/jfr configure --interactive
```

Он задаёт вопросы по категориям событий и генерирует `.jfc`. Это хороший старт, если не хочется вручную изучать XML базового шаблона.

- Источник: https://dev.java/learn/jvm/jfr/configure/

> "As of JDK 19, it's strongly encouraged to use the `jfr configure` utility for creating settings files."

RU:

> «Начиная с JDK 19 для создания файлов настроек настоятельно рекомендуется использовать утилиту `jfr configure`.»

### Вариант C. Редактирование XML

Это оправдано, когда конфигурацию нужно review-ить через Git и точно понимать diff. Начинайте с копии стандартного `.jfc`, а не с пустого файла.

Упрощённый пример настройки одного события:

```xml
<event name="jdk.JavaMonitorEnter">
  <setting name="enabled">true</setting>
  <setting name="threshold">20 ms</setting>
  <setting name="stackTrace">true</setting>
</event>
```

Смысл: писать событие входа/ожидания monitor только при длительности от 20 ms и прикладывать к нему stack trace. Конкретный набор допустимых settings зависит от event type.

## 8. Настройка событий: что именно меняется

Чаще всего вы управляете четырьмя параметрами.

| Параметр | Смысл | Пример решения |
|---|---|---|
| `enabled` | Собирать ли данный тип событий | Включить `SocketRead`, если расследуете внешние вызовы |
| `threshold` | Минимальная длительность, после которой duration event записывается | `20 ms`: не писать очень короткие ожидания lock/I/O |
| `period` | Минимальный интервал между periodic/sample events | Изменить частоту диагностики/сэмплирования |
| `stackTrace` | Прикладывать ли стек вызовов | Включать там, где нужен ответ «кто вызвал» |

- Источник: https://dev.java/learn/jvm/jfr/configure/

> "`enabled`: ... governs if JFR will collect an event ... `period`: ... governs the minimum length of time JFR will wait before recording another instance of this event ... `threshold`: ... governs the minimum length of time it takes this event to execute before JFR will record the event ... `stackTrace`: ... governs if JFR will record the stackTrace associated with the event."

RU:

> «`enabled` определяет, будет ли JFR собирать событие; `period` — минимальное время ожидания до записи следующего экземпляра; `threshold` — минимальную длительность выполнения события для его записи; `stackTrace` — будет ли JFR записывать связанный с событием стек вызовов.»

### Важный trade-off

Чем больше событий включено, чем ниже threshold и чем чаще period, тем больше подробностей в записи. Но одновременно растут:

- overhead CPU и памяти;
- объём JFR repository и вероятность раньше упереться в `maxsize`;
- объём `.jfr` dump;
- шум, который усложняет анализ.

Отдельно осторожно относитесь к `stackdepth`: по умолчанию это 64 метода; значения больше 64 могут дать значимый overhead.

- Источник: https://dev.java/learn/jvm/jfr/configure/

> "stackdepth=depth: Stack depth for stack traces. By default, the depth is set to 64 method calls. The maximum is 2048. Values greater than 64 could create significant overhead and reduce performance."

RU:

> «`stackdepth` задаёт глубину стека. По умолчанию она равна 64 вызовам; максимум — 2048. Значения больше 64 могут создать существенные накладные расходы и снизить производительность.»

## 9. Пример production-конфигурации

### Базовая continuous recording

```bash
java \
  -XX:StartFlightRecording=name=continuous,settings=default,disk=true,maxage=30m,maxsize=512m \
  -jar service.jar
```

Разбор параметров:

| Параметр | Назначение |
|---|---|
| `name=continuous` | Имя записи для `JFR.check` и `JFR.dump` |
| `settings=default` | Базовый low-overhead template |
| `disk=true` | Хранить данные на диске во время записи |
| `maxage=30m` | Не держать disk-данные старше 30 минут |
| `maxsize=512m` | Не занимать для этой записи больше 512 MB |

`maxage` и `maxsize` относятся к данным записи на диске и действуют при `disk=true`. Если фактический event rate высок, лимит `maxsize` может сократить доступную ретроспективу раньше 30 минут.

- Источник: https://dev.java/learn/jvm/jfr/configure/

> "`maxage=time`: Specifies the maximum age of disk data to keep for the recording. This parameter is valid only when the disk parameter is set to true."

RU:

> «`maxage=time` задаёт максимальный возраст данных на диске, сохраняемых для записи. Этот параметр действует только при `disk=true`.»

- Источник: https://dev.java/learn/jvm/jfr/configure/

> "`maxsize=size`: Specifies the maximum size (in bytes) of disk data to keep for the recording. This parameter is valid only when the disk parameter is set to true."

RU:

> «`maxsize=size` задаёт максимальный размер данных на диске, сохраняемых для записи. Этот параметр действует только при `disk=true`.»

### Запуск с собственным шаблоном

```bash
java \
  -XX:StartFlightRecording=name=continuous,settings=/opt/service/jfr/company-continuous.jfc,disk=true,maxage=1h,maxsize=1g \
  -jar service.jar
```

Или краткая подробная запись при воспроизводимой проблеме:

```bash
jcmd <PID> JFR.start \
  name=incident-profile \
  settings=/opt/service/jfr/company-incident.jfc \
  duration=5m \
  filename=/var/tmp/incident-profile.jfr
```

Синтаксис параметров отличается: в `-XX:StartFlightRecording` параметры разделяются запятыми, а для `JFR.start` — пробелами.

- Источник: https://dev.java/learn/jvm/jfr/configure/

> "`-XX:StartFlightRecording`, and `JFR.start` and `JFR.config` all take the same options, however when using `-XX:StartFlightRecording`, each option is delimited by a comma while with `JFR.start` and `JFR.config` use a space."

RU:

> «`-XX:StartFlightRecording`, `JFR.start` и `JFR.config` принимают одинаковые параметры, но в `-XX:StartFlightRecording` они разделяются запятыми, а в `JFR.start` и `JFR.config` — пробелами.»

## 10. Практическая схема для микросервиса

### Шаг 1. Включить baseline до инцидента

Начните с `default` на одном staging-инстансе, затем на ограниченной части production. Определите подходящие `maxage` и `maxsize` по реальному потоку событий. После проверки включите на всех нужных инстансах.

### Шаг 2. Подготовить runbook для alert

Когда Prometheus/Grafana сообщает о проблеме:

1. Зафиксируйте точный временной интервал и affected instance/pod.
2. Снимите JFR dump на каждом подозрительном инстансе до истечения retention window.
3. Сохраните файл с именем, содержащим сервис, instance, UTC-время и тип инцидента.
4. Откройте `.jfr` в JMC и ограничьте анализ проблемным временным окном.
5. Сопоставьте JFR с Grafana, логами и distributed traces.

Пример имени:

```text
orders-api-pod-7f5c9-2026-08-28T11-10Z-high-p99.jfr
```

### Шаг 3. Добавить второй шаблон для controlled capture

Держите в Git два файла:

```text
jfr/
├── company-continuous.jfc   # низкая стоимость, всегда включён
└── company-incident.jfc     # подробнее, запускается кратковременно
```

Это удобнее, чем в панике менять десятки флажков в JMC. У каждого изменения должен быть понятный вопрос, на который оно отвечает: «почему ждём PostgreSQL?», «где allocation pressure?», «кто удерживает lock?».

### Шаг 4. Добавить пользовательские JFR events при необходимости

Вы можете писать не только настройки, но и **свои события JFR в Java-коде**. Это отдельная возможность: класс наследуется от `jdk.jfr.Event`, вы добавляете полезные поля и вызываете `commit()`. Тогда в `.jfr` появится ваш доменный event, например `com.company.OrderProcessing`, с `tenant`, `operation`, `outcome` и `duration`.

Это полезно для корреляции JFR с бизнес-операцией, но нельзя бездумно записывать PII, access tokens, полный request body или высококардинальные значения.

- Источник: https://docs.oracle.com/en/java/java-components/jdk-mission-control/8/user-guide/using-jdk-flight-recorder.html

> "Custom events: user defined events created using JMC or APIs."

RU:

> «Пользовательские события — это определяемые пользователем события, создаваемые с помощью JMC или API.»

## 11. Границы применимости JFR

JFR отвечает на вопрос **«что JVM и приложение делали в момент деградации?»**: CPU, GC, allocation, lock, thread states, I/O и стеки вызовов — в пределах включённых событий.

JFR не заменяет другие наблюдаемые данные:

- Prometheus/Grafana нужны для долгосрочных метрик, графиков и alerting.
- Логи нужны для ошибок и бизнес-контекста.
- OpenTelemetry/tracing нужен для связи конкретного HTTP-запроса с downstream-вызовами, SQL и другими сервисами.
- JFR нужен для глубокой JVM-диагностики и поиска механизма задержки.

Также JFR не возвращает прошлое, если запись не существовала: continuous recording должна работать **до** инцидента. Именно поэтому документация описывает связку JFR + JMC как инструмент для continuous collection и after-the-fact incident analysis.

- Источник: https://docs.oracle.com/en/java/java-components/jdk-mission-control/8/user-guide/using-jdk-flight-recorder.html

> "Flight Recorder and JMC together create a complete toolchain to continuously collect low-level and detailed runtime information enabling after-the-fact incident analysis."

RU:

> «Flight Recorder и JMC вместе образуют полный набор средств для непрерывного сбора низкоуровневой детальной runtime-информации, позволяющей анализировать инциденты после их возникновения.»

## Итог

1. Сначала включите постоянную ограниченную по времени/размеру JFR-recording.
2. При alert сделайте dump существующего окна, а не пытайтесь «снять прошлое» после факта.
3. В JMC анализируйте только проблемный временной интервал и сопоставляйте его с Grafana, логами и traces.
4. Используйте `default.jfc` как безопасную базу для continuous recording.
5. Создайте копию `.jfc`, если нужны конкретные дополнительные события, thresholds или stack traces.
6. Используйте `profile.jfc` или отдельный incident-template коротким периодом, а не включайте максимальную детализацию навсегда.
