# Тема 04. Потоки: дамп, очереди, взаимная блокировка

> **Статус:** теория · **Дальше:** интервью в чате, затем [практика](04-threads-tasks.md)

---

## Оглавление

- [1. Дамп потоков: что делает каждый поток](#1-дамп-потоков-что-делает-каждый-поток)
- [2. Почему дампов нужно несколько](#2-почему-дампов-нужно-несколько)
- [3. Очередь к пулу: CPU свободен, а всё стоит](#3-очередь-к-пулу-cpu-свободен-а-всё-стоит)
- [4. Взаимная блокировка](#4-взаимная-блокировка)
- [5. Конкуренция за монитор](#5-конкуренция-за-монитор)
- [6. Виртуальные потоки в дампе](#6-виртуальные-потоки-в-дампе)
- [Вопросы для интервью](#вопросы-для-интервью)

---

## 1. Дамп потоков: что делает каждый поток

**Аналогия:**

Фотография операционного зала банка. На ней видно, кто из операционистов работает, кто ждёт ключ от сейфа, а кто ушёл звонить в головной офис.

**Как это устроено в коде:**

- **Фотография** — `jcmd <pid> Thread.print`: все потоки, состояние каждого и стек вызовов.
- **Строка `java.lang.Thread.State`** — что поток делает в момент снимка.
- **Первый кадр приложения** — первая строка `at com.example…` сверху стека: где в нашем коде поток стоит.
- **Строки `- locked`, `- waiting to lock`, `- parking to wait for`** — какой монитор или замок поток держит или ждёт.

| Состояние | Что значит для бизнес-кода |
|---|---|
| `RUNNABLE` | выполняет код или сидит в системном вызове: чтение сокета тоже `RUNNABLE` |
| `BLOCKED` | ждёт входа в `synchronized`, который держит другой поток |
| `WAITING` | ждёт без срока: `LockSupport.park`, `Object.wait()`, `join()`. Так выглядит ожидание свободного соединения в пуле |
| `TIMED_WAITING` | ждёт со сроком: `sleep`, `parkNanos`, `wait(timeout)` |

Запуск на JDK 27, лаборатория `pool`, один из обработчиков:

```text
"http-nio-8080-exec-1" #25 [20868] prio=5 os_prio=0 cpu=15.62ms elapsed=7.09s … waiting on condition
   java.lang.Thread.State: WAITING (parking)
	at jdk.internal.misc.Unsafe.park(java.base@27/Native Method)
	- parking to wait for  <0x0000000703b85b50> (a java.util.concurrent.Semaphore$FairSync)
	…
	at java.util.concurrent.Semaphore.acquireUninterruptibly(java.base@27/Semaphore.java:341)
	at com.example.profcourse.t04.ConnectionPool.borrow(ConnectionPool.java:19)
	at com.example.profcourse.t04.OrderController.placeOrder(OrderController.java:20)
```

**Разбор:**

- `WAITING (parking)` на `Semaphore` внутри `ConnectionPool.borrow` — обработчик ждёт соединение с БД.
- `cpu=15.62ms` за 7 секунд жизни — поток почти не работал. Медленно не потому, что считаем, а потому, что ждём.

- Источник: https://docs.oracle.com/en/java/javase/27/docs/api/java.base/java/lang/Thread.State.html

> `BLOCKED` A thread that is blocked waiting for a monitor lock is in this state.
>
> `WAITING` A thread that is waiting indefinitely for another thread to perform a particular action is in this state.
>
> `TIMED_WAITING` A thread that is waiting for another thread to perform an action for up to a specified waiting time is in this state.

RU:

> `BLOCKED` — в этом состоянии поток, заблокированный в ожидании захвата монитора.
>
> `WAITING` — в этом состоянии поток, который бессрочно ждёт, пока другой поток выполнит определённое действие.
>
> `TIMED_WAITING` — в этом состоянии поток, который ждёт действия другого потока не дольше заданного времени.

**Ловушка:**

`RUNNABLE` не значит «грузит CPU». Поток, ждущий ответа базы в `socketRead`, тоже `RUNNABLE`.

**Правило:** в дампе группируют потоки по паре «состояние + первый кадр приложения»; самая большая группа — первая гипотеза.

---

## 2. Почему дампов нужно несколько

**Аналогия:**

По одной фотографии не понять, стоит ли машина в пробке или просто притормозила у светофора. Нужны три кадра с интервалом.

**Как это устроено в коде:**

- **Три кадра** — три `Thread.print` с интервалом 5–10 секунд.
- **Пробка** — тот же поток в том же кадре на всех снимках. Это зависание или очередь.
- **Светофор** — кадры разные, потоки двигаются. Это нормальная работа, медленная или нет — покажут метрики.
- **До перезапуска** — дамп снимают раньше, чем рестарт сотрёт картину.

```bash

for i in 1 2 3; do jcmd "$PID" Thread.print > "dump-$i.txt"; sleep 5; done
```

- Источник: https://docs.oracle.com/en/java/javase/27/troubleshoot/prepare-java-troubleshooting.html

> If the application stopped responding, then gather these files:
>
> - Stack traces: Take several stack traces using `jcmd Thread.print` before restarting the system

RU:

> Если приложение перестало отвечать, соберите следующие файлы:
>
> - Стеки потоков: снимите несколько стеков с помощью `jcmd Thread.print` до перезапуска системы

**Правило:** сервис «завис» — сначала три дампа с интервалом, потом перезапуск.

---

## 3. Очередь к пулу: CPU свободен, а всё стоит

**Аналогия:**

В банке четыре окна и двадцать клиентов, и каждый операционист, приняв клиента, две секунды ждёт ответа головного офиса. Очередь растёт, хотя операционисты почти ничего не делают.

**Как это устроено в коде:**

- **Окна** — пул на 4 соединения с БД (`ConnectionPool`).
- **Клиенты** — 20 обработчиков HTTP (`http-nio-8080-exec-N`).
- **Звонок в головной офис** — `OrderController.placeOrder` берёт соединение, вставляет заказ, **внутри транзакции** вызывает платёжный шлюз на 2 секунды и только потом отдаёт соединение.
- **Пропускная способность** — 4 соединения / 2 с = 2 заказа в секунду при любом числе обработчиков.

Запуск на JDK 27, лаборатория `pool`: дамп, сгруппированный по состоянию и первому кадру приложения.

| Состояние | Первый кадр приложения | Потоков |
|---|---|---|
| `WAITING` | `ConnectionPool.borrow` | 16 |
| `TIMED_WAITING` | `OrderController.chargeCard` | 4 |

```text
"http-nio-8080-exec-13" #37 … waiting on condition
   java.lang.Thread.State: TIMED_WAITING (sleeping)
	at java.lang.Thread.sleep(java.base@27/Thread.java:593)
	at com.example.profcourse.common.Slow.sleep(Slow.java:11)
	at com.example.profcourse.t04.OrderController.chargeCard(OrderController.java:36)
	at com.example.profcourse.t04.OrderController.placeOrder(OrderController.java:23)
```

**Разбор:**

- 16 потоков ждут соединение, 4 держат соединение и ждут шлюз. Число вторых равно размеру пула — значит, соединения заняты именно звонком.
- `Slow.sleep` — учебная заглушка сети; в проде здесь был бы `socketRead` в `RUNNABLE`. Поэтому первый кадр приложения берут из нашего кода, пропуская утилиты.
- Увеличить пул до 20 — переложить очередь в базу. Исправление — вынести вызов шлюза из транзакции.

**Ловушка:**

CPU-профиль этого сервиса пуст: потоки ничего не считают. Профайлер здесь не поможет, поможет дамп.

**Правило:** много потоков в ожидании пула и столько же, сколько соединений, — в одном внешнем вызове: виноват вызов внутри транзакции, а не размер пула.

---

## 4. Взаимная блокировка

**Аналогия:**

Двое в узком коридоре: каждый ждёт, пока другой отойдёт. Без вмешательства они простоят до конца смены.

**Как это устроено в коде:**

- **Коридор** — два счёта, каждый под своим `synchronized`.
- **Встречное движение** — перевод A → B берёт замок A, затем B; перевод B → A — замок B, затем A.
- **Вмешательство** — его нет: JVM не разрывает взаимную блокировку. Потоки висят до перезапуска.
- **Диагноз** — `Thread.print` сам находит цикл и печатает его в конце дампа.

Запуск на JDK 27, лаборатория `deadlock`, конец дампа:

```text
Found one Java-level deadlock:
=============================
"transfer-1":
  waiting to lock monitor 0x0000018f97e9ca30 (object 0x0000000703b84718, a com.example.profcourse.t04.Account),
  which is held by "transfer-2"

"transfer-2":
  waiting to lock monitor 0x0000018f97e9c170 (object 0x0000000703b846d0, a com.example.profcourse.t04.Account),
  which is held by "transfer-1"
…
Found 1 deadlock.
```

**Разбор:**

- Каждый поток держит один счёт (`- locked`) и ждёт второй (`- waiting to lock`). Оба в `BLOCKED` в `LegacyTransfers.transfer`.
- `cpu=0.00ms` и одинаковые кадры на всех дампах — признак зависания, а не медленной работы.
- Исправление — брать замки всегда в одном порядке, например по номеру счёта.

### То же в VisualVM

VisualVM 2.1.10, лаборатория `deadlock` на JDK 27. Потоки и снятие дампа — [локальная документация, «Потоки»](../../../docs/profiler/VisualVM/visualvm-docs-local-Ru/www/threads.html).

![VisualVM: вкладка Threads, Deadlock detected](images/04-visualvm-deadlock.png)

1. Двойной щелчок по процессу в **Applications**, затем вкладка **Threads**.
2. Красная надпись «Deadlock detected! Take a thread dump to get more info.» — VisualVM сам нашёл цикл.
3. Кнопка **Thread Dump** — снять дамп, тот же `Thread.print`.
4. Строки `transfer-1`, `transfer-2` на шкале времени — голубые, состояние **Monitor**: поток ждёт монитор и не двигается.

![VisualVM: конец дампа потоков](images/04-visualvm-thread-dump.png)

1. Конец дампа: `transfer-1` ждёт монитор `Account`, который держит `transfer-2`, и наоборот.
2. Стек каждого потока: `- waiting to lock` и `- locked` в `LegacyTransfers.transfer(LegacyTransfers.java:16)`.
3. Последняя строка — «Found 1 deadlock.»

**Правило:** зависшие переводы — сначала искать `Found one Java-level deadlock` в конце дампа; лечится единым порядком захвата.

---

## 5. Конкуренция за монитор

**Аналогия:**

Один журнал посещений на стойке и восемь посетителей. Пока один пишет, остальные стоят в очереди за ручкой.

**Как это устроено в коде:**

- **Журнал** — `AuditLog.write` под `synchronized`, запись занимает 5 мс.
- **Посетители** — 8 обработчиков, каждый пишет в журнал на каждый запрос.
- **Очередь** — в дампе: один поток внутри `write`, остальные `BLOCKED` на тот же объект.
- **Сколько стоят в очереди** — JFR пишет событие `jdk.JavaMonitorEnter` на каждое ожидание дольше 20 мс. Сводка: `jfr view contention-by-site`.

Запуск на JDK 27, лаборатория `audit`: дамп и запись JFR за 10 секунд.

```text
   java.lang.Thread.State: BLOCKED (on object monitor)
	at com.example.profcourse.lab.AuditLog.write(AuditLog.java:11)
	- waiting to lock <0x0000000704241e88> (a com.example.profcourse.lab.AuditLog)
```

```text
                               Contention by Site

StackTrace                                                  Count   Avg.   Max.
----------------------------------------------------------- ----- ------ ------
com.example.profcourse.lab.AuditLog.write(String)             531 131 ms 819 ms
```

**Разбор:**

- В дампе 7 потоков `BLOCKED` на одном `AuditLog`, восьмой спит внутри `write`. Обработчики работают по одному.
- Среднее ожидание — 131 мс при работе 5 мс; худшее — 819 мс: монитор не честный, кому-то не везёт много раз подряд.
- Дамп показывает очередь, JFR — её цену. Для доклада нужны оба.

- Источник: https://docs.oracle.com/en/java/javase/27/troubleshoot/troubleshoot-performance-issues-using-jfr.html

> Look at the locks that are contended the most and the stack trace of the threads waiting to acquire the lock. Typically, look for contention that you did not think would be an issue. Logging is a common area that can be an unexpected bottleneck in some applications.
>
> …
>
> By default, contention events with a duration longer than 20 ms are recorded.

RU:

> Смотрите самые конкурентные замки и стеки потоков, ждущих их захвата. Обычно ищут конкуренцию там, где её не ожидали. Логирование — частое место, которое в некоторых приложениях становится неожиданным узким местом.
>
> …
>
> По умолчанию записываются события конкуренции длительностью больше 20 мс.

### То же в JMC

Запись `audit` на 20 секунд с шаблоном `profile` — порог ожидания 10 мс вместо 20, поэтому событий больше, чем в сводке выше:

```bash

java -XX:StartFlightRecording:settings=profile,duration=20s,filename=build/lab/gui/audit.jfr \
  -cp build/classes/java/main com.example.profcourse.lab.LabApp audit
```

![JMC: Lock Instances](images/04-jmc-lock-instances.png)

1. **File → Open File…** → `audit.jfr`, в дереве — **Java Application → Lock Instances**.
2. **Monitor Class**: `com.example.profcourse.lab.AuditLog` — суммарно заблокированы 2 мин 19 с, 8 разных потоков, 2 080 ожиданий.
3. **Thread**: `http-nio-8080-exec-5…8` — по ~17,5 с ожидания и по 260 событий у каждого. Очередь равномерная: страдают все обработчики.
4. **Results**: правило **Java Blocking** — 100, красное. Текст правила называет тот же класс `AuditLog`.
5. **Flame View** внизу: все 2 080 ожиданий заканчиваются в `AuditLog.write(String)`.

**Разбор:**

- 2 мин 19 с ожидания за 20 секунд записи — 8 потоков почти всё время стоят в очереди.
- Число событий зависит от шаблона и длины: `default` за 10 секунд дал 531, `profile` за 20 секунд — 2 080. Сравнивают записи одной длины и с одним шаблоном.

**Правило:** много `BLOCKED` на одном объекте — критическая секция однопоточная; её сокращают, выносят медленную работу наружу или убирают общий замок.

---

## 6. Виртуальные потоки в дампе

**Аналогия:**

На фотографии зала видны только операционисты за стойками. Тех, кто ждёт в комнате отдыха, на ней нет.

**Как это устроено в коде:**

- **За стойкой** — виртуальный поток, который сейчас выполняется на платформенном (смонтирован).
- **В комнате отдыха** — виртуальные потоки, ждущие I/O или замка. `Thread.print` их не показывает.
- **Полный список** — `jcmd <pid> Thread.dump_to_file -format=json <file>`: все потоки, включая виртуальные.

- Источник: https://docs.oracle.com/en/java/javase/27/docs/specs/man/jcmd.html

> `Thread.print` [options]
> : Print all platform threads, and mounted virtual threads, with stack traces. The Thread.dump_to_file command will print all threads to a file.

RU:

> `Thread.print` [параметры]
> : Печатает все платформенные потоки и смонтированные виртуальные потоки со стеками. Команда Thread.dump_to_file выведет все потоки в файл.

Подробнее о виртуальных потоках — курс [`virtual-threads-study`](../../../virtual-threads-study/README.md).

**Правило:** сервис на виртуальных потоках — дамп через `Thread.dump_to_file`, иначе очередь не видна.

---

## Вопросы для интервью

1. Чем `BLOCKED` отличается от `WAITING`? В каком состоянии поток, ждущий соединение из пула?
2. Поток в `RUNNABLE`. Значит ли это, что он нагружает CPU?
3. Зачем снимать три дампа, а не один? Что сравнивать между ними?
4. 16 потоков ждут пул, 4 — во внешнем вызове. Увеличите пул? Почему?
5. Как JVM показывает взаимную блокировку? Как её исправить в коде переводов?
6. Дамп показывает 7 потоков `BLOCKED` на журнале аудита. Что ещё нужно, чтобы оценить ущерб?
7. Почему профайлер CPU бесполезен для сервиса, который ждёт пул?
8. Чем дамп сервиса на виртуальных потоках отличается и как снять полный?

---

**Практика:** [04-threads-tasks.md](04-threads-tasks.md)
