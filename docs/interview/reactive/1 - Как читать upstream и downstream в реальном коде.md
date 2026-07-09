## Как читать upstream и downstream в реальном коде

Правило простое:

- **upstream — это всё, что выше по цепочке операторов** (ближе к источнику данных, к началу `Flux`/`Mono`; в коде это находится в верхней части метода), а
- **downstream — это всё, что ниже** (ближе к `subscribe()`, к финальному потребителю; в коде это находится в нижней части метода).

Источник: https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html

EN:

> "In Reactor, when you chain operators, you can wrap as many Flux and Mono implementations inside one another as you need. Once you subscribe, a chain of Subscriber objects is created, backward (up the chain) to the first publisher."

RU:

> "В Reactor, когда вы связываете операторы в цепочку, вы можете оборачивать столько реализаций Flux и Mono друг в друга, сколько нужно. После подписки создаётся цепочка объектов Subscriber, идущая назад (вверх по цепочке) к первому издателю."

В реальном бизнес-коде это удобно читать так:
- **контроллер**, куда пришёл HTTP-запрос, находится **в самом НИЗУ цепочки** (это **точка подписки**/потребления результата), а вызов в базу данных или во внешний сервис — это источник, то есть самый ВЕРХ цепочки.
- Запрос "идёт вниз" от контроллера к источнику данных только в момент подписки, а данные потом "поднимаются вверх" **от источника** к контроллеру.

```java
@RestController
class OrderController {

    private final OrderService orderService;

    @GetMapping("/orders/{id}")
    Mono<OrderResponse> getOrder(@PathVariable String id) {
        return orderService.findOrder(id)         // DOWNSTREAM: контроллер потребляет результат
            .map(OrderResponse::from)              // трансформация перед отдачей клиенту
            .subscribeOn(Schedulers.boundedElastic()); // здесь решаем, на каком потоке стартует ВСЯ цепочка ниже(то есть map, затем получение данных (источник))
    }
}

@Service
class OrderService {

    private final OrderRepository orderRepository; // блокирующий JDBC-репозиторий

    Mono<Order> findOrder(String id) {
        return Mono.fromCallable(() -> orderRepository.findById(id)) // ИСТОЧНИК (самый верх upstream) — блокирующий вызов в БД
            .subscribeOn(Schedulers.boundedElastic());                // снимаем блокировку с event-loop потока
    }
}
```

**Схема направления (как реально выглядит вызов от контроллера к БД):**

```
Controller.getOrder()  →  OrderService.findOrder()  →  orderRepository.findById()  →  (ответ из БД)
     (низ цепочки,                                              (верх цепочки,
      точка подписки)                                            источник данных)

     DOWNSTREAM  ◀─────────────────────────────────────────────  UPSTREAM
     (получает готовый результат)                                (откуда данные берутся)
```

Любой оператор в середине цепочки одновременно является **"downstream"** для того, что выше него, и **"upstream"** для того, что ниже. Это относительное понятие, а не фиксированная точка.

## Как это работает с subscribeOn

`subscribeOn` меняет поток, на котором выполняется **вся цепочка от источника** (то есть влияет на **upstream** — на сам вызов в БД), независимо от того, на каком уровне архитектуры (контроллер, сервис или репозиторий) физически стоит вызов `subscribeOn` в коде:

Источник: https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html

EN:

> "subscribeOn applies to the subscription process, when the backward chain is being constructed... Changes the Thread from which the whole chain of operators subscribes."

RU:

> "subscribeOn применяется к процессу подписки, в момент построения обратной цепочки... Изменяет поток, из которого происходит подписка всей цепочки операторов."

```java
@Service
class OrderService {

    private final OrderRepository orderRepository; // блокирующий JDBC-вызов

    Mono<Order> findOrder(String id) {
        return Mono.fromCallable(() -> orderRepository.findById(id)) // блокирующий вызов в БД (верх цепочки)
            .subscribeOn(Schedulers.boundedElastic()); // именно этот вызов переезжает с event-loop на boundedElastic
    }
}
```

Схема:

```
Controller.getOrder()  →  OrderService.findOrder()  →  orderRepository.findById()
                                                              ▲
                                                              │
                                            subscribeOn(boundedElastic) стоит здесь,
                                            но эффект применяется именно К ИСТОЧНИКУ —
                                            блокирующий JDBC-вызов уходит с event-loop потока
```

Если бы `orderRepository.findById()` выполнялся прямо на event-loop потоке без `subscribeOn` — он заблокировал бы этот поток на время похода в БД, и все остальные HTTP-запросы, обслуживаемые тем же event-loop, встали бы в ожидание.

Источник: https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html

EN:

> "A bounded elastic thread pool (Schedulers.boundedElastic()). This is a handy way to give a blocking process its own thread so that it does not tie up other resources. This is a better choice for I/O blocking work."

RU:

> "Ограниченный эластичный пул потоков (Schedulers.boundedElastic()). Это удобный способ выделить блокирующему процессу собственный поток, чтобы он не занимал другие ресурсы. Это лучший выбор для блокирующей I/O-работы."

## Как это работает с publishOn

`publishOn` меняет поток только для операторов **ниже себя** (downstream по коду), не трогая то, что выше.

Источник: https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html

EN:

> "publishOn applies in the same way as any other operator, in the middle of the subscriber chain. It takes signals from upstream and replays them downstream while executing the callback on a worker from the associated Scheduler."

RU:

> "publishOn применяется так же, как любой другой оператор, в середине цепочки подписчиков. Он принимает сигналы от upstream и передаёт их дальше вниз по цепочке (downstream), выполняя callback на потоке из связанного Scheduler."

- Практический бизнес-кейс:
  - после того как данные пришли из БД (уже на `boundedElastic`), нужно выполнить тяжёлую CPU-трансформацию — переключаемся на отдельный пул `parallel`, предназначенный именно для быстрых неблокирующих вычислений:

```java
@RestController
class OrderController {

    private final OrderService orderService;

    @GetMapping("/orders/{id}")
    Mono<OrderResponse> getOrder(@PathVariable String id) {
        return orderService.findOrder(id)          // выполняется на boundedElastic (из-за subscribeOn внутри сервиса)
            .publishOn(Schedulers.parallel())       // ГРАНИЦА: переключаемся на CPU-пул для тяжёлой трансформации
            .map(OrderResponse::from);               // трансформация выполняется на parallel-потоке
    }
}
```

Источник: https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html

EN:

> "A fixed pool of workers that is tuned for parallel work (Schedulers.parallel()). It creates as many workers as you have CPU cores."

RU:

> "Фиксированный пул workers, настроенный для параллельной работы (Schedulers.parallel()). 
> Он создаёт столько workers, сколько у вас ядер CPU."

Схема:

```
orderService.findOrder()  →  publishOn(parallel)  →  map(OrderResponse::from)
   [поток boundedElastic]           │                  [поток parallel]
                                     └── ГРАНИЦА: всё, что НИЖЕ по коду, переезжает на CPU-пул
```

**Важное уточнение (исправление ошибки из предыдущей версии):** 
 - `Schedulers.parallel()` — это отдельный, независимый CPU-пул потоков, никак не связанный с Netty event loop.

Источник: https://projectreactor.io/docs/netty/1.1.21/reference

EN:

> "By default Reactor Netty uses an 'Event Loop Group', where the number of the worker threads equals the number of processors available to the runtime."

RU:

> "По умолчанию Reactor Netty использует 'Event Loop Group', где число рабочих потоков равно числу процессоров, доступных среде выполнения."

- Финальная запись ответа в клиентский `Channel` выполняется reactor-netty автоматически на своём event loop-потоке, привязанном к соединению — вручную это делать через `publishOn` не нужно и невозможно.

---
**"worker"** в терминологии Reactor это не буквально поток (`Thread`), а отдельная абстракция-исполнитель, привязанная к потоку.

## Что такое Worker по официальному Javadoc

Согласно `Scheduler.Worker`, **worker** — это представление асинхронной границы, которая исполняет задачи, а не сам поток:

Источник: https://projectreactor.io/docs/core/release/api/reactor/core/scheduler/Scheduler.Worker.html

EN:

> "A worker representing an asynchronous boundary that executes tasks."

RU:

> "Worker", представляющий асинхронную границу, которая исполняет задачи."

То есть `Worker` — это интерфейс-абстракция с методом `schedule(Runnable task)`, на который тебе ставится задача, а **worker** уже сам решает, на каком потоке (backing thread) её выполнить.

## Как это устроено конкретно у Schedulers.parallel()

Для `parallel()` документация описывает создание фиксированного пула **workers**, каждый из которых опирается на однопоточный `ExecutorService`:

Источник: https://projectreactor.io/docs/core/release/api/reactor/core/scheduler/Schedulers.html

EN:

> "Scheduler that hosts a fixed pool of single-threaded ExecutorService-based workers and is suited for parallel work."

RU:

> "Scheduler, содержащий фиксированный пул однопоточных workers на основе ExecutorService, предназначенный для параллельной работы."

Это значит: 
 - в `Schedulers.parallel()` каждый **worker** жёстко привязан к ровно одному постоянному потоку (single-threaded), поэтому в этом конкретном случае "worker" и "поток" почти взаимозаменяемы — **worker** _не разделяет_ свой единственный поток с другими **workers**, и задача, поставленная на конкретный **worker**, всегда выполнится на его закреплённом потоке.

## Где твоя интуиция про очередь задач верна — в других шедулерах

Твоё описание ("**worker** — это задача, которая ставится в очередь, и берётся потоком из пула") точнее описывает `boundedElastic()`, где пул динамически создаёт **workers** с верхней границей по числу поддерживающих потоков, а сверх этого лимита — уже по числу задач в очереди:

Источник: https://projectreactor.io/docs/core/release/api/reactor/core/scheduler/Schedulers.html

EN:

> "A Scheduler that dynamically creates workers with an upper bound to the number of backing threads and after that on the number of enqueued tasks."

RU:

> "Scheduler, который динамически создаёт воркеров с верхней границей по числу поддерживающих потоков, а после её достижения — по числу задач, поставленных в очередь."

Здесь видно: 
 - если все реальные потоки заняты, новые задачи именно **ставятся в очередь** и ждут своего потока — то есть формулировка про "**worker** = **задача** _в очереди_, которая берётся потоком из пула" точно описывает поведение `boundedElastic`, но не буквальную структуру `parallel()`, где **worker** жёстко закреплён за одним потоком без пере-использования.

## Итоговое уточнение цитаты про parallel()

**Правильная формулировка:** 
 - у `Schedulers.parallel()` создаётся столько **однопоточных workers**, сколько у вас ядер CPU — каждый **worker** здесь и есть обёртка вокруг одного выделенного постоянного потока, а не задача из очереди. 

---

## Мнемоника для запоминания

- `subscribeOn` — "на каком потоке взять данные из источника" — влияет на весь путь до самого вызова в БД/внешний сервис, место вызова в коде не важно.
- `publishOn` — "с этого места переключаемся" — влияет только на операторы физически ниже него в коде (то, что происходит с данными ПОСЛЕ их получения).

Источник: https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html

EN:

> "Only the closest subscribeOn call in the downstream chain effectively schedules subscription and request signals to the source... Using multiple subscribeOn calls will introduce unnecessary Thread switches that have no value."

RU:

> "Только самый близкий вызов subscribeOn в цепочке downstream реально влияет на планирование подписки и сигналов запроса к источнику... Использование нескольких вызовов subscribeOn приводит к бесполезным переключениям потоков."

Именно поэтому в реальном коде порядок вызова `publishOn` критичен (переставил — изменил поведение), а `subscribeOn` можно ставить в любом месте цепочки (в контроллере, в сервисе, в репозитории) с одинаковым эффектом — он всегда "дотягивается" до источника данных.
