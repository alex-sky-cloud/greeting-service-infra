# Тема 02. Консольные утилиты JDK

> **Статус:** теория · **Дальше:** интервью в чате, затем [практика](02-jdk-tools-tasks.md)

---

## Оглавление

- [1. Найти процесс: jps и jcmd без аргументов](#1-найти-процесс-jps-и-jcmd-без-аргументов)
- [2. jcmd: паспорт JVM](#2-jcmd-паспорт-jvm)
- [3. Цена команды: что останавливает приложение](#3-цена-команды-что-останавливает-приложение)
- [4. jstat: сборки мусора в реальном времени](#4-jstat-сборки-мусора-в-реальном-времени)
- [5. Гистограмма кучи: кто занимает память](#5-гистограмма-кучи-кто-занимает-память)
- [6. Память вне кучи: VM.native_memory](#6-память-вне-кучи-vmnative_memory)
- [7. jmap, jstack, jinfo — чем заменить](#7-jmap-jstack-jinfo--чем-заменить)
- [Вопросы для интервью](#вопросы-для-интервью)

---

## 1. Найти процесс: jps и jcmd без аргументов

**Аналогия:**

Прежде чем осматривать пациента, надо найти его палату. На посту есть список: номер палаты и фамилия.

**Как это устроено в коде:**

- **Список на посту** — `jps -l` или `jcmd` без аргументов: все JVM текущего пользователя, PID и главный класс.
- **Номер палаты** — PID. Его передают всем остальным утилитам.
- **Чужое отделение** — JVM другого пользователя или в другом контейнере в списке не видна. Утилиту запускают от того же пользователя и в том же контейнере.

Запуск на JDK 27, лаборатории `report` и `churn`:

```bash

jps -l
# 17492 org.gradle.launcher.daemon.bootstrap.GradleDaemon
# 11432 com.example.profcourse.lab.LabApp
# 18616 com.intellij.database.remote.RemoteJdbcServer
# 2216 com.intellij.idea.Main
# 20460 jdk.jcmd/sun.tools.jps.Jps
jcmd
# 22112 com.example.profcourse.lab.LabApp churn
```

**Разбор:**

- В списке есть сам `jps`, демон Gradle и IDE: это тоже JVM.
- `jcmd` без аргументов показывает ещё и аргументы программы — по ним удобно различать несколько одинаковых сервисов.

**Правило:** PID берут из `jcmd` или `jps -l`, утилиту запускают от пользователя сервиса и из того же JDK.

---

## 2. jcmd: паспорт JVM

**Аналогия:**

Врач начинает с карты: возраст, вес, какие лекарства уже принимает. Без этого любой анализ читается неправильно.

**Как это устроено в коде:**

- **Возраст** — `jcmd <pid> VM.version` и `VM.uptime`: какая JVM и сколько работает.
- **Лекарства** — `VM.command_line` и `VM.flags`: с какими параметрами запущена, включая выставленные самой JVM.
- **Вес** — `GC.heap_info`: какая куча, сколько занято, какой сборщик.
- **Список процедур** — `jcmd <pid> help`: все команды этой JVM; `jcmd <pid> help <команда>` — справка по одной.

Запуск на JDK 27, лаборатория `report` без `-Xmx`:

```bash

jcmd 11432 VM.version
# OpenJDK 64-Bit Server VM version 27+35-2325
# JDK 27.0.0
jcmd 11432 VM.flags
# … -XX:InitialHeapSize=8388608 … -XX:MaxHeapSize=4240441344 … -XX:+UseG1GC …
jcmd 11432 GC.heap_info
# garbage-first heap   total reserved 4141056K, committed 946176K, used 240742K
#  region size 2M, 116 eden (232M), 1 survivor (2M), 3 old (6M), 0 humongous (0M), 342 free (684M)
```

**Разбор:**

- `-Xmx` не задан, а `MaxHeapSize` — 4 ГБ: JVM сама взяла четверть памяти машины. В контейнере это надо проверять первым делом.
- `committed` — сколько кучи JVM уже взяла у ОС, `used` — сколько занято объектами.

- Источник: https://docs.oracle.com/en/java/javase/27/troubleshoot/diagnostic-tools.html

> The `jcmd` utility is used to send diagnostic command requests to the JVM. These requests are useful for managing recordings from Flight Recorder, troubleshooting, and diagnosing JVM and Java applications.

RU:

> Утилита `jcmd` отправляет JVM запросы диагностических команд. Эти запросы полезны для управления записями Flight Recorder, поиска неисправностей и диагностики JVM и Java-приложений.

**Правило:** любое расследование начинается с `VM.version`, `VM.flags` и `GC.heap_info` — паспорта JVM.

---

## 3. Цена команды: что останавливает приложение

**Аналогия:**

Градусник ставят любому пациенту. Пункцию — только по показаниям: она болезненна и сама влияет на состояние.

**Как это устроено в коде:**

- **Градусник** — команды с `Impact: Low`: `VM.version`, `VM.flags`, `VM.uptime`, `JFR.start`, `JFR.dump`.
- **Анализ посерьёзнее** — `Impact: Medium`: `Thread.print`, `GC.heap_info`, `VM.native_memory`. Время зависит от числа потоков и классов.
- **Пункция** — `Impact: High`: `GC.class_histogram` и `GC.heap_dump`. Обходят всю кучу и делают полную сборку.
- **Где смотреть** — `jcmd <pid> help <команда>` печатает строку `Impact` для каждой команды.

Запуск на JDK 27: гистограммы кучи на лаборатории `leak`. В GC-логе:

```text
[13.984s][info][gc] GC(58) Pause Full (Heap Inspection Initiated GC) 10M->7M(13M) 14.624ms
[14.183s][info][gc] GC(59) Pause Full (Heap Inspection Initiated GC) 8M->7M(13M) 11.983ms
[81.880s][info][gc] GC(183) Pause Full (Heap Inspection Initiated GC) 46M->33M(53M) 31.743ms
```

**Разбор:**

- Каждая гистограмма — полная сборка с остановкой приложения. Куча выросла с 13 до 53 МБ — пауза выросла с 12 до 32 мс; на куче в 30 ГБ это секунды.
- Эти сборки потом видны в `jstat` в столбце `FGC`. Их не путают с полными сборками от нехватки памяти.

- Источник: https://docs.oracle.com/en/java/javase/27/docs/specs/man/jcmd.html

> `GC.class_histogram` [options]
> : Provides statistics about the Java heap usage.
>
> Impact: High --- depends on Java heap size and content.
>
> …
>
> `GC.heap_dump` [options] filename
> : Generates a HPROF format dump of the Java heap.
>
> Impact: High --- depends on the Java heap size and content. Request a full GC unless the `-all` option is specified.

RU:

> `GC.class_histogram` [параметры]
> : Выдаёт статистику использования Java-кучи.
>
> Влияние: высокое — зависит от размера и содержимого Java-кучи.
>
> …
>
> `GC.heap_dump` [параметры] имя_файла
> : Создаёт дамп Java-кучи в формате HPROF.
>
> Влияние: высокое — зависит от размера и содержимого Java-кучи. Запрашивает полную сборку, если не указан параметр `-all`.

**Правило:** на продакшене без согласования — только команды с `Impact: Low`; гистограмма и дамп кучи — осознанно, с пониманием паузы.

---

## 4. jstat: сборки мусора в реальном времени

**Аналогия:**

Монитор у кровати: пульс и давление каждую секунду. Сам по себе не ставит диагноз, но показывает, что меняется и как быстро.

**Как это устроено в коде:**

- **Монитор** — `jstat -gcutil -t <pid> 5000`: строка каждые 5 секунд, `-t` — секунды с запуска JVM.
- **Столбцы `E`, `O`** — заполнение Eden и старого поколения в процентах **от текущей ёмкости**, а не от `-Xmx`.
- **Столбцы `YGC`, `FGC`** — сколько молодых и полных сборок было с запуска; растут нарастающим итогом.
- **Столбцы `YGCT`, `FGCT`, `GCT`** — сколько секунд заняли эти сборки, тоже нарастающим итогом.
- **Доля времени в GC** — ΔGCT / ΔTimestamp между двумя строками.

Запуск на JDK 27, лаборатория `leak` с `-Xmx96m`, начало и конец записи:

```text
Timestamp   S0     S1     E      O      M     CCS    YGC     YGCT     FGC    FGCT     CGC    CGCT       GCT
     31,8    -  71,27   0,00  83,06  88,01  67,93     85     0,114     2     0,026    68     0,021     0,161
    227,1    -      -  25,00  99,26  88,46  66,64    621     1,412     7     0,250   580     0,215     1,877
    232,1    -      -  50,00  98,94  88,46  66,64    637     1,433    23     1,021   580     0,215     2,669
    237,1    -      -   0,00  99,37  88,46  66,64    660     1,461    46     2,359   580     0,215     4,035
```

Та же утилита на лаборатории `churn` — много мусора, утечки нет:

```text
Timestamp   S0     S1     E      O      M     CCS    YGC     YGCT     FGC    FGCT     CGC    CGCT       GCT
      5,2    -   0,89  43,40   4,53  77,09  42,07    388     0,261     0     0,000     0     0,000     0,261
     33,2    -   2,87   0,00   4,80  77,09  42,07   2177     1,639     0     0,000     0     0,000     1,639
```

**Разбор:**

- `leak`, вся запись: GC занял (4,035 − 0,161) / 205,3 ≈ 1,9 % времени — по среднему всё прилично.
- `leak`, последние 10 секунд: (4,035 − 1,877) / 10 ≈ 22 %, полных сборок — 39. Это агония перед `OutOfMemoryError`.
- `churn`: 64 молодые сборки в секунду, но `O` стоит на 4–5 %, полных сборок нет. Мусора много, утечки нет.
- `O` = 83 % уже в начале `leak` — не тревога: ёмкость старого поколения в G1 растёт по мере надобности.
- Дробная часть через запятую — это русская локаль Windows. В WSL будет точка.

- Источник: https://docs.oracle.com/en/java/javase/27/troubleshoot/diagnostic-tools.html

> The `jstat` utility uses the built-in instrumentation in the Java HotSpot VM to provide information about performance and resource consumption of running applications.
>
> The tool can be used when diagnosing performance issues, and in particular issues related to heap sizing and garbage collection. The `jstat` utility does not require the VM to be started with any special options.

RU:

> Утилита `jstat` использует встроенную инструментовку Java HotSpot VM и показывает данные о производительности и потреблении ресурсов работающими приложениями.
>
> Её можно использовать при диагностике проблем производительности, особенно связанных с размером кучи и сборкой мусора. Для `jstat` не нужно запускать VM с какими-то особыми параметрами.

- Источник: https://docs.oracle.com/en/java/javase/27/docs/specs/man/jstat.html

> `O`: Old space utilization as a percentage of the space's current capacity.
>
> …
>
> Don't write scripts to parse the `jstat` command's output because the format might change in future releases.

RU:

> `O`: заполнение старого пространства в процентах от его текущей ёмкости.
>
> …
>
> Не пишите скрипты, разбирающие вывод команды `jstat`: формат может измениться в будущих выпусках.

**Ловушка:**

Доля GC за всё время прячет агонию последних секунд. Окно для расчёта выбирают по времени инцидента.

**Правило:** `jstat -gcutil -t` — первый взгляд на GC; смотрят рост `FGC` и долю ΔGCT за окно инцидента.

---

## 5. Гистограмма кучи: кто занимает память

**Аналогия:**

Перепись склада: сколько коробок каждого вида и сколько места они занимают. Одна перепись ничего не говорит, две с разницей в час — показывают, что прибывает.

**Как это устроено в коде:**

- **Перепись** — `jcmd <pid> GC.class_histogram`: класс, число живых объектов, байты.
- **Только живые** — перед подсчётом JVM делает полную сборку: мусор в переписи не участвует.
- **Две переписи** — снимки с разницей в минуты при одинаковой нагрузке; растущие классы — кандидаты в утечку.
- **Коробки внутри коробок** — `[B` (массивы байт) и `String` почти всегда наверху: в них лежат данные чужих объектов. Смотрят на первый класс приложения рядом с ними.

Запуск на JDK 27, лаборатория `leak`, снимки на 14-й и 82-й секунде:

```text
 num     #instances         #bytes  class name (module)
   1:        149119       27697856  [B (java.base@27)
   2:        103743        2489832  java.lang.String (java.base@27)
   3:         47786        1146864  java.util.concurrent.ConcurrentHashMap$Node (java.base@27)
   4:         45287        1086888  java.time.Instant (java.base@27)
   5:         45283        1086792  com.example.profcourse.t03.UserSession
```

| Класс | Было объектов | Стало | Рост |
|---|---|---|---|
| `com.example.profcourse.t03.UserSession` | 7 806 | 45 283 | +37 477 |
| `java.time.Instant` | 7 810 | 45 287 | +37 477 |
| `ConcurrentHashMap$Node` | 10 311 | 47 786 | +37 475 |

**Разбор:**

- Три класса выросли на одно и то же число — это одна связка: сессия, её время и узел карты, в которой она лежит.
- Утечка — не «много `[B`», а «объекты `UserSession` копятся в какой-то `ConcurrentHashMap`». Какой именно — покажет дамп кучи (тема 03).

**Правило:** утечку ищут разницей двух гистограмм; ответ — первый класс приложения среди растущих и связка классов с одинаковым ростом.

---

## 6. Память вне кучи: VM.native_memory

**Аналогия:**

Квартира — не только комнаты. Есть кладовка, балкон, антресоль: их площадь тоже входит в счёт за аренду.

**Как это устроено в коде:**

- **Комнаты** — Java-куча, то, что ограничивает `-Xmx`.
- **Кладовка и антресоль** — память JVM вне кучи: метаданные классов, стеки потоков, скомпилированный код, буферы GC.
- **Счёт за аренду** — что видит ОС и контейнер: куча плюс всё остальное. OOM-killer контейнера считает именно его.
- **Опись** — Native Memory Tracking: флаг `-XX:NativeMemoryTracking=summary` при старте и `jcmd <pid> VM.native_memory summary`.

Запуск на JDK 27, лаборатория `report` с NMT:

```text
Total: reserved=5573MB, committed=1036MB
       malloc: 37MB #16289, peak=49MB #13255
       mmap:   reserved=5535MB, committed=999MB

-                 Java Heap (reserved=4044MB, committed=924MB)
                            (mmap: reserved=4044MB, committed=924MB, peak=946MB)

-                     Class (reserved=1024MB, committed=0MB)
                            (classes #1224)
```

**Разбор:**

- `reserved` — адресное пространство, `committed` — реально взятая память. Сравнивают `committed` с лимитом контейнера.
- NMT включается только при старте JVM: на работающем процессе его не включить.

- Источник: https://docs.oracle.com/en/java/javase/27/troubleshoot/diagnostic-tools.html

> Enabling NMT will result in 5-10 percent JVM performance drop and memory usage for NMT adds 2 words to all malloc memory as malloc header.

RU:

> Включение NMT снижает производительность JVM на 5–10 процентов, а на учёт NMT к каждому блоку malloc добавляется заголовок в 2 машинных слова.

**Правило:** процесс съел больше `-Xmx` — смотрят `VM.native_memory summary`; флаг NMT ставят заранее на стенде, а не на всём продакшене.

---

## 7. jmap, jstack, jinfo — чем заменить

**Аналогия:**

Раньше в больнице было три отдельных прибора. Потом поставили один комбайн, который умеет всё то же и меньше мешает пациенту.

**Как это устроено в коде:**

| Старая утилита | Замена в `jcmd` |
|---|---|
| `jstack <pid>` | `jcmd <pid> Thread.print` |
| `jmap -histo <pid>` | `jcmd <pid> GC.class_histogram` |
| `jmap -dump:file=… <pid>` | `jcmd <pid> GC.heap_dump <file>` |
| `jinfo -flags <pid>` | `jcmd <pid> VM.flags` |
| `jinfo -sysprops <pid>` | `jcmd <pid> VM.system_properties` |

- **Остаётся отдельно** — `jstat` (живая статистика GC) и `jfr` (разбор записей JFR, тема 05).
- **Ядро процесса** — `jhsdb jstack --core …` читает упавший процесс по core-файлу; `jcmd` работает только с живым.

- Источник: https://docs.oracle.com/en/java/javase/27/troubleshoot/diagnostic-tools.html

> Some command-line utilities described in this section are experimental. The `jstack`, `jinfo`, and `jmap` utilities are examples of utilities that are experimental. It is suggested to use the latest diagnostic utility, `jcmd` instead of the earlier `jstack`, `jinfo,` and `jmap` utilities.

RU:

> Некоторые утилиты командной строки, описанные в этом разделе, экспериментальные. Утилиты `jstack`, `jinfo` и `jmap` — примеры экспериментальных. Рекомендуется использовать новую диагностическую утилиту `jcmd` вместо прежних `jstack`, `jinfo` и `jmap`.

**Правило:** для живого процесса — `jcmd`; `jstack`, `jmap`, `jinfo` знают, чтобы читать старые инструкции.

---

## Вопросы для интервью

1. Как найти PID нужной JVM? Почему утилита может не увидеть процесс?
2. С каких трёх команд `jcmd` вы начнёте и что в них ищете?
3. Какие команды `jcmd` опасно запускать на нагруженном продакшене и почему?
4. Что показывают столбцы `O`, `FGC` и `GCT` в `jstat -gcutil`? Как посчитать долю времени в GC?
5. В `jstat` `O` = 83 % через 30 секунд после старта. Это утечка?
6. Как гистограммой кучи найти утечку? Почему `[B` наверху ни о чём не говорит?
7. Процесс в контейнере занимает 3 ГБ при `-Xmx2g`. Куда смотреть?
8. Чем заменить `jstack`, `jmap` и `jinfo`?

---

**Практика:** [02-jdk-tools-tasks.md](02-jdk-tools-tasks.md)
