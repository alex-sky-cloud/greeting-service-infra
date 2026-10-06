# Тема 01. Поток и общая память

> **Статус:** теория · **После основной практики:** [`01-thread-basics-verify-tasks.md`](01-thread-basics-verify-tasks.md)

---

## Оглавление

- [Процесс и поток](#процесс-и-поток)
- [start и run](#start-и-run)
- [Состояния потока](#состояния-потока)
- [join: дождаться результата](#join-дождаться-результата)
- [Прерывание потока](#прерывание-потока)
- [Когда завершается программа](#когда-завершается-программа)
    - [Daemon-потоки программу не задерживают](#daemon-потоки-программу-не-задерживают)
    - [Три способа дождаться потока — и их разница](#три-способа-дождаться-потока--и-их-разница)
- [Типичные ошибки](#типичные-ошибки)
- [Практика](#практика)

---

## Процесс и поток

Представьте кухню ресторана. **Процесс** — это отдельная кухня со своими продуктами и своими ножами: что там происходит, соседней кухне не видно. **Поток** — это повар внутри одной кухни. Поваров может быть несколько, но холодильник у них один, общий.

Теперь переведём аналогию на технический язык.

**Программа** — это код, сохранённый на диске. Когда операционная система запускает программу, появляется **процесс** — выполняющийся экземпляр этой программы со своей областью памяти и открытыми ресурсами. Например, Java-приложение, сервер PostgreSQL и брокер Kafka обычно работают как отдельные процессы; их можно увидеть в диспетчере задач.

Java-код выполняет **JVM** (Java Virtual Machine, виртуальная машина Java). Когда вы запускаете команду `java Kitchen`, операционная система запускает процесс JVM, JVM загружает класс `Kitchen` и создаёт поток `main`, который начинает выполнять метод `main`.

Внутри процесса работает как минимум один **поток** — последовательность выполняемых команд. Java-приложение может создать дополнительные потоки. Все потоки одного процесса могут обращаться к общей памяти процесса, поэтому им доступны одни и те же объекты и поля, если у них есть ссылки на эти данные.

Ниже поток `main` создаёт дополнительный поток `cook`. Оба обращаются к одному статическому полю `fridge`.

Инструкции для потока записываются в методе `run()` интерфейса `Runnable`. В этом примере метод `run()` написан явно, чтобы было видно, где лежат инструкции и какой поток их выполняет.

```java

public class Kitchen {
    static String fridge = "пусто"; // Одно поле класса, общее для main и cook.

    public static void main(String[] args) throws InterruptedException {
        Runnable task = new Runnable() { // Объект с инструкциями для будущего потока.
            @Override
            public void run() { // Тело run() — набор инструкций, которые выполнит поток.
                System.out.println("run() выполняет поток " + Thread.currentThread().getName());
                fridge = "яйцо"; // Поток кладёт яйцо в общий холодильник.
            }
        };

        Thread cook = new Thread(task, "cook"); // Создаём поток cook и передаём ему task. Поток ещё не работает.
        cook.start(); // main запускает cook. Дальше cook сам вызывает task.run().
        cook.join();  // main ждёт, пока cook выполнит run() до конца.

        System.out.println(fridge);
    }
}
```

Результат:

```text
run() выполняет поток cook
яйцо
```

### Кто вызывает `run()`

В коде нет строки `task.run()`, и это сделано намеренно: метод `run()` вызываем не мы. Последовательность такая:

1. Поток `main` выполняет `new Thread(task, "cook")`. Создан объект потока, внутри него сохранена ссылка на `task`. Поток ещё ничего не выполняет.
2. Поток `main` вызывает `cook.start()`. JVM запускает новый поток `cook`, и `main` сразу идёт к следующей строке.
3. Запущенный поток `cook` вызывает свой метод `Thread.run()`, а тот вызывает `task.run()`.
4. Поток `cook` выполняет инструкции из тела `run()`: печатает своё имя и записывает `"яйцо"` в `fridge`.
5. Метод `run()` закончился — поток `cook` завершён. `main` выходит из `cook.join()` и печатает `fridge`.

Первая строка вывода это подтверждает: `Thread.currentThread().getName()` возвращает имя потока, который выполняет эту строку, и это `cook`, а не `main`.

Что ещё в примере не видно напрямую:

- `static` означает, что поле `fridge` принадлежит самому классу `Kitchen`, а не отдельному объекту. В памяти одно такое поле, и к нему обращаются оба потока.
- `new Runnable() { ... }` — анонимный класс: класс без имени, который реализует интерфейс `Runnable` и сразу создаёт свой объект.
- `@Override` отмечает, что метод `run()` реализует метод из интерфейса `Runnable`. Если ошибиться в имени метода, компилятор сообщит об ошибке.
- `throws InterruptedException` в объявлении `main` нужен, потому что `join()` может быть прерван. Прерывание разберём ниже в этой теме.

### Короткая запись через лямбду

Тот же `Runnable` обычно записывают короче — лямбда-выражением:

```java

Thread cook = new Thread(() -> fridge = "яйцо", "cook"); // Лямбда — это тело метода run().
```

Здесь лямбда `() -> fridge = "яйцо"` заменяет весь анонимный класс:

- `()` — список параметров метода `run()`. Он пустой, потому что `run()` ничего не принимает.
- `->` отделяет параметры от тела метода.
- `fridge = "яйцо"` — тело метода `run()`, то есть инструкции для потока.

Java понимает, что лямбда реализует именно `run()`, потому что конструктор `Thread` ожидает `Runnable`, а у `Runnable` единственный абстрактный метод — `run()`. Всё остальное не меняется: `cook.start()` запускает поток, и уже поток `cook` вызывает `run()`, то есть выполняет тело лямбды.

- Источник: https://docs.oracle.com/javase/tutorial/essential/concurrency/procthread.html

> A process has a self-contained execution environment. A process generally has a complete, private set of basic run-time resources; in particular, each process has its own memory space.
>
> Threads exist within a process — every process has at least one. Threads share the process's resources, including memory and open files. This makes for efficient, but potentially problematic, communication.

RU:

> Процесс имеет самостоятельную среду выполнения. Обычно процесс располагает полным собственным набором основных ресурсов времени выполнения; в частности, у каждого процесса есть собственное пространство памяти.
>
> Потоки существуют внутри процесса — у каждого процесса есть по меньшей мере один. Потоки разделяют ресурсы процесса, включая память и открытые файлы. Это делает обмен данными эффективным, но потенциально проблемным.

**Правило:** процесс имеет собственную память, а потоки внутри процесса используют её совместно. Поэтому общее изменяемое поле — место возможной ошибки.

---

## start и run

- `run()` — описание инструкций, которые должен выполнить поток.
- `start()` — запускает выполнение этих инструкций в новом потоке.

Инструкции мы записали в лямбде, а лямбда — это и есть тело метода `run()`. Значит, у нас уже есть объект `Runnable` с готовым методом `run()`, и его можно вызвать как обычный метод любого объекта. Новый поток в этом случае не запускается, поэтому инструкции выполнит тот поток, который дошёл до строки с вызовом. Внутри метода `main` это поток `main`.

Пример показывает оба случая и печатает имя исполнителя: `Thread.currentThread()` возвращает поток, выполняющий текущую строку, а `getName()` — его имя.

```java

public class StartVsRun {
    public static void main(String[] args) {
        // Лямбда — это тело метода run(), то есть инструкции для исполнителя.
        Runnable task = () -> System.out.println("инструкции выполняет поток " + Thread.currentThread().getName());

        task.run(); // Вызов метода run() у объекта task. Новый поток не создаётся.

        new Thread(task, "worker").start(); // Запуск нового потока worker. Он сам вызовет run().
    }
}
```

Результат:

```text
инструкции выполняет поток main
инструкции выполняет поток worker
```

Первую строку напечатал `main`: вызов `task.run()` — это обычный вызов метода, потоки здесь не участвуют, поэтому инструкции выполнил тот же поток, что выполняет `main`.

Вторую строку напечатал `worker`: `new Thread(task, "worker")` создал объект потока и сохранил в нём `task`, а `start()` запустил этот поток. Дальше `worker` работает самостоятельно и сам вызывает `run()`, то есть выполняет тело лямбды. Поток `main` его инструкции не выполняет и сразу идёт дальше по своему коду.

### Что именно делает `start()`

Ниже — описание метода `Thread.start()` из документации JDK 17. Курс идёт на JDK 17 и новее, поэтому цитаты приводятся из этой версии.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html

> Causes this thread to begin execution; the Java Virtual Machine calls the `run` method of this thread.
>
> The result is that two threads are running concurrently: the current thread (which returns from the call to the `start` method) and the other thread (which executes its `run` method).

RU:

> Заставляет этот поток начать выполнение; виртуальная машина Java вызывает метод `run` этого потока.
>
> В результате одновременно работают два потока: текущий поток (который возвращается из вызова метода `start`) и другой поток (который выполняет свой метод `run`).

Из этого описания следуют три факта.

**Первый факт:** `start()` — это и есть запуск потока. После вызова поток переходит из состояния `NEW` в `RUNNABLE`, то есть его жизненный цикл начался.

**Второй факт**: метод `run()` вызывает виртуальная машина, а не наш код. Поэтому в примере `Kitchen` строки `task.run()` нет, но инструкции выполняются.

**Третий факт**: вызывающий поток ничего не ждёт — он возвращается из `start()` и продолжает выполнять свои строки. С этого момента в программе работают два потока одновременно.

Запуск потока не означает, что поток немедленно получит процессор. Процессорное время между потоками распределяет операционная система.

- Источник: https://docs.oracle.com/javase/tutorial/essential/concurrency/procthread.html

> Processing time for a single core is shared among processes and threads through an OS feature called time slicing.

RU:

> Процессорное время одного ядра распределяется между процессами и потоками с помощью возможности операционной системы, которая называется разделением времени (time slicing).

Поэтому состояние `RUNNABLE` означает «поток запущен и готов выполняться», а не «поток прямо сейчас занимает процессор».

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.State.html

> Thread state for a runnable thread. A thread in the runnable state is executing in the Java virtual machine but it may be waiting for other resources from the operating system such as processor.

RU:

> Состояние потока для потока, готового к выполнению. Поток в состоянии runnable выполняется в виртуальной машине Java, но может ожидать другие ресурсы операционной системы, например процессор.

Практическое следствие: сразу после `start()` новый поток может ещё не выполнить ни одной инструкции. Именно поэтому в примере `Kitchen` поле `fridge` читается после `join()`.

Запустить один объект `Thread` можно только один раз.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html

> It is never legal to start a thread more than once. In particular, a thread may not be restarted once it has completed execution.

RU:

> Запускать поток более одного раза недопустимо. В частности, поток нельзя перезапустить после того, как он завершил выполнение.

Повторный вызов `start()` на том же объекте выбрасывает `IllegalThreadStateException`. Чтобы выполнить ту же работу ещё раз, создают новый объект `Thread` и передают ему тот же `Runnable`.

### Почему `thread.run()` вызывать не нужно

Класс `Thread` сам реализует интерфейс `Runnable`, поэтому метод `run()` есть и у объекта потока. Вот что о нём написано в javadoc метода `Thread.run()` в JDK 17.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html

> If this thread was constructed using a separate `Runnable` run object, then that `Runnable` object's `run` method is called; otherwise, this method does nothing and returns.

RU:

> Если этот поток был создан с отдельным объектом-задачей `Runnable`, то вызывается метод `run` этого объекта `Runnable`; иначе метод ничего не делает и завершается.

Значит, `worker.run()` только перенаправляет вызов на `task.run()`. Это обычный вызов метода, поэтому новый поток не запускается, а инструкции выполняет текущий поток. Результат такой же, как у `task.run()` в примере выше, хотя строка внешне похожа на запуск потока.

В документации JDK 21 это сказано прямым запретом — такую же подсказку выводит IntelliJ IDEA в предупреждении *«Calls to 'run()' should probably be replaced with 'start()'»*.

- Источник: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html

> This method is not intended to be invoked directly.

RU:

> Этот метод не предназначен для прямого вызова.

**Правило:** `run()` только описывает инструкции и поток не создаёт. Поток запускает `start()`, после чего JVM вызывает `run()` у этого потока, а вызывающий поток продолжает работу параллельно.

---

## Состояния потока

**Аналогия** — сотрудник в течение дня: 
  - ещё не пришёл, 
  - работает, 
  - стоит под дверью занятого кабинета, 
  - ждёт звонка неизвестно сколько, 
  - ждёт до 10:00, 
  - ушёл домой.

Технически состояние показывает, что поток делает прямо сейчас или чего он ожидает. 

**Монитор** в таблице — встроенный замок Java-объекта: если замок уже занят другим потоком, войти в защищённый участок кода нельзя. Подробно монитор разберём в теме про `synchronized`.

Тем же языком про поток:

| Состояние | Когда | Житейски |
|---|---|---|
| `NEW` | объект создан, `start()` не вызван | не пришёл |
| `RUNNABLE` | выполняется в JVM | работает |
| `BLOCKED` | ждёт замок, чтобы войти в `synchronized` | стоит под занятой дверью |
| `WAITING` | `wait()`, `join()` без таймаута | ждёт звонка без срока |
| `TIMED_WAITING` | `sleep(…)`, `wait(…)`, `join(…)` с таймаутом | ждёт до назначенного часа |
| `TERMINATED` | работа закончилась | ушёл |

- Источник: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.State.html

> `NEW` A thread that has not yet started is in this state.
> `RUNNABLE` A thread executing in the Java virtual machine is in this state.
> `BLOCKED` A thread that is blocked waiting for a monitor lock is in this state.
> `WAITING` A thread that is waiting indefinitely for another thread to perform a particular action is in this state.
> `TIMED_WAITING` A thread that is waiting for another thread to perform an action for up to a specified waiting time is in this state.
> `TERMINATED` A thread that has exited is in this state.

RU:

> `NEW` Поток, который ещё не был запущен, находится в этом состоянии.
> 
> `RUNNABLE` Поток, выполняющийся в виртуальной машине Java, находится в этом состоянии.
>
> `BLOCKED` Поток, заблокированный в ожидании захвата монитора, находится в этом состоянии.
> 
> `WAITING` Поток, неограниченно долго ожидающий, пока другой поток выполнит определённое действие, находится в этом состоянии.
> 
> `TIMED_WAITING` Поток, ожидающий выполнения действия другим потоком не дольше указанного времени ожидания, находится в этом состоянии.
> 
> `TERMINATED` Поток, который завершился, находится в этом состоянии.

Важная оговорка из того же источника: это состояния виртуальной машины, а не операционной системы.

> A thread can be in only one state at a given point in time. These states are virtual machine states which do not reflect any operating system thread states.

RU:

> В каждый момент времени поток может находиться только в одном состоянии. Это состояния виртуальной машины, которые не отражают какие-либо состояния потоков операционной системы.

**Правило:** 
  - состояние потока `BLOCKED` — означает, что поток ждёт замок,
  - состояние потока `WAITING` — ждёт действия другого потока. На собеседовании их часто путают.

---

Поток в каждый момент находится ровно в одном из шести состояний. Это состояния виртуальной машины, а не операционной системы.

- Источник: https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.State.html

EN:

> A thread can be in only one state at a given point in time. These states are virtual machine states which do not reflect any operating system thread states.

RU:

> Поток может находиться только в одном состоянии в каждый момент времени. Это состояния виртуальной машины, которые не отражают состояния потоков операционной системы.

## NEW

NEW — это состояние потока, который ещё не запущен. Объект `Thread` создан и описывает будущий поток, но `start()` не вызывали. Инструкции потока лежат в `run()`.

- Источник: https://www.baeldung.com/java-thread-lifecycle

EN:

> A NEW Thread (or a Born Thread) is a thread that's been created but not yet started.

RU:

> NEW-поток (или «рождённый» поток) — это поток, который создан, но ещё не запущен.

## RUNNABLE

RUNNABLE — это состояние после вызова `start()`. Поток считается RUNNABLE и когда он выполняется на процессоре, и когда он готов выполняться, но ждёт процессор. Состояния RUNNING в Java нет. Планировщик решает, когда поток получит процессорное время, а состояние при этом не меняется.

- Источник: https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.State.html

EN:

> A thread in the runnable state is executing in the Java virtual machine but it may be waiting for other resources from the operating system such as processor.

RU:

> Поток в состоянии runnable выполняется в виртуальной машине Java, но может ожидать других ресурсов от операционной системы, например процессор.

- Источник: https://www.baeldung.com/java-thread-lifecycle

EN:

> Threads in this state are either running or ready to run, but they're waiting for resource allocation from the system.

RU:

> Потоки в этом состоянии либо выполняются, либо готовы выполняться, но ждут выделения ресурсов от системы.

## BLOCKED

BLOCKED — поток ждёт монитор. Он хочет войти в `synchronized`-блок или метод, но монитор общего ресурса занят другим потоком. Когда монитор освободится, поток захватит его и вернётся в RUNNABLE. В BLOCKED попадает и поток, который после `Object.wait()` заново входит в `synchronized`.

- Источник: https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.State.html

EN:

> A thread in the blocked state is waiting for a monitor lock to enter a synchronized block/method or reenter a synchronized block/method after calling Object.wait.

RU:

> Поток в состоянии blocked ждёт монитор, чтобы войти в synchronized-блок или метод либо повторно войти в него после вызова Object.wait.

## WAITING

WAITING — поток без ограничения по времени ждёт определённого действия от другого потока. Это ожидание события, а не монитора. Поток попадает сюда после `Object.wait()`, `Thread.join()` или `LockSupport.park()` без таймаута.

Пример: поток A вызвал `lock.wait()` и отпустил монитор. Он остаётся в WAITING, пока поток B не вызовет `lock.notify()` или `notifyAll()`. После этого A должен снова захватить монитор. Если монитор занят, A сначала окажется в BLOCKED, затем перейдёт в RUNNABLE.

- Источник: https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.State.html

EN:

> For example, a thread that has called Object.wait() on an object is waiting for another thread to call Object.notify() or Object.notifyAll() on that object. A thread that has called Thread.join() is waiting for a specified thread to terminate.

RU:

> Например, поток, вызвавший Object.wait() на объекте, ждёт, пока другой поток вызовет Object.notify() или Object.notifyAll() на этом объекте. Поток, вызвавший Thread.join(), ждёт завершения указанного потока.

---

- **WAITING:** поток сам вызвал `wait()`. Монитор он при этом отпустил и ждёт только сигнала (`notify`). Пока сигнала нет, монитор потоку не нужен.
- **BLOCKED:** поток хочет войти в `synchronized`, но монитор занят. Он не ждёт сигнала, а ждёт освобождения монитора.

Пример с `wait()`. Поток «Пассажир» ждёт объявления о посадке, а поток «Диспетчер» его делает. Общий ресурс — табло `board`, монитор которого может держать только один поток.

1. «Пассажир» входит в `synchronized(board)` и вызывает `board.wait()`. Он отпускает монитор табло и переходит в **WAITING**: ждёт объявления о посадке.
2. «Диспетчер» входит в `synchronized(board)` и вызывает `board.notify()`. «Пассажир» получил сигнал, но «Диспетчер» ещё держит монитор табло.
3. Теперь «Пассажиру» нужно заново захватить монитор, а он занят. Поэтому «Пассажир» переходит в **BLOCKED**.
4. «Диспетчер» выходит из `synchronized`, «Пассажир» захватывает монитор и становится **RUNNABLE**.

Фраза «в BLOCKED попадает и поток, который после `Object.wait()` заново входит в `synchronized`» описывает шаг 3. Это не то же самое, что WAITING на шаге 1.


|  | WAITING | BLOCKED |
| :-- | :-- | :-- |
| Чего ждёт | Сигнала или события (`notify`, завершение потока, `unpark`) | Освобождения монитора |
| Как попал | Сам вызвал `wait()`, `join()` или `park()` | Попытался войти в `synchronized`, а монитор занят |
| Держит монитор | Нет, `wait()` его отпустил | Нет, он его ещё не получил |

В аналогии «Пассажир» в WAITING сидит в зале ожидания и слушает объявление. В BLOCKED он уже услышал объявление и стоит у закрытой двери на платформу, пока «Диспетчер» её не откроет.



---


## TIMED_WAITING

TIMED_WAITING — то же ожидание, но с ограничением по времени. Поток попадает сюда после `Thread.sleep`, `Object.wait(timeout)`, `Thread.join(timeout)`, `LockSupport.parkNanos` или `LockSupport.parkUntil`.

Когда время истекло, поток просыпается сам и возвращается в RUNNABLE. Если он ждал через `wait(timeout)`, то сначала заново захватывает монитор, и при занятом мониторе проходит через BLOCKED. Если сигнал пришёл раньше таймаута (`notify` или завершение потока при `join`), поток просыпается раньше.

- Источник: https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.State.html

EN:

> A thread is in the timed waiting state due to calling one of the following methods with a specified positive waiting time: Thread.sleep, Object.wait with timeout, Thread.join with timeout, LockSupport.parkNanos, LockSupport.parkUntil

RU:

> Поток находится в состоянии timed waiting из-за вызова одного из следующих методов с заданным положительным временем ожидания: Thread.sleep, Object.wait с таймаутом, Thread.join с таймаутом, LockSupport.parkNanos, LockSupport.parkUntil.

Описание того, что поток делает после истечения таймаута, — это мой вывод из определения состояния. 

---

TIMED_WAITING — это WAITING с будильником: поток ждёт сигнала, но не дольше заданного времени. Какой из двух событий случится первым, то и разбудит поток.

## Что происходит с потоком

Сначала поток вызывает метод с таймаутом, например `board.wait(5000)`, и переходит в **TIMED_WAITING**. Дальше возможны два исхода:

- **Таймаут истёк:** поток просыпается сам, без сигнала.
- **Сигнал пришёл раньше:** приходит `notify`, или завершается поток, которого ждали через `join(timeout)`. Тогда поток просыпается раньше таймаута.

После пробуждения поток всегда идёт дальше по одному из двух путей.


| Как поток ждал | Что после пробуждения |
| :-- | :-- |
| `sleep(ms)`, `join(ms)`, `parkNanos`, `parkUntil` | Сразу **RUNNABLE** |
| `wait(ms)` | Сначала нужно заново захватить монитор. Если он свободен, поток сразу **RUNNABLE**. Если занят, поток идёт в **BLOCKED**, а потом в **RUNNABLE**. |

Для `wait(ms)` нужен монитор потому, что `wait` всегда вызывается внутри `synchronized` и при ожидании монитор отпускает. Вернуться в `synchronized` поток может, только забрав монитор обратно. Это то же правило, что и после обычного `wait()`.

## Пример

Поток «Пассажир» ждёт объявления о посадке не дольше 5 секунд. Монитор табло `board` держит «Диспетчер».

1. «Пассажир» вызывает `board.wait(5000)` и переходит в **TIMED_WAITING**.
2. Через 5 секунд объявления нет, и «Пассажир» просыпается сам.
3. Если «Диспетчер» в этот момент держит монитор табло, «Пассажир» уходит в **BLOCKED**.
4. Когда «Диспетчер» отпускает монитор, «Пассажир» захватывает его и становится **RUNNABLE**.

Для сравнения, `Thread.sleep(5000)` монитор не использует. По истечении 5 секунд поток сразу идёт в **RUNNABLE**.


---

## TERMINATED

TERMINATED — поток завершился: `run()` вернул управление нормально или из-за необработанного исключения. Перезапустить такой поток нельзя.

- Источник: https://www.baeldung.com/java-thread-lifecycle

EN:

> It's in the TERMINATED state when it has either finished execution or was terminated abnormally.

RU:

> Поток находится в состоянии TERMINATED, когда он либо завершил выполнение, либо был завершён аварийно.

## Жизненный цикл

Этот раздел — моё обобщение переходов по источникам выше.

1. NEW → `start()` → RUNNABLE.
2. RUNNABLE ↔ BLOCKED: поток ждёт монитор `synchronized`, затем получает его.
3. RUNNABLE ↔ WAITING: поток вызвал `wait()`, `join()` или `park()`, затем получил `notify`, завершение другого потока или `unpark`.
4. RUNNABLE ↔ TIMED_WAITING: поток вызвал `sleep(ms)` или метод с таймаутом, затем истёк таймаут или пришёл сигнал.
5. WAITING или TIMED_WAITING → BLOCKED, если после пробуждения монитор занят. Затем BLOCKED → RUNNABLE.
6. RUNNABLE → TERMINATED, когда `run()` завершился.

Порядок не жёсткий: поток может много раз переходить между RUNNABLE и состояниями ожидания. NEW всегда первое состояние, TERMINATED всегда последнее.


---

## join: дождаться результата

Аналогия: вы попросили коллегу посчитать выручку и не уходите домой, пока он не положит отчёт на стол. Без этого ожидания вы возьмёте со стола пустой лист.

```java

public class Report {
    static int revenue; // Общее поле. До присваивания содержит 0.

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(500); // Имитируем долгий расчёт: worker полсекунды занят.
            } catch (InterruptedException e) {
                return;
            }
            revenue = 1000; // Результат расчёта записывается в общее поле.
        });

        worker.start(); // worker начинает расчёт независимо от main.
        System.out.println("До join: " + revenue);

        worker.join(); // main останавливается здесь до завершения worker.
        System.out.println("После join: " + revenue);
    }
}
```

Результат:

```text
До join: 0
После join: 1000
```

`Thread.sleep(500)` приостанавливает поток `worker` на полсекунды и нужен только для того, чтобы расчёт занимал заметное время, как реальная работа. 
- Блок `try`/`catch` обязателен, потому что `sleep()` может быть прерван и тогда выбрасывает `InterruptedException`; прерывание разобрано ниже, в разделе «Прерывание потока».

Первая строка печатает `0`: 
  - `main` продолжил работу сразу после `start()`, а `worker` в этот момент ещё спит и ничего не записал. 
  - Вторая строка печатает `1000`: `join()` остановил `main` до завершения `worker`, а к этому моменту присваивание уже выполнено.


Если убрать `join()`, вторая строка тоже напечатает `0`. 
 - `main` не станет ждать, прочитает поле, пока `worker` ещё спит, и завершится. 
 - Сам `worker` при этом доработает и запишет `1000`, потому что JVM дожидается не-daemon-потоков, — но этого значения уже никто не прочитает.

Почему в примере понадобилась задержка: 
 - без неё работа `worker` — одно присваивание, которое занимает микросекунды. 
 - Такой поток почти всегда успевает закончить раньше, чем `main` дойдёт до второй строки, и программа без `join()` выглядит работающей правильно. 
 - Но это совпадение по времени, а не гарантия: в реальной программе работа потока длится дольше, и чтение без `join()` вернёт незаконченный результат.

- Источник: https://docs.oracle.com/javase/tutorial/essential/concurrency/join.html

> The `join` method allows one thread to wait for the completion of another.

RU:

> Метод `join` позволяет одному потоку дождаться завершения другого.

`join()` даёт не только ожидание, но и видимость результата: после возврата из `join()` вы гарантированно видите всё, что успел записать тот поток. Это правило зафиксировано в спецификации языка.

- Источник: https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html

> All actions in a thread happen-before any other thread successfully returns from a `join()` on that thread.

RU:

> Все действия в потоке происходят-до (happens-before) успешного возврата любого другого потока из `join()` на этом потоке.

Обратное правило действует на старте:

> A call to `start()` on a thread happens-before any actions in the started thread.

RU:

> Вызов `start()` на потоке происходит-до (happens-before) любых действий в запущенном потоке.

**Правило:** результат чужого потока читают после `join()`, а не «через пару строк после `start()`».

---

## Прерывание потока

Метод `join()` ждёт, пока поток сам дойдёт до конца. Иногда нужно другое: один поток просит другой **остановить текущую работу раньше**. Например, пользователь нажал «Отмена», пока отчёт ещё выгружается.

**Аналогия:** начальник говорит сотруднику «закончи текущее дело и выходи из задачи». Он не выключает компьютер сотрудника и не вырывает его из кабинета — сотрудник сам решает, как завершить работу. Если сотрудник в этот момент спит в перерыве, его будят и передают просьбу.

**Технический перевод:** поток `main` (или любой другой) вызывает `worker.interrupt()` у объекта `Thread`. JVM **не останавливает** поток `worker` силой. Она помечает у этого потока **флаг прерывания** (interrupt status): «поступила просьба остановиться». Дальше всё зависит от того, чем `worker` занят в этот момент.

- Источник: Java Tutorials, раздел *Interrupts* (JDK 17)

> An interrupt is an indication to a thread that it should stop what it is doing and do something else. It's up to the programmer to decide exactly how a thread responds to an interrupt, but it is very common for the thread to terminate.

RU:

> Прерывание — это указание потоку, что он должен прекратить то, что делает, и заняться чем-то другим. Программист сам решает, как именно поток отвечает на прерывание, но очень часто поток завершается.

Из цитаты следует: `interrupt()` — это **сигнал программисту и потоку**, а не команда JVM «принудительно остановить поток». Ответ на просьбу пишет код внутри `run()`: часто поток просто выходит из метода и завершается.

### Флаг прерывания

У каждого объекта `Thread` есть внутренний флаг: **прерван / не прерван**. Его не видно в вашем коде как поле, но его читают методы `Thread`.

| Состояние флага | Смысл |
|---|---|
| не прерван | просьбы остановиться не было |
| прерван | кто-то вызвал `interrupt()` у этого потока, а поток ещё не обработал просьбу |

Вызов `worker.interrupt()` переводит флаг потока `worker` в состояние **прерван**. Отдельного «события», которое само по себе прерывает цикл `for` или `while`, **нет**: поток должен либо выйти из ожидания через `InterruptedException`, либо сам проверить флаг в коде.

- Источник: Java Tutorials, подраздел *The Interrupt Status Flag* (JDK 17)

> The interrupt mechanism is implemented using an internal flag known as the interrupt status. Invoking `Thread.interrupt` sets this flag.

RU:

> Механизм прерывания реализован с помощью внутреннего флага, известного как статус прерывания. Вызов `Thread.interrupt` устанавливает этот флаг.

### Два случая после `interrupt()`

Поведение описано в javadoc метода `Thread.interrupt()` (JDK 17).

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#interrupt()

> If this thread is blocked in an invocation of the … `join()` … `sleep(long)` … methods of this class, then its interrupt status will be cleared and it will receive an `InterruptedException`.
>
> If none of the previous conditions hold then this thread's interrupt status will be set.

RU:

> Если этот поток заблокирован в вызове … `join()` … `sleep(long)` … этого класса, его статус прерывания будет сброшен, и он получит `InterruptedException`.
>
> Если ни одно из предыдущих условий не выполняется, статус прерывания этого потока будет установлен.

| Чем занят поток в момент `interrupt()` | Что происходит дальше |
|---|---|
| ждёт в `sleep`, `join` или `wait` | ожидание прерывается, выбрасывается `InterruptedException`, флаг сбрасывается |
| выполняет обычный код (цикл, расчёт) | поток **продолжает работать**; флаг остаётся «прерван», пока код сам не проверит его и не выйдет |

### Поток ждёт: `sleep`

Пример: `worker` крутит цикл с паузами, `main` через 300 мс просит его остановиться.

```java

public class Stopping {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(100); // worker ждёт. В этот момент его могут прервать.
                }
            } catch (InterruptedException e) {
                // sleep завершился из-за interrupt(): сюда попали, run() дальше не идёт.
                System.out.println("worker получил просьбу остановиться");
            }
        });

        worker.start();     // JVM запускает worker; worker заходит в sleep.
        Thread.sleep(300);  // main тоже ждёт 300 мс.
        worker.interrupt(); // main помечает worker как «прерван»; sleep worker просыпается с исключением.
        worker.join();      // main ждёт, пока worker выйдет из run().
    }
}
```

Пошагово:

1. `worker` внутри `Thread.sleep(100)` — поток не считает, он **ждёт** (состояние `TIMED_WAITING`).
2. `main` вызывает `worker.interrupt()`.
3. JVM прерывает ожидание: метод `sleep` **не дожидается** оставшегося времени и выбрасывает `InterruptedException` в потоке `worker`.
4. Управление попадает в `catch`. После блока `catch` метод `run()` заканчивается — поток `worker` **завершился**.
5. `main` выходит из `worker.join()`.

Почему флаг «сбрасывается»: javadoc `Thread.sleep(long)` (JDK 17) говорит, что при `InterruptedException` **статус прерывания текущего потока очищается**. То есть после `catch` нельзя просто прочитать флаг — просьба уже «учтена» самим методом `sleep`. Обычно в `catch` либо выходят из `run()`, либо снова вызывают `Thread.currentThread().interrupt()`, если нужно передать просьбу дальше.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#sleep(long)

> The interrupted status of the current thread is cleared when this exception is thrown.

RU:

> Статус прерывания текущего потока очищается, когда выбрасывается это исключение.

### Поток считает без пауз

Если `worker` крутит цикл **без** `sleep`, `wait` и `join`, `interrupt()` **не выбросит** исключение сам по себе. Флаг станет «прерван», но цикл продолжится, пока в коде нет проверки.

```java

public class StoppingBusy {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            long checked = 0;
            while (!Thread.currentThread().isInterrupted()) { // Пока флаг «не прерван», крутим цикл.
                checked++; // Имитация работы без пауз внутри цикла.
            }
            System.out.println("worker остановился, проверок: " + checked);
        });

        worker.start();
        Thread.sleep(50);   // main даёт worker поработать.
        worker.interrupt(); // Флаг worker становится «прерван»; цикл сам этого не замечает.
        worker.join();
    }
}
```

Здесь `main` не «врывается» в цикл. Он только ставит флаг. Поток `worker` замечает просьбу **на следующей итерации**, когда выполняется `isInterrupted()`. Число `checked` будет большим — это нормально.

- Источник: Java Tutorials, *Supporting Interruption* (JDK 17)

> What if a thread goes a long time without invoking a method that throws `InterruptedException`? Then it must periodically invoke `Thread.interrupted`, which returns `true` if an interrupt has been received.

RU:

> Что, если поток долго не вызывает метод, который выбрасывает `InterruptedException`? Тогда он должен периодически вызывать `Thread.interrupted`, который возвращает `true`, если прерывание было получено.

В учебнике для проверки внутри **текущего** потока часто пишут `Thread.interrupted()`. В условии цикла удобнее `Thread.currentThread().isInterrupted()` — смысл тот же: «просили ли **меня** остановиться».

### Как прочитать флаг: `interrupted()` и `isInterrupted()`

Оба метода отвечают на вопрос «флаг прерван?», но по-разному относятся к самому флагу.

| Метод | Кто проверяется | Флаг после вызова |
|---|---|---|
| `Thread.interrupted()` | **текущий** поток (тот, в чьём коде стоит вызов) | сбрасывается в «не прерван», если до вызова был «прерван» |
| `worker.isInterrupted()` | поток `worker` | **не меняется** |

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#interrupted()

> Tests whether the current thread has been interrupted. The interrupted status of the thread is cleared by this method.

RU:

> Проверяет, был ли прерван текущий поток. Статус прерывания потока этим методом **очищается**.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#isInterrupted()

> Tests whether this thread has been interrupted. The interrupted status of the thread is unaffected by this method.

RU:

> Проверяет, был ли прерван этот поток. Статус прерывания потока этим методом **не изменяется**.

**Зачем два метода:** `interrupted()` — «прочитал просьбу один раз и снял флажок», чтобы не обрабатывать её повторно. `isInterrupted()` — «посмотреть, не просили ли остановить **этот** поток», не трогая флаг; удобно в цикле или когда `main` смотрит состояние `worker`.

**Правило:** `interrupt()` просит поток остановить работу; JVM не останавливает произвольный код за вас. В ожидании (`sleep`, `join`, `wait`) ответ часто приходит как `InterruptedException`. В расчёте без пауз поток должен сам проверять флаг и выходить из цикла.

---

## Когда завершается программа

Метод `join()` из предыдущего раздела — это ожидание, которое мы написали сами: поток `main` стоит ровно в той строке, где мы вызвали `join()`. Если `join()` не вызывать, `main` до своей последней строки дойдёт раньше остальных потоков и закончится.

Возникает вопрос: завершится ли при этом вся программа. Нет. Завершение процесса зависит не от потока `main`, а от того, остались ли в программе работающие **не-daemon** потоки. Решение принимает JVM, а не поток `main` и не операционная система.

По умолчанию каждый созданный нами поток — не-daemon, его также называют пользовательским (user thread). Поток `main` тоже не-daemon.

```java

public class Shutdown {
  public static void main(String[] args) {
    Thread worker = new Thread(() -> {
      for (int i = 1; i <= 3; i++) {
        try {
          Thread.sleep(3000); // Имитируем работу, которая занимает время.
        } catch (InterruptedException e) {
          return;
        }

        System.out.println();
        System.out.println("worker работает, шаг " + i);
      }
      System.out.println("worker закончил");
    }, "worker");

    worker.start(); // Запускаем не-daemon поток. join() здесь намеренно не вызываем.

    System.out.println("main закончил, но JVM ещё работает и ждет завершения не фоновых потоков!");
  }
}
```

Результат:

```text
main закончил, но JVM ещё работает
worker работает, шаг 1
worker работает, шаг 2
worker работает, шаг 3
worker закончил
```

Поток `main` закончился первым, однако процесс продолжил работу, пока `worker` не выполнил все свои шаги. Условия выхода JVM описаны в документации класса `Thread`.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html

> When a Java Virtual Machine starts up, there is usually a single non-daemon thread (which typically calls the method named `main` of some designated class). The Java Virtual Machine continues to execute threads until either of the following occurs:
>
> - The `exit` method of class `Runtime` has been called and the security manager has permitted the exit operation to take place.
> - All threads that are not daemon threads have died, either by returning from the call to the `run` method or by throwing an exception that propagates beyond the `run` method.

RU:

> Когда виртуальная машина Java запускается, обычно существует единственный не-daemon-поток (который, как правило, вызывает метод с именем `main` некоторого указанного класса). Виртуальная машина Java продолжает выполнять потоки, пока не произойдёт одно из следующего:
>
> - был вызван метод `exit` класса `Runtime`, и менеджер безопасности разрешил выполнить операцию выхода;
> - все потоки, не являющиеся daemon-потоками, умерли — либо вернувшись из вызова метода `run`, либо выбросив исключение, которое вышло за пределы метода `run`.

### Daemon-потоки программу не задерживают

Аналогия: торговый центр закрывается, когда ушли все продавцы. Уборщика при этом никто не дожидается — его работу прекращают вместе с закрытием.

Такой «уборщик» в Java — daemon-поток. Нужен он для фоновых подсобных задач: периодической очистки кэша, сбора статистики, служебных таймеров.

Достаточно одной строки, добавленной к предыдущему примеру перед `start()`:

```java

worker.setDaemon(true); // Помечаем поток как daemon. Вызов возможен только до start().
worker.start();
```

Результат того же кода меняется полностью:

```text
main закончил, но JVM ещё работает
```

Ни одного шага `worker` в выводе нет. После завершения `main` в программе не осталось работающих не-daemon-потоков, JVM начала завершение и оборвала daemon-поток в середине его работы.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html

> Marks this thread as either a daemon thread or a user thread. The Java Virtual Machine exits when the only threads running are all daemon threads.

RU:

> Помечает этот поток как daemon-поток или как пользовательский поток. Виртуальная машина Java завершает работу, когда все работающие потоки являются daemon-потоками.

Отсюда два практических вывода. Первый: `setDaemon(true)` вызывают до `start()`, иначе будет `IllegalThreadStateException`. Второй: daemon-поток может быть прерван на любой строке, поэтому запись в файл, отправку в сеть и работу с базой в нём не размещают — данные потеряются при завершении программы.

### Три способа дождаться потока — и их разница

| Что написано в коде | Кто ждёт | Что будет с работой потока |
|---|---|---|
| `worker.join()` | поток, вызвавший `join()` | `main` стоит в этой строке, пока `worker` не завершится |
| `worker.start()` без `join()`, поток не-daemon | JVM перед завершением процесса | `main` заканчивается раньше, но работа потока доводится до конца |
| `worker.setDaemon(true)` | никто | поток обрывается, как только закончились все не-daemon-потоки |

**Правило:** JVM ждёт все не-daemon-потоки и не ждёт daemon-потоки. `join()` нужен тогда, когда результат потока требуется именно в текущем потоке и именно в этом месте кода.

---

Отсюда два практических вывода.

1. **Статус daemon задают до запуска.** 
  - Вызывайте `setDaemon(true)` до `start()`. Когда поток уже запущен, поменять его тип нельзя, и метод выбросит `IllegalThreadStateException`.
2. **Когда JVM завершается, daemon-потоки просто останавливаются.** 
  - Они не доделывают начатую работу, а блоки `finally` в них могут не выполниться. 
  - Поэтому в daemon-потоке не делают то, что нужно довести до конца: запись в файл, отправку в сеть, работу с базой. Иначе при выходе из программы часть данных потеряется.

---

## Типичные ошибки

| Ошибка | Что получится |
|---|---|
| `worker.run()` вместо `worker.start()` | всё выполнится в текущем потоке, параллельности нет |
| Чтение результата сразу после `start()` | увидите пустое значение: поток ещё не доработал |
| Повторный `start()` на том же объекте | `IllegalThreadStateException` |
| `interrupt()` у потока в цикле без пауз и без проверки флага | просьба висит на флаге, цикл крутится дальше |
| `setDaemon(true)` после `start()` | `IllegalThreadStateException` |
| Важная работа в daemon-потоке | JVM оборвёт поток при завершении, данные потеряются |

---

## Практика

Практические задачи по теме — в отдельном файле [`01-thread-basics-tasks.md`](01-thread-basics-tasks.md). Каждая задача — рабочая ситуация, которую нужно решить с нуля средствами этой темы.

---

*Тема 01 курса [`00-plan.md`](00-plan.md).*
