# Project Reactor: `cache()`, Hot Publisher, heap и stack

## Оглавление

- [Вопрос, на который отвечает документ](#вопрос-на-который-отвечает-документ)
- [Главная мысль](#главная-мысль)
- [Три разных понятия](#три-разных-понятия)
- [Где живут данные](#где-живут-данные)
- [Как поздний подписчик получает старые данные](#как-поздний-подписчик-получает-старые-данные)
- [Что именно делает `cache()`](#что-именно-делает-cache)
- [`Mono.cache()`](#monocache)
- [`Flux.cache()` и риск `OutOfMemoryError`](#fluxcache-и-риск-outofmemoryerror)
- [Как ограничивать память](#как-ограничивать-память)
- [Практическое правило](#практическое-правило)

## Вопрос, на который отвечает документ

Есть источник метрик. 
 - Он производит новые объекты `Metric`. 
 - Нужно, чтобы подписчик, пришедший через **час**, увидел и старые метрики, и новые. 
 - Для этого используется `cache()`.

Нужно понять:

 - где Reactor хранит накопленные данные;
 - есть ли они в **stack**;
 - как новый подписчик находит эти данные, если старый уже ушёл;
 - почему бесконечный `Flux.cache()` может привести к **переполнению** _heap_;
 - как ограничить потребление памяти.

## Главная мысль

- `cache()` удерживает ранее полученные элементы в **объекте** оператора **cache**. 
  - Этот объект и сохранённые элементы живут в **Java heap**.

Когда **поздний подписчик** вызывает `subscribe()`, в **stack** его текущего метода появляется временная ссылка на cached `Flux`. 
 - Эта ссылка ведёт к уже существующему объекту **cache** в **heap**. 
 - Из него Reactor и воспроизводит ранее сохранённые элементы.

Старый **stack** первого подписчика для этого не нужен и не используется.

## Три разных понятия

### 1. Объект данных

Например:

```java
Metric metric = new Metric("cpu", 0.72);
```

`Metric` — объект данных. 
 - Он может ссылаться на другие объекты:
   - строки, 
   - `Map`, 
   - списки тегов, 
   - DTO 
   - и так далее. 
   
 - Вместе они образуют **граф объектов**.

### 2. Сигнал `onNext`

Когда **publisher** передаёт объект подписчику, это выглядит так:

```java
subscriber.onNext(metric);
```

- `metric` — объект данных;
- `onNext(metric)` — сигнал, то есть событие передачи этого объекта по Reactive Streams-протоколу.

Поэтому фразу из Javadoc Reactor «cache stores `onNext` signals» для практического понимания можно читать так:

> cache сохраняет ранее переданные элементы `T` и затем повторно отправляет их позднему подписчику.

### 3. `Flux` с оператором `cache()`

```java
Flux<Metric> cachedMetrics = source.cache();
```

`cachedMetrics` — объект publisher-а, построенный Reactor. 

- Внутри него находится состояние **cache**: 
  - ссылки на сохранённые элементы и служебное состояние, необходимое для **replay**.

## Где живут данные

### Heap

**Heap** — общая для потоков, область данных JVM, из которой выделяется память под экземпляры классов и массивы.

- Источник: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html

EN:

> “The Java Virtual Machine has a heap that is shared among all Java Virtual Machine threads. The heap is the run-time data area from which memory for all class instances and arrays is allocated.”

RU:

> «В JVM есть heap, общий для всех потоков JVM. Heap — это область данных времени выполнения, из которой выделяется память для всех экземпляров классов и массивов».

Следовательно, объект `Metric`, объект `Flux` с `cache()` и внутренний буфер **cache** находятся в **heap**.

### Stack

У каждого потока JVM есть собственный **stack**. 
 - Он хранит **frames** вызванных методов, локальные переменные и промежуточные результаты вычислений.

- Источник: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html

EN:

> “Each Java Virtual Machine thread has a private Java Virtual Machine stack… A Java Virtual Machine stack stores frames.”

RU:

> «У каждого потока JVM есть собственный stack JVM… Stack JVM хранит frames».

**Frame** создаётся при вызове метода и **уничтожается** _при завершении_ этого вызова.

- Источник: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html

EN:

> “A new frame is created each time a method is invoked. A frame is destroyed when its method invocation completes.”

RU:

> «Новый frame создаётся при каждом вызове метода. Frame уничтожается, когда вызов метода завершается».

В **stack** действительно могут временно находиться **ссылки** на объекты из heap. 

- Но cache не может храниться только в **stack**: 
  - stack конкретного вызова исчезнет после завершения метода, а **cache** должен быть доступен будущему подписчику через минуту или час.

**Важно**: 
- **heap** и **stack** — не две разные «оперативные памяти»;
   - это разные логические области памяти JVM;
   - память для процесса JVM выделяется операционной системой, обычно из RAM.

## Как поздний подписчик получает старые данные

Пусть **cached publisher** хранится в **singleton Spring bean**:

```java
@Service
class MetricsService {

    private final Flux<Metric> metrics = source.cache();

    Flux<Metric> metrics() {
        return metrics;
    }
}
```

После того как **source** передал несколько метрик, **логическая цепочка** ссылок выглядит так:

```text
MetricsService singleton                 heap
        ↓
Flux с оператором cache                  heap
        ↓
внутреннее состояние cache               heap
        ↓
Metric #1, Metric #2, Metric #3 ...      heap
```

Через **час** новый код вызывает:

```java
metricsService.metrics().subscribe(...);
```

В **frame** нового вызова есть временная ссылка на `Flux`. 
 - Эта ссылка ведёт к `Flux` в **heap**, затем к состоянию **cache** и сохранённым `Metric`.

```text
stack нового вызова
        ↓ временная ссылка
cached Flux в heap
        ↓
сохранённые Metric в heap
```

Первый подписчик мог давно отменить подписку, а его method frame давно уничтожен. 

- Это не влияет на cache, пока **cached** `Flux` остаётся достижимым через bean, поле, registry или другую сильную ссылку.

## Что именно делает `cache()`

Оператор `cache()` превращает `Flux` или `Mono` в **publisher**, который запоминает переданные сигналы и воспроизводит их последующим подписчикам.

Для `Flux` официальный Javadoc описывает оператор так:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html

EN:

> “Turn this `Flux` into a hot source and cache last emitted signals for further `Subscriber`.”

RU:

> «Превращает этот `Flux` в горячий источник и кэширует последние переданные сигналы для последующих подписчиков».

Для `Mono` формулировка аналогична:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html

EN:

> “Turn this `Mono` into a hot source and cache last emitted signals for further `Subscriber`.”

RU:

> «Превращает этот `Mono` в горячий источник и кэширует последние переданные сигналы для последующих подписчиков».

Практический смысл слова «**горячий**» здесь: 
 - после первой подписки результат работы **source** становится общим для последующих подписчиков, а не вычисляется заново для каждого из них.

## `Mono.cache()`

`Mono<T>` передаёт не более одного элемента `T`, затем завершается успешно, завершается пустым или завершается ошибкой.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html

EN:

> “A Reactive Streams `Publisher` with basic rx operators that emits at most one item via the `onNext` signal then terminates with an `onComplete` signal … or only emits a single `onError` signal.”

RU:

> «Publisher Reactive Streams с базовыми rx-операторами, который передаёт не более одного элемента через сигнал `onNext`, затем завершается сигналом `onComplete` либо передаёт единственный сигнал `onError`».


```java
Mono<TariffTable> cachedTariffs = tariffClient.loadTariffs()
    .cache();
```

`cache()` **запоминает** результат первой подписки на **source**.

- Если source завершился успешно, кэш хранит ссылку на полученный `TariffTable`. 
- Поэтому последующие подписчики получают тот же экземпляр `TariffTable`: 
  - повторной подписки на source не будет.

Пока поле `cachedTariffs` **доступно** через **singleton bean**, кэш **не может быть очищен** сборщиком мусора. 
-  Следовательно, `TariffTable` и все объекты, на которые он ссылается, также остаются в памяти.

У `cache()` без параметров нет TTL: 
  - результат хранится, пока существует bean, содержащий это поле.

```java
Mono<TariffTable> cachedTariffs = tariffClient.loadTariffs()
    .cache(Duration.ofMinutes(30));
```

В этом варианте результат хранится **30 минут** с момента его получения.

- В течение **30 минут** каждый новый подписчик получает сохранённый `TariffTable`.
- После истечения **30 минут** кэш считается устаревшим.
- Первый подписчик после этого создаёт новую подписку на source.
- Результат этой новой подписки сохраняется в кэше ещё на 30 минут.

`cache()` **кэширует** не только успешный результат: 
 - ошибка или пустое завершение **source** тоже являются **terminal signal** и могут быть повторно переданы поздним подписчикам.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html

EN:

> “Turn this `Mono` into a hot source and cache last emitted signals for further `Subscriber`, with an expiry timeout.”

RU:

> «Преобразует этот `Mono` в горячий источник и кэширует последние переданные сигналы для последующих подписчиков с тайм-аутом истечения.»



## `Flux.cache()` и риск `OutOfMemoryError`

`Flux<T>` может передать от нуля до множества элементов. 
  - Поэтому `Flux.cache()` без параметров опасен для бесконечного или очень большого **source**.

```java
Flux<Metric> cachedMetrics = infiniteMetricsSource.cache();
```

Официальный Javadoc прямо предупреждает:

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html

EN:

> “Will retain an unbounded volume of onNext signals.”

RU:

> «Будет удерживать неограниченный объём сигналов `onNext`».

В прикладных терминах: 
 - **cache** будет удерживать неограниченное число ранее переданных объектов `Metric`.

Если **source** _бесконечный_ и создаёт новые объекты, логическая картина будет такой:

```text
Metric #1  → cache удерживает ссылку
Metric #2  → cache удерживает ссылку
Metric #3  → cache удерживает ссылку
...
Metric #N  → cache удерживает ссылку
```

Память **heap** будет расти. 
 - **Garbage collector** не сможет освободить эти объекты, потому что они достижимы по цепочке:

```text
singleton bean → cached Flux → cache state → Metric
```

Когда JVM не сможет выделить больше памяти в heap, она выбросит `OutOfMemoryError`.

- Источник: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html

EN:

> “If a computation requires more heap than can be made available by the automatic storage management system, the Java Virtual Machine throws an `OutOfMemoryError`.”

RU:

> «Если вычислению требуется больше heap, чем может предоставить система автоматического управления памятью, JVM выбрасывает `OutOfMemoryError`».

## Как ограничивать память

### Ограничить число сохранённых элементов

```java
Flux<Metric> cachedMetrics = source.cache(1_000);
```

Это означает: 
 - **cache** удерживает историю максимум из 1 000 элементов. 
 **- Поздний подписчик сначала получает сохранённую историю в пределах этого лимита, затем получает новые элементы от работающего source.**

### Ограничить время хранения

```java
Flux<Metric> cachedMetrics = source.cache(Duration.ofHours(1));
```

Это означает: 
 - элементы могут быть **replayed** только в течение заданного TTL.

Но **TTL** сам по себе не задаёт верхнюю границу количества элементов. 
- Если поток очень интенсивный, за один час может накопиться очень много объектов.

### Ограничить и количество, и время

```java
Flux<Metric> cachedMetrics = source.cache(
    10_000,
    Duration.ofHours(1)
);
```

Это обычно наиболее понятный и безопасный вариант для истории метрик:

 - не более 10 000 элементов;
 - не старше одного часа.

Официальный API `Flux` предоставляет overload `cache(int history, Duration ttl)`.

- Источник: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html

EN:

> “cache(int history, Duration ttl)”

RU:

> «Метод `cache`, принимающий максимальный размер истории и время жизни элементов».

## Практическое правило

| Сценарий | Подход |
|---|---|
| Нужно один раз загрузить небольшой справочник и повторно отдавать его | `Mono.cache()` или `Mono.cache(ttl)` |
| Нужны последние N событий | `Flux.cache(N)` |
| Нужна история только за период | `Flux.cache(ttl)`, но нужно оценить скорость потока и память |
| Нужны и лимит памяти, и временное окно | `Flux.cache(N, ttl)` |
| История большая, должна переживать рестарт JVM или быть общей между pod-ами | Внешнее хранилище: БД, Redis, Kafka, time-series DB и т. п. |

**Главное правило:** 
 - не используйте `Flux.cache()` без параметров **для бесконечного source**, если вы не рассчитали объём удерживаемой памяти и не уверены, что поток когда-нибудь завершится.
