# Тема 05. JFR и JMC

> **Статус:** теория · **Дальше:** интервью в чате, затем [практика](05-jfr-jmc-tasks.md)

---

## Оглавление

- [1. Что такое JFR и сколько он стоит](#1-что-такое-jfr-и-сколько-он-стоит)
- [2. Запись при старте и через jcmd](#2-запись-при-старте-и-через-jcmd)
- [3. Шаблоны default и profile](#3-шаблоны-default-и-profile)
- [4. Постоянная запись и выгрузка после инцидента](#4-постоянная-запись-и-выгрузка-после-инцидента)
- [5. Прочитать запись: jfr summary и jfr view](#5-прочитать-запись-jfr-summary-и-jfr-view)
- [6. JMC: автоматический анализ](#6-jmc-автоматический-анализ)
- [7. Время метода без правки кода](#7-время-метода-без-правки-кода)
- [Вопросы для интервью](#вопросы-для-интервью)

---

## 1. Что такое JFR и сколько он стоит

**Аналогия:**

Видеорегистратор в машине пишет всегда, по кругу, и почти не мешает ехать. После аварии сохраняют последние минуты.

**Как это устроено в коде:**

- **Регистратор** — JDK Flight Recorder, встроен в JVM. Ставить ничего не нужно.
- **Кадры** — события: сборка мусора, сэмпл стека, ожидание монитора, чтение сокета, выделение памяти.
- **Порог** — длительные события пишутся, только если дольше порога: ожидание монитора — от 20 мс в шаблоне `default`.
- **Запись** — файл `.jfr`. Читают утилитой `jfr`, в JMC или в IntelliJ IDEA.

- Источник: https://docs.oracle.com/en/java/javase/27/troubleshoot/troubleshoot-performance-issues-using-jfr.html

> The overhead for recording a standard time fixed recording (profiling recording) using the default settings is less than two percent for most applications. Running with a standard continuous recording generally has no measurable performance effect.

RU:

> Накладные расходы стандартной записи фиксированной длительности (профилирующей записи) с настройками по умолчанию — меньше двух процентов для большинства приложений. Стандартная постоянная запись обычно не даёт измеримого влияния на производительность.

**Правило:** JFR — первый профайлер на продакшене: встроен, дешёв, пишет и CPU, и ожидания, и память.

---

## 2. Запись при старте и через jcmd

**Аналогия:**

Регистратор можно включить при посадке в машину или нажать кнопку уже в пути.

**Как это устроено в коде:**

- **При посадке** — флаг `-XX:StartFlightRecording:…` при запуске JVM.
- **Кнопка в пути** — `jcmd <pid> JFR.start …` на работающем процессе, без перезапуска.
- **Что пишется сейчас** — `jcmd <pid> JFR.check`.
- **Сохранить** — `jcmd <pid> JFR.dump name=… filename=…`: копия того, что в буфере, запись продолжается.
- **Остановить** — `jcmd <pid> JFR.stop name=…`.

Запуск на JDK 27, лаборатория `report` с постоянной записью:

```bash

java -XX:StartFlightRecording:name=continuous,maxage=10m,maxsize=100m \
  -cp build/classes/java/main com.example.profcourse.lab.LabApp report
# [0.387s][info][jfr,startup] Started recording 1.
# [0.387s][info][jfr,startup] Use jcmd 21172 JFR.dump name=continuous filename=FILEPATH to copy recording data to file.

jcmd 21172 JFR.check
# Recording 1: name=continuous maxsize=100,0MB maxage=10m (running)
jcmd 21172 JFR.dump name=continuous filename=build/lab/rep-incident.jfr
# Dumped recording "continuous", 1,8 MB written to: …/build/lab/rep-incident.jfr
```

**Разбор:**

- JVM сама подсказывает команду выгрузки при старте.
- 10 секунд записи — 1,8 МБ. Час постоянной записи с `maxage` на продакшене — десятки мегабайт.

**Правило:** на продакшене запись включают флагом при старте; `JFR.start` — когда перезапуск невозможен.

---

## 3. Шаблоны default и profile

**Аналогия:**

У регистратора два режима: экономный для каждого дня и подробный, когда что-то ищут.

**Как это устроено в коде:**

- **Шаблон** — файл `.jfc` в `$JAVA_HOME/lib/jfr/`: какие события писать, с каким периодом и порогом.
- **`default`** — для постоянной записи на продакшене.
- **`profile`** — для короткой записи при расследовании: чаще сэмплы, ниже пороги.
- **Выбор** — параметр `settings=profile` в `-XX:StartFlightRecording` или `JFR.start`.
- **Свой шаблон** — `jfr configure`: из стандартного шаблона с изменёнными параметрами.

Значения из `lib/jfr/default.jfc` и `lib/jfr/profile.jfc` в JDK 27:

| Событие | `default` | `profile` |
|---|---|---|
| `jdk.ExecutionSample` — сэмпл стека | раз в 20 мс | раз в 10 мс |
| `jdk.JavaMonitorEnter` — ожидание монитора | дольше 20 мс | дольше 10 мс |
| `jdk.ThreadPark` — ожидание замка | дольше 20 мс | дольше 10 мс |
| `jdk.ObjectAllocationSample` — сэмпл аллокации | до 150 в секунду | до 300 в секунду |

- Источник: https://openjdk.org/jeps/520

> The JDK includes two configuration files, `default.jfc` and `profile.jfc`; the former is used when no configuration file is specified, while the latter contains convenient configuration settings for profiling.

RU:

> В JDK есть два файла конфигурации, `default.jfc` и `profile.jfc`: первый используется, когда файл конфигурации не указан, второй содержит удобные настройки для профилирования.

Свои шаблоны — [локальная памятка «continuous-запись, dump после инцидента и собственные шаблоны `.jfc`»](../../../docs/profiler/Java%20Flight%20Recorder%20continuous-запись,%20dump%20после%20инцидента%20и%20собственные%20шаблоны%20%60.jfc%60.md).

**Правило:** постоянно — `default`; на время расследования — `profile`, коротко и под присмотром.

---

## 4. Постоянная запись и выгрузка после инцидента

**Аналогия:**

Регистратор не хранит всю дорогу за год — только последний час. Этого хватает, чтобы разобрать аварию.

**Как это устроено в коде:**

- **Последний час** — `maxage=1h` и `maxsize=500m`: старые данные вытесняются.
- **На диск** — постоянная запись хранит данные в каталоге-репозитории, а не только в памяти.
- **При остановке** — `dumponexit=true` и `filename=…`: файл появится и при штатной остановке JVM.
- **После инцидента** — `jcmd <pid> JFR.dump name=… filename=incident.jfr`.
- **Из кода** — класс `jdk.jfr.Recording`: те же `setMaxAge`, `setMaxSize`, `setToDisk`, `dump(Path)`. Так сервис сам сохраняет запись после медленного запроса.

- Источник: https://docs.oracle.com/en/java/javase/27/troubleshoot/prepare-java-troubleshooting.html

> Run a continuous flight recording: Set up Java to run with a continuous flight recording.
>
> Continuous flight recordings provide a file-based record of JVM events. This provides a low-overhead means of monitoring a Java application that can be used in the event of an issue.

RU:

> Запускайте постоянную запись: настройте Java на работу с постоянной записью Flight Recorder.
>
> Постоянная запись даёт файловый журнал событий JVM. Это дешёвый способ наблюдения за Java-приложением, которым можно воспользоваться при проблеме.

**Ловушка:**

В записи есть переменные окружения и системные свойства процесса — в `jfr summary` из раздела 5 это `jdk.InitialEnvironmentVariable`. Пароли из окружения уедут вместе с файлом, поэтому перед передачей — `jfr scrub`.

- Источник: https://docs.oracle.com/en/java/javase/27/docs/specs/man/jfr.html

> Use `jfr scrub` to remove sensitive contents from a file or to reduce its size.

RU:

> `jfr scrub` удаляет из файла чувствительное содержимое или уменьшает его размер.

**Правило:** постоянная запись с `maxage` стоит на каждом сервисе; после инцидента — `JFR.dump` до перезапуска; наружу — только после `jfr scrub`.

---

## 5. Прочитать запись: jfr summary и jfr view

**Аналогия:**

Запись регистратора — часы видео. Сначала смотрят оглавление, потом готовые нарезки по темам.

**Как это устроено в коде:**

- **Оглавление** — `jfr summary file.jfr`: какие события и сколько.
- **Нарезки** — `jfr view <view> file.jfr`: готовые сводки. Список — `jfr --help view`, все сразу — `jfr view all-views file.jfr`.
- **Сырые события** — `jfr print --events jdk.ExecutionSample file.jfr`.

| Вопрос | Сводка `jfr view` |
|---|---|
| где тратится CPU | `hot-methods` |
| кто выделяет память | `allocation-by-site` |
| где ждут мониторы | `contention-by-site` |
| паузы GC | `gc-pauses` |
| сколько длится метод | `method-timing` |

Запуск на JDK 27, запись `rep-incident.jfr` с лаборатории `report`:

```text
 Version: 2.1
 Chunks: 1
 Start: 2026-10-20 11:06:55 (UTC)
 Duration: 10 s

 Event Type                              Count  Size (bytes)
=============================================================
 jdk.GCPhaseParallel                     54773       1461905
 jdk.TenuringDistribution                 1545         15747
 jdk.ObjectAllocationSample               1542         22964
 …
 jdk.ExecutionSample                       111          1110
 jdk.InitialEnvironmentVariable            106         10484
```

```text
                                           Java Methods that Execute the Most

Method                                                                                                  Samples Percent
------------------------------------------------------------------------------------------------------- ------- -------
java.time.format.DateTimeFormatterBuilder.parsePattern(String)                                               65  58,56%
java.lang.invoke.DirectMethodHandle$Holder.invokeSpecial(...)                                                13  11,71%
java.lang.String$$StringConcat.0x00000000980c5000.prepend(...)                                                8   7,21%
java.time.format.DateTimeFormatterBuilder$CompositePrinterParser.format(...)                                  2   1,80%
com.example.profcourse.t06.LegacyReportBuilder.build(List)                                                    2   1,80%
```

**Разбор:**

- Больше всего событий — служебных событий GC. Сэмплов стека — 111 за 10 секунд: для выводов маловато, запись нужна длиннее.
- Наверху `parsePattern` — разбор шаблона даты, а не конкатенация строк, о которой подумал бы каждый. Почему так — тема 06.
- Наш метод `LegacyReportBuilder.build` — всего 1,8 % «на вершине стека». Время тратят вызванные им методы JDK.

**Правило:** чтение записи начинают с `jfr summary`, затем `jfr view` по вопросу; свою гипотезу проверяют записью, а не интуицией.

---

## 6. JMC: автоматический анализ

**Аналогия:**

Тот же видеорегистратор, но запись смотрит эксперт и сразу отмечает опасные моменты.

**Как это устроено в коде:**

- **Эксперт** — JDK Mission Control (JMC): отдельная программа, открывает `.jfr`.
- **Отметки** — страница Automated Analysis Results: правила с оценкой серьёзности — паузы GC, конкуренция за замки, горячие методы, аллокации.
- **Подробности** — отдельные страницы по методам, замкам, памяти и сборкам мусора.
- **Живой процесс** — JMC подключается к JVM и сам запускает запись.

- Источник: https://docs.oracle.com/en/java/javase/27/troubleshoot/troubleshoot-performance-issues-using-jfr.html

> Open the recording in JMC. Look at the Garbage Collections section in the Automated Analysis Results page.

RU:

> Откройте запись в JMC. Посмотрите раздел Garbage Collections на странице Automated Analysis Results.

Руководство JMC 9 — [локальная копия](../../../docs/profiler/Java%20Mission%20Control/jmc9-user-guide-local/README.md), окна программы — [«Графический интерфейс JMC»](../../../docs/profiler/Java%20Mission%20Control/jmc9-user-guide-local/jmc-application-gui.html), чтение записи — [«Использование JDK Flight Recorder»](../../../docs/profiler/Java%20Mission%20Control/jmc9-user-guide-local/using-jdk-flight-recorder.html). JFR — [руководство](../../../docs/profiler/Java%20Flight%20Recorder%20(JFR)/jfr-runtime-guide-local/README.md) и [статья Axiom JDK](../../../docs/profiler/Java%20Flight%20Recorder%20(JFR)/axiom-jdk-jfr-local/README.md).

### Запуск JMC

Снимки сделаны в JMC 8.1 на Windows; руководство написано для JMC 9, названия страниц совпадают. Без флага браузера Edge панель Flame View в JMC 8.1 на Windows остаётся пустой.

```bash

/mnt/c/jdk/jmc/jmc.exe -vmargs -Dorg.eclipse.swt.browser.DefaultType=edge
```

### Открыть запись

1. Меню **File**.
2. **Open File…** (`Ctrl+O`).

![JMC: меню File → Open File](images/05-jmc-open-file-menu.png)

1. В поле **Имя файла** — путь к `.jfr`, например `build\lab\gui\report.jfr`.
2. **Открыть**.

![JMC: диалог Open File](images/05-jmc-open-file-dialog.png)

**Ловушка:**

`jmc -open file.jfr` открывает файл, только если JMC ещё не запущен. Если окно уже открыто, второй вызов тихо завершается — открывайте через **File → Open File…**.

### Automated Analysis Results

Запись лаборатории `report`, 20 секунд, шаблон `profile`:

```bash

java -XX:StartFlightRecording:settings=profile,duration=20s,filename=build/lab/gui/report.jfr \
  -cp build/classes/java/main com.example.profcourse.lab.LabApp report
# выгрузка: 8000 заказов, 421568 символов, 739 мс
# выгрузка: 8000 заказов, 421568 символов, 385 мс
# …
```

![JMC: Automated Analysis Results](images/05-jmc-automated-analysis.png)

1. В дереве **Outline** — **Automated Analysis Results**. Эта страница открывается первой.
2. Блок **Java Application**: правило **Parallel Threads** — 25 (жёлтый), **Free Physical Memory** — 85 (красный).
3. Блок **Environment**: **Competing CPU Ratio Usage** — 94, **Competing Processes** — 47.

**Разбор:**

- Оценка 0–100: зелёный — норма, жёлтый — посмотреть, красный — проблема. Щелчок по правилу раскрывает объяснение и совет.
- Красное **Competing CPU** здесь — не вина сервиса: на машине параллельно работали IDE и браузер. Правила про окружение сначала сверяют с машиной, на которой шла запись.

### Method Profiling

![JMC: Method Profiling](images/05-jmc-method-profiling.png)

1. В дереве — **Java Application → Method Profiling**.
2. Верхняя строка таблицы: `DateTimeFormatterBuilder.parsePattern(String)` — 216 сэмплов, 50,3 %.
3. Панель **Stack Trace** снизу: кто вызвал выделенный метод — `parsePattern` ← `appendPattern` ← `ofPattern` ← `LegacyReportBuilder.build` ← `LabApp.report` ← `main`.

**Разбор:**

- Тот же ответ, что дал `jfr view hot-methods` в разделе 5, но здесь сразу виден путь вызова до нашего кода.
- Панель Stack Trace следует за выделением: щёлкните другую строку таблицы — стек сменится.

**Правило:** `jfr view` — быстрый ответ в терминале; JMC — когда нужен обзор всей записи и подсказки, куда смотреть.

---

## 7. Время метода без правки кода

**Аналогия:**

Сэмплы — фотографии раз в 20 мс: видно, где чаще всего застают. Секундомер на конкретном методе — точное число вызовов и время каждого.

**Как это устроено в коде:**

- **Секундомер** — параметр `method-timing=Класс::метод` в `JFR.start` или `-XX:StartFlightRecording` (JDK 25+).
- **Без правки кода** — JVM сама вставляет замер в байткод выбранных методов и убирает его после записи.
- **Результат** — `jfr view method-timing file.jfr`: число вызовов, минимум, среднее, максимум.
- **Цена** — замер на каждом вызове. Ставят на несколько методов, а не на пакет целиком.

Запуск на JDK 27, лаборатория `report`, 15 секунд:

```bash

jcmd 21172 JFR.start name=timing duration=15s \
  "method-timing=com.example.profcourse.t06.LegacyReportBuilder::build;java.time.format.DateTimeFormatter::ofPattern" \
  filename=build/lab/rep-timing.jfr
jfr view method-timing build/lab/rep-timing.jfr
```

```text
                                                Method Timing

Timed Method                                                 Invocations  Minimum Time  Average Time  Maximum Time
------------------------------------------------------------ ----------- ------------- ------------- -------------
com.example.profcourse.t06.LegacyReportBuilder.build(List)            86 157,000000 ms 170,000000 ms 226,000000 ms
java.time.format.DateTimeFormatter.ofPattern(String)             691 819   0,000177 ms   0,000360 ms   0,257000 ms
java.time.format.DateTimeFormatter.ofPattern(String, Locale)           0           N/A           N/A           N/A
```

**Разбор:**

- 86 выгрузок по 8 000 заказов — 688 000 строк; `ofPattern` вызван 691 819 раз. Шаблон даты создаётся на каждую строку отчёта — это доказательство, а не догадка.
- Время коротких методов под секундомером неточно: доли микросекунды сравнимы с ценой самого замера. Для таких методов верят числу вызовов, а время берут у вызывающего.

- Источник: https://openjdk.org/jeps/520

> The `method-timing` option counts the number of invocations and calculates the average execution time of methods that match the filter, while the `method-trace` option records stack traces for methods that match the filter.
>
> …
>
> The precision of the `average` and `duration` fields depends upon the time it takes to obtain a timestamp, which in turn depends upon the hardware and operating system.

RU:

> Параметр `method-timing` считает число вызовов и среднее время выполнения методов, подходящих под фильтр, а `method-trace` записывает стеки вызовов для подходящих методов.
>
> …
>
> Точность полей `average` и `duration` зависит от того, сколько времени занимает получение отметки времени, а это, в свою очередь, зависит от оборудования и операционной системы.

**Правило:** сэмплы показывают, где искать; `method-timing` на 1–3 метода подтверждает число вызовов и время до и после исправления.

---

## Вопросы для интервью

1. Сколько стоит JFR и почему его можно держать включённым на продакшене?
2. Как начать запись на работающем сервисе и сохранить её, не останавливая?
3. Чем шаблон `profile` отличается от `default`? Когда какой?
4. Какие параметры постоянной записи вы поставите на каждый сервис?
5. Почему запись JFR нельзя сразу отправить подрядчику?
6. С чего начинаете читать незнакомый `.jfr`?
7. Что даёт JMC сверх `jfr view`?
8. Чем `method-timing` отличается от сэмплов? Почему время короткого метода под ним неточно?

---

**Практика:** [05-jfr-jmc-tasks.md](05-jfr-jmc-tasks.md)
