# Тема 01. Поток и общая память

> **Статус:** теория · **Следующий шаг:** прочитать файл → написать в чат *«дай интервью по теме 1»*

---

## Оглавление

- [Процесс и поток](#процесс-и-поток)
- [start и run](#start-и-run)
- [Состояния потока](#состояния-потока)
- [join: дождаться результата](#join-дождаться-результата)
- [Прерывание потока](#прерывание-потока)
- [Daemon-поток](#daemon-поток)
- [Типичные ошибки](#типичные-ошибки)
- [Что попробовать самостоятельно](#что-попробовать-самостоятельно)

---

## Процесс и поток

Представьте кухню ресторана. **Процесс** — это отдельная кухня со своими продуктами и своими ножами: что там происходит, соседней кухне не видно. **Поток** — это повар внутри одной кухни. Поваров может быть несколько, но холодильник у них один, общий.

Отсюда всё остальное в курсе. Два повара у одного холодильника работают быстро, но могут одновременно взять последнее яйцо.

```java
public class Kitchen {
    static String fridge = "пусто";   // общая полка на всех поваров

    public static void main(String[] args) throws InterruptedException {
        Thread cook = new Thread(() -> fridge = "яйцо");
        cook.start();
        cook.join();
        System.out.println(fridge);   // яйцо
    }
}
```

Технически: поток живёт внутри процесса и пользуется памятью этого процесса. Поэтому объект, созданный в одном потоке, виден другому потоку — не копия, а тот же самый объект. Отдельные процессы так обмениваться не могут, им нужны сокеты или файлы.

- Источник: https://docs.oracle.com/javase/tutorial/essential/concurrency/procthread.html

> Threads exist within a process — every process has at least one. Threads share the process's resources, including memory and open files. This makes for efficient, but potentially problematic, communication.

RU:

> Потоки существуют внутри процесса — у каждого процесса есть по меньшей мере один. Потоки разделяют ресурсы процесса, включая память и открытые файлы. Это делает обмен данными эффективным, но потенциально проблемным.

**Правило:** потоки делят память процесса, поэтому любое общее изменяемое поле — это место возможной ошибки.

---

## start и run

Аналогия: `start()` — позвать коллегу и поручить ему работу, вы при этом свободны. `run()` — открыть его инструкцию и выполнить всё самому. Работа сделана в обоих случаях, но во втором никакого коллеги не появилось.

```java
public class StartVsRun {
    public static void main(String[] args) {
        Runnable task = () -> System.out.println("работаю в " + Thread.currentThread().getName());

        new Thread(task, "worker").start();   // работаю в worker
        new Thread(task, "worker").run();     // работаю в main
    }
}
```

Технически новый поток операционной системы создаётся только вызовом `start()`. Метод `run()` — обычный метод обычного объекта: вызвали — выполнился в вызывающем потоке.

Порядок двух строк в выводе не задан: `main` часто успевает напечатать свою строку первым. Смотреть нужно не на порядок, а на имя потока в каждой строке.

- Источник: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html

> Schedules this thread to begin execution. The thread will execute independently of the current thread.

RU:

> Планирует начало выполнения этого потока. Поток будет выполняться независимо от текущего потока.

Про `run()` там же сказано прямо:

> This method is not intended to be invoked directly. If this thread is a platform thread created with a `Runnable` task then invoking this method will invoke the task's `run` method.

RU:

> Этот метод не предназначен для прямого вызова. Если данный поток — платформенный поток, созданный с задачей `Runnable`, то вызов этого метода вызовет метод `run` этой задачи.

Второй раз тот же объект `Thread` запустить нельзя:

> A thread can be started at most once. In particular, a thread can not be restarted after it has terminated.

RU:

> Поток может быть запущен не более одного раза. В частности, поток нельзя перезапустить после того, как он завершился.

**Правило:** поток создаёт только `start()`; `start()` на одном объекте — один раз.

---

## Состояния потока

Аналогия — сотрудник в течение дня: ещё не пришёл, работает, стоит под дверью занятого кабинета, ждёт звонка неизвестно сколько, ждёт до 10:00, ушёл домой.

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
> `RUNNABLE` Поток, выполняющийся в виртуальной машине Java, находится в этом состоянии.
> `BLOCKED` Поток, заблокированный в ожидании захвата монитора, находится в этом состоянии.
> `WAITING` Поток, неограниченно долго ожидающий, пока другой поток выполнит определённое действие, находится в этом состоянии.
> `TIMED_WAITING` Поток, ожидающий выполнения действия другим потоком не дольше указанного времени ожидания, находится в этом состоянии.
> `TERMINATED` Поток, который завершился, находится в этом состоянии.

Важная оговорка из того же источника: это состояния виртуальной машины, а не операционной системы.

> A thread can be in only one state at a given point in time. These states are virtual machine states which do not reflect any operating system thread states.

RU:

> В каждый момент времени поток может находиться только в одном состоянии. Это состояния виртуальной машины, которые не отражают какие-либо состояния потоков операционной системы.

**Правило:** `BLOCKED` — ждёт замок, `WAITING` — ждёт действия другого потока. На собеседовании их часто путают.

---

## join: дождаться результата

Аналогия: вы попросили коллегу посчитать выручку и не уходите домой, пока он не положит отчёт на стол. Без этого ожидания вы возьмёте со стола пустой лист.

```java
public class Report {
    static int revenue;

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> revenue = 1000);
        worker.start();
        System.out.println(revenue);   // 0 — отчёта на столе ещё нет

        worker.join();
        System.out.println(revenue);   // 1000
    }
}
```

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

Аналогия: вы стучите коллеге и говорите «заканчивай». Вы не выдёргиваете его из кабинета силой — он сам решает, когда прервать дело. Если коллега в этот момент спит, он проснётся и увидит просьбу.

```java
public class Stopping {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                System.out.println("получил просьбу закончить");
            }
        });

        worker.start();
        Thread.sleep(300);
        worker.interrupt();
        worker.join();
    }
}
```

Технически `interrupt()` не останавливает поток, а поднимает у него внутренний флаг. Если поток в этот момент внутри `sleep`, `wait` или `join`, метод немедленно выбрасывает `InterruptedException`. Если поток занят вычислениями, он обязан сам периодически проверять флаг.

- Источник: https://docs.oracle.com/javase/tutorial/essential/concurrency/interrupt.html

> An interrupt is an indication to a thread that it should stop what it is doing and do something else. It's up to the programmer to decide exactly how a thread responds to an interrupt, but it is very common for the thread to terminate.

RU:

> Прерывание — это указание потоку, что он должен прекратить то, что делает, и заняться чем-то другим. Программист сам решает, как именно поток отвечает на прерывание, но очень часто поток завершается.

Два метода проверки различаются побочным эффектом — это любимый вопрос:

> The interrupt mechanism is implemented using an internal flag known as the interrupt status. Invoking `Thread.interrupt` sets this flag. When a thread checks for an interrupt by invoking the static method `Thread.interrupted`, interrupt status is cleared. The non-static `isInterrupted` method, which is used by one thread to query the interrupt status of another, does not change the interrupt status flag.

RU:

> Механизм прерывания реализован с помощью внутреннего флага, известного как статус прерывания. Вызов `Thread.interrupt` устанавливает этот флаг. Когда поток проверяет наличие прерывания, вызывая статический метод `Thread.interrupted`, статус прерывания сбрасывается. Нестатический метод `isInterrupted`, который используется одним потоком для запроса статуса прерывания другого, не изменяет флаг статуса прерывания.

**Правило:** `interrupt()` — просьба, а не убийство потока. `Thread.interrupted()` флаг снимает, `isInterrupted()` — нет.

---

## Daemon-поток

Аналогия: уборщик в торговом центре. Центр закрывается, когда ушли все продавцы; уборщика при этом никто не дожидается.

```java
public class Background {
    public static void main(String[] args) {
        Thread cleaner = new Thread(() -> {
            while (true) {
                System.out.println("подметаю");
            }
        });
        cleaner.setDaemon(true);   // без этой строки программа не завершится никогда
        cleaner.start();
        System.out.println("main закончил");
    }
}
```

- Источник: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html

> Marks this thread as either a daemon or non-daemon thread. The shutdown sequence begins when all started non-daemon threads have terminated.

RU:

> Помечает этот поток как daemon-поток или как не-daemon-поток. Последовательность завершения работы начинается, когда все запущенные не-daemon-потоки завершились.

`setDaemon(true)` вызывают до `start()`. Daemon-поток может быть прерван на середине работы, поэтому важные записи в файл или в базу в нём не делают.

**Правило:** JVM ждёт обычные потоки и не ждёт daemon-потоки.

---

## Типичные ошибки

| Ошибка | Что получится |
|---|---|
| `worker.run()` вместо `worker.start()` | всё выполнится в текущем потоке, параллельности нет |
| Чтение результата сразу после `start()` | увидите пустое значение: поток ещё не доработал |
| Повторный `start()` на том же объекте | `IllegalThreadStateException` |
| `interrupt()` в расчёте без проверки флага | поток продолжит работу как будто ничего не было |
| `setDaemon(true)` после `start()` | `IllegalThreadStateException` |

---

## Что попробовать самостоятельно

1. В примере `Report` уберите `join()` и запустите несколько раз — сравните вывод.
2. Напечатайте `worker.getState()` до `start()`, сразу после `start()` и после `join()`.
3. Вызовите `start()` на одном объекте дважды и прочитайте текст исключения.
4. В примере `Stopping` замените `Thread.sleep(100)` на пустой цикл `while (true) {}`: `interrupt()` сам по себе работу не прервёт, программа зависнет на `join()` — её придётся снять вручную (`Ctrl+C`). Затем добавьте в цикл проверку `Thread.interrupted()` и запустите снова.

---

*Тема 01 курса [`00-plan.md`](00-plan.md).*
