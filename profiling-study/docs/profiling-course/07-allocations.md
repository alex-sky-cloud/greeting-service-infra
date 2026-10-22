# Тема 07. Аллокации и давление на GC

> **Статус:** теория · **Дальше:** интервью в чате, затем [практика](07-allocations-tasks.md)

---

## Оглавление

- [1. Скорость выделения памяти](#1-скорость-выделения-памяти)
- [2. TLAB: почему new обычно дешёвый](#2-tlab-почему-new-обычно-дешёвый)
- [3. Сэмплы аллокаций в JFR](#3-сэмплы-аллокаций-в-jfr)
- [4. Первое место из кода приложения](#4-первое-место-из-кода-приложения)
- [5. Откуда берётся лишний мусор](#5-откуда-берётся-лишний-мусор)
- [6. Как измерить байты на вызов](#6-как-измерить-байты-на-вызов)
- [Вопросы для интервью](#вопросы-для-интервью)

---

## 1. Скорость выделения памяти

**Аналогия:**

Мусорная машина приезжает тем чаще, чем больше мусора выносят жильцы. Ускорить машину можно, но проще меньше мусорить.

**Как это устроено в коде:**

- **Сколько мусора** — скорость выделения памяти (allocation rate): байт в секунду на новые объекты.
- **Как часто машина** — молодая сборка начинается, когда заполнен Eden. Больше мусора — чаще сборки.
- **Чем платим** — каждая сборка — пауза и CPU на работу GC. Утечки нет, а сервис тормозит.
- **Как увидеть** — GC-лог: сколько освобождено за сборку и как часто сборки идут.

Запуск на JDK 27, лаборатория `catalog`, GC-лог:

```text
[5.631s][info][gc] GC(39) Pause Young (Normal) (G1 Evacuation Pause) 376M->10M(618M) 1.612ms
[5.833s][info][gc] GC(40) Pause Young (Normal) (G1 Evacuation Pause) 378M->8M(618M) 0.642ms
[6.087s][info][gc] GC(41) Pause Young (Normal) (G1 Evacuation Pause) 376M->10M(618M) 1.566ms
[6.293s][info][gc] GC(42) Pause Young (Normal) (G1 Evacuation Pause) 377M->8M(618M) 0.948ms
```

**Разбор:**

- Каждые 0,2 секунды освобождается около 368 МБ — это почти 1,8 ГБ мусора в секунду.
- После сборки остаётся 8–10 МБ: живой набор маленький, утечки нет. Вся проблема — в скорости выделения.
- Лаборатория печатает ~1 070 цен в секунду. 1,8 ГБ / 1 070 ≈ 1,7 МБ на одну цену.

- Источник: https://docs.oracle.com/en/java/javase/27/troubleshoot/troubleshoot-performance-issues-using-jfr.html

> To reduce the need of GCs, decrease the allocation rate.

RU:

> Чтобы реже требовались сборки мусора, снижайте скорость выделения памяти.

**Правило:** частые молодые сборки при маленьком остатке — не утечка, а давление на GC; лечат скоростью выделения, а не размером кучи.

---

## 2. TLAB: почему new обычно дешёвый

**Аналогия:**

У каждого кассира своя пачка чистых чеков. Взять чек — секунда; за новой пачкой идут на склад редко.

**Как это устроено в коде:**

- **Пачка чеков** — TLAB (Thread Local Allocation Buffer): кусок Eden, выданный одному потоку.
- **Взять чек** — `new` маленького объекта: сдвиг указателя внутри TLAB, без блокировок.
- **На склад** — TLAB кончился: поток берёт новый. Большой объект кладётся сразу мимо TLAB.
- **Где дорого** — не сам `new`, а сборки, которые вызывает поток мусора.

- Источник: https://docs.oracle.com/en/java/javase/27/troubleshoot/troubleshoot-performance-issues-using-jfr.html

> Small objects are allocated in a Thread Local Area Buffer (TLAB). TLAB is a small memory area where new objects are allocated. Once a TLAB is full, the thread gets a new one. Larger objects are allocated outside a TLAB. Often, the majority of allocations happen inside a TLAB.

RU:

> Маленькие объекты выделяются в TLAB (Thread Local Area Buffer). TLAB — небольшая область памяти, в которой создаются новые объекты. Когда TLAB заполнен, поток получает новый. Крупные объекты выделяются вне TLAB. Обычно большинство выделений происходит внутри TLAB.

**Правило:** один `new` не страшен; страшны миллионы ненужных `new` в горячем пути.

---

## 3. Сэмплы аллокаций в JFR

**Аналогия:**

Не взвешивать каждый пакет мусора, а иногда брать пакет и записывать: чей, сколько весит и сколько таких было с прошлого раза.

**Как это устроено в коде:**

- **Пакет** — событие `jdk.ObjectAllocationSample`: класс объекта, поток, стек.
- **Вес** — поле `weight`: сколько байт представляет этот сэмпл. Сумма весов по месту — давление на память от этого места.
- **Частота** — не больше 150 сэмплов в секунду в `default`, 300 — в `profile`. Дёшево, можно на продакшене.
- **Сводки** — `jfr view allocation-by-site` (по методу на вершине стека) и `jfr view allocation-by-class` (по классу объекта).

Запуск на JDK 27, лаборатория `catalog`, запись 15 секунд:

```text
                                  Allocation by Site

Method                                                            Allocation Pressure
----------------------------------------------------------------- -------------------
java.util.HashMap.newNode(int, Object, Object, HashMap$Node)                   73,31%
java.util.HashMap.resize()                                                     26,66%
java.util.Formatter.<init>()                                                    0,01%
```

```text
                              Allocation by Class

Object Type                                                 Allocation Pressure
----------------------------------------------------------- -------------------
java.util.HashMap$Node                                                   73,31%
java.util.HashMap$Node[]                                                 26,66%
java.lang.StringBuilder                                                   0,01%
```

Описание поля из `jfr metadata --events jdk.ObjectAllocationSample`:

```text
  @Description("The relative weight of the sample. Aggregating the weights for a large number of samples, for a particular class, thread or stack trace, gives a statistically accurate representation of the allocation pressure")
  long weight;
```

**Разбор:**

- 99,97 % давления — узлы и таблицы `HashMap`. Кто-то строит хеш-таблицы в горячем пути.
- Обе сводки называют JDK. Кто именно строит — ни одна из них не говорит.

**Правило:** аллокации ищут по сумме `weight`, а не по числу сэмплов; сводку по классу и по месту читают вместе.

---

## 4. Первое место из кода приложения

**Аналогия:**

Мусор в контейнере — от упаковки одной фабрики, но она делает то, что заказали. Ищут заказчика.

**Как это устроено в коде:**

- **Фабрика упаковки** — `HashMap.newNode`, `Arrays.copyOf`, `StringBuilder` — вершина стека аллокации.
- **Заказчик** — первый кадр `com.example…` ниже по стеку: код, который решил создать эти объекты.
- **Как найти** — `jfr print --events jdk.ObjectAllocationSample --stack-depth 10`, дерево в JMC или сумма весов по первому кадру приложения.

Запуск на JDK 27, лаборатория `catalog`, `jfr print --events jdk.ObjectAllocationSample --stack-depth 8`, один из сэмплов:

```text
    stackTrace = [
      java.util.HashMap.resize() line: 736
      java.util.HashMap.putVal(int, Object, Object, boolean, boolean) line: 661
      java.util.HashMap.putHashMapEntries(HashMap, boolean) line: 505
      java.util.HashMap.putMapEntries(Map, boolean) line: 541
      java.util.HashMap.<init>(Map) line: 493
      com.example.profcourse.t07.LegacyCatalog.priceFor(String, int) line: 21
      com.example.profcourse.lab.LabApp.catalog() line: 187
```

**Разбор:**

- Под пятью кадрами `HashMap` — `LegacyCatalog.priceFor`, строка 21: конструктор копии `new HashMap<>(…)`.
- Каждый расчёт цены копирует прайс из 50 000 позиций «чтобы прайс не поменялся посреди расчёта». Прайс и так неизменяемый.
- Есть и сэмплы с первым кадром `PriceList.<init>` — прайс строится один раз при старте. В сводке по первому кадру приложения это отдельная строка.

### То же в JMC

Запись лаборатории `catalog`, 20 секунд, шаблон `profile`, JMC 8.1:

```bash

java -XX:StartFlightRecording:settings=profile,duration=20s,filename=build/lab/gui/catalog.jfr \
  -cp build/classes/java/main com.example.profcourse.lab.LabApp catalog
```

![JMC: Memory, аллокации по классам](images/07-jmc-memory-allocations.png)

1. **File → Open File…** → `catalog.jfr`, в дереве — **Java Application → Memory**.
2. Таблица **Class**, сортировка щелчком по заголовку **Alloc Total**: `HashMap$Node` — 15 GiB, `HashMap$Node[]` — 4,28 GiB за 20 секунд; дальше — мегабайты.
3. Выделите строку `HashMap$Node` — **Flame View** внизу покажет 4 323 сэмпла аллокаций: `LabApp.catalog()` → `LegacyCatalog.priceFor(String, int)` → `HashMap.<init>(Map)` → `putMapEntries` → … → `newNode`.

**Разбор:**

- Тот же ответ, что дал `jfr print` выше, но без `grep`: первый кадр приложения над кадрами `HashMap` виден сразу — `LegacyCatalog.priceFor`.
- 19 ГБ за 20 секунд — около 1 ГБ в секунду мусора от одного метода.

Что это стоит сборщику — страница **JVM Internals → Garbage Collections**:

![JMC: Garbage Collections](images/07-jmc-gc.png)

1. В дереве — **JVM Internals → Garbage Collections**.
2. Колонка **Longest Pause**: молодые сборки `G1 Evacuation Pause`, `G1New` — по 1,7–2,9 мс; первая — 22,5 мс.
3. График **Heap Post GC** — ровный: после каждой сборки куча возвращается к прежнему уровню. Утечки нет, есть только поток мусора.
4. **Results**: **GC Pauses** — 2, **G1/CMS Full Collection** — 0, все правила зелёные.

**Разбор:**

- Паузы короткие, правила зелёные — JMC не считает это проблемой. Цена мусора — в CPU сборщика и частоте сборок, а не в длине пауз.
- Ровный **Heap Post GC** отличает мусор (тема 07) от утечки (тема 03): при утечке эта линия растёт.

**Правило:** чинят первый кадр приложения в стеке аллокации, а не класс JDK наверху.

---

## 5. Откуда берётся лишний мусор

**Аналогия:**

Ксерокопия всей папки, чтобы прочитать одну страницу.

**Как это устроено в коде:**

- **Защитные копии** — `new HashMap<>(map)`, `new ArrayList<>(list)` на каждый вызов «на всякий случай».
- **Форматирование и регулярки в цикле** — `String.format`, `String.replaceAll`, `Pattern.compile` на каждую строку.
- **Упаковка** — `Map<String, Long>`, `List<Integer>` и стримы по числам: объект на каждое значение.
- **Склейка ради лога** — строка собирается, даже если уровень лога выключен.
- **Сериализация в горячем пути** — `toString` и JSON больших объектов для каждого запроса.

**Ловушка:**

«JVM сама уберёт» — уберёт, но ценой сборок. Мусор, который не создали, убирать не нужно.

**Правило:** в горячем пути не копируют то, что только читают, и не создают заново то, что можно создать один раз.

---

## 6. Как измерить байты на вызов

**Аналогия:**

Сколько бумаги уходит на один договор: посчитать пачку до и после сотни договоров и разделить.

**Как это устроено в коде:**

- **Счётчик пачки** — `com.sun.management.ThreadMXBean.getCurrentThreadAllocatedBytes()`: сколько байт выделил текущий поток с его старта.
- **До и после** — счётчик до цикла из N вызовов и после, разница / N.
- **Прогрев** — сначала тысячи вызовов: JIT может убрать временные объекты, которые не выходят за метод.
- **Бюджет** — число байт на вызов, которое тест не даёт превысить: регрессия ловится в CI, а не на продакшене.

Запуск на JDK 27, одна цена со скидкой после прогрева:

| Реализация | Байт на вызов |
|---|---|
| `LegacyCatalog.priceFor` | 1 724 385 |
| без копии прайса | 0 |

- Источник: https://docs.oracle.com/en/java/javase/27/docs/api/jdk.management/com/sun/management/ThreadMXBean.html

> Returns an approximation of the total amount of memory, in bytes, allocated in heap memory for the current thread. The returned value is an approximation because some Java virtual machine implementations may use object allocation mechanisms that result in a delay between the time an object is allocated and the time its size is recorded.

RU:

> Возвращает приблизительный общий объём памяти в байтах, выделенной в куче для текущего потока. Значение приблизительное, потому что некоторые реализации JVM выделяют объекты так, что размер записывается с задержкой после выделения.

**Разбор:**

- 1,7 МБ на вызов совпадает с оценкой по GC-логу в разделе 1. Два независимых способа дали одно число.
- 0 байт — JIT увидел, что временных объектов нет или они не выходят за метод.

**Правило:** для горячих методов фиксируют бюджет байт на вызов в тесте; мерят на одном потоке после прогрева.

---

## Вопросы для интервью

1. Что такое скорость выделения памяти и как она связана с частотой сборок?
2. Как по GC-логу оценить, сколько мусора в секунду создаёт сервис?
3. Что такое TLAB? Почему `new` дешёвый, а мусор — нет?
4. Что означает поле `weight` в сэмпле аллокации? Почему суммируют вес, а не число сэмплов?
5. Профиль аллокаций показывает `HashMap.newNode`. Что делаете дальше?
6. Назовите три частых источника лишних аллокаций в бизнес-коде.
7. Как измерить, сколько байт выделяет один вызов метода? Что может исказить замер?
8. Как не дать регрессии по аллокациям снова попасть в продакшен?

---

**Практика:** [07-allocations-tasks.md](07-allocations-tasks.md)
