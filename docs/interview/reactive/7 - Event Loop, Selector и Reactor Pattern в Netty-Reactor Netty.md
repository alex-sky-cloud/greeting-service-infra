# Event Loop, Selector и Reactor Pattern в Netty / Reactor Netty

## Оглавление

1. [Ядро ОС узнаёт о данных первым](#%D1%8F%D0%B4%D1%80%D0%BE-%D0%BE%D1%81-%D1%83%D0%B7%D0%BD%D0%B0%D1%91%D1%82-%D0%BE-%D0%B4%D0%B0%D0%BD%D0%BD%D1%8B%D1%85-%D0%BF%D0%B5%D1%80%D0%B2%D1%8B%D0%BC)
2. [Что такое Event Pool и почему его называют селекторным механизмом](#2-%D1%87%D1%82%D0%BE-%D1%82%D0%B0%D0%BA%D0%BE%D0%B5-event-pool-%D0%B8-%D0%BF%D0%BE%D1%87%D0%B5%D0%BC%D1%83-%D0%B5%D0%B3%D0%BE-%D0%BD%D0%B0%D0%B7%D1%8B%D0%B2%D0%B0%D1%8E%D1%82-%D1%81%D0%B5%D0%BB%D0%B5%D0%BA%D1%82%D0%BE%D1%80%D0%BD%D1%8B%D0%BC-%D0%BC%D0%B5%D1%85%D0%B0%D0%BD%D0%B8%D0%B7%D0%BC%D0%BE%D0%BC)
3. [Что такое Selector на самом деле](#3-%D1%87%D1%82%D0%BE-%D1%82%D0%B0%D0%BA%D0%BE%D0%B5-selector-%D0%BD%D0%B0-%D1%81%D0%B0%D0%BC%D0%BE%D0%BC-%D0%B4%D0%B5%D0%BB%D0%B5)
4. [Boss group и Worker group: кто регистрирует что](#4-boss-group-%D0%B8-worker-group-%D0%BA%D1%82%D0%BE-%D1%80%D0%B5%D0%B3%D0%B8%D1%81%D1%82%D1%80%D0%B8%D1%80%D1%83%D0%B5%D1%82-%D1%87%D1%82%D0%BE)
5. [Что значит «блокируется» на `select()`](#5-%D1%87%D1%82%D0%BE-%D0%B7%D0%BD%D0%B0%D1%87%D0%B8%D1%82-%D0%B1%D0%BB%D0%BE%D0%BA%D0%B8%D1%80%D1%83%D0%B5%D1%82%D1%81%D1%8F-%D0%BD%D0%B0-select)
6. [Аналогия: диспетчер такси](#6-%D0%B0%D0%BD%D0%B0%D0%BB%D0%BE%D0%B3%D0%B8%D1%8F-%D0%B4%D0%B8%D1%81%D0%BF%D0%B5%D1%82%D1%87%D0%B5%D1%80-%D1%82%D0%B0%D0%BA%D1%81%D0%B8)
6. [`epoll_wait` и почему это не busy polling](#7-epoll_wait-%D0%B8-%D0%BF%D0%BE%D1%87%D0%B5%D0%BC%D1%83-%D1%8D%D1%82%D0%BE-%D0%BD%D0%B5-busy-polling)
7. [Как канал становится готовым](#8-%D0%BA%D0%B0%D0%BA-%D0%BA%D0%B0%D0%BD%D0%B0%D0%BB-%D1%81%D1%82%D0%B0%D0%BD%D0%BE%D0%B2%D0%B8%D1%82%D1%81%D1%8F-%D0%B3%D0%BE%D1%82%D0%BE%D0%B2%D1%8B%D0%BC)
8. [Как Selector определяет готовый канал](#9-%D0%BA%D0%B0%D0%BA-selector-%D0%BE%D0%BF%D1%80%D0%B5%D0%B4%D0%B5%D0%BB%D1%8F%D0%B5%D1%82-%D0%B3%D0%BE%D1%82%D0%BE%D0%B2%D1%8B%D0%B9-%D0%BA%D0%B0%D0%BD%D0%B0%D0%BB)
9. [`OP_ACCEPT` и `OP_READ`](#10-op_accept-%D0%B8-op_read)
10. [Push или pull: модель Event Loop](#11-push-%D0%B8%D0%BB%D0%B8-pull-%D0%BC%D0%BE%D0%B4%D0%B5%D0%BB%D1%8C-event-loop)
11. [Мультиплексор и Reactor pattern](#12-%D0%BC%D1%83%D0%BB%D1%8C%D1%82%D0%B8%D0%BF%D0%BB%D0%B5%D0%BA%D1%81%D0%BE%D1%80-%D0%B8-reactor-pattern)
12. [Ядро ОС и сетевые данные](#13-%D1%8F%D0%B4%D1%80%D0%BE-%D0%BE%D1%81-%D0%B8-%D1%81%D0%B5%D1%82%D0%B5%D0%B2%D1%8B%D0%B5-%D0%B4%D0%B0%D0%BD%D0%BD%D1%8B%D0%B5)
13. [Практическая схема HTTP-запроса](#14-%D0%BF%D1%80%D0%B0%D0%BA%D1%82%D0%B8%D1%87%D0%B5%D1%81%D0%BA%D0%B0%D1%8F-%D1%81%D1%85%D0%B5%D0%BC%D0%B0-http-%D0%B7%D0%B0%D0%BF%D1%80%D0%BE%D1%81%D0%B0)
14. [Project Reactor и Observer pattern](#15-project-reactor-%D0%B8-observer-pattern)

***

## 1. Ядро ОС узнаёт о данных первым

При **входящем** сетевом трафике, **сетевой стек** ОС принимает пакеты и помещает полученные данные **в буфер сокета**.
- JVM-приложение **не обращается к сетевой** карте **напрямую**: оно читает и записывает данные **через сокет**, а затем читает доступные данные из сокета — например, через **SocketChannel** или Netty Channel.

Здесь точнее разделены этапы:

- ОС принимает пакеты, данные становятся доступны через сокет, JVM читает их через API.
- **SocketChannel** в Java представляет канал для **потокового соединения через сокет** и поддерживает чтение байтов в **ByteBuffer**.

Источник: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SocketChannel.html

EN:

> “A selectable channel for stream-oriented connecting sockets.”

RU:

> «Канал, доступный для выбора, для потоковых соединяющихся сокетов».

Практически это означает следующее:
- клиент отправляет TCP-пакеты, операционная система принимает их и делает доступными для сокета, а затем Java-приложение читает накопившиеся данные через `SocketChannel`, Netty `Channel` или иной API.

Не стоит представлять `read()` как прямое «чтение с сетевого провода».
- Для приложения это операция чтения данных, уже предоставленных ему сетевым стеком ОС.

- Источник: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/ReadableByteChannel.html

EN:

> “A channel that can read bytes.”

RU:

> «Канал, который может читать байты».

### Полезная аналогия

**Сеть** можно представить как **реку**, ядро ОС — как **водозабор и резервуар**, а приложение — как потребитель, который забирает воду из резервуара.
- Приложение не управляет сетевой картой напрямую; Приложение читает байты из интерфейса, который предоставляет ОС.

Эта аналогия полезна, но важно не воспринимать её слишком буквально:
- детали буферизации, доставки и уведомлений зависят от ОС, сетевого протокола и реализации JVM.

***

## Это относится к любому I/O?

Для сетевых запросов к PostgreSQL, Redis, Kafka, удалённому HTTP-сервису или другому TCP-серверу **принцип одинаков на транспортном уровне**:
- приложение открывает TCP-соединение,
- отправляет байты и
- получает байты в ответ.

**Отличается протокол**, поверх TCP: PostgreSQL использует свой протокол, HTTP-сервис — HTTP, а Redis — RESP.

TCP определяет надёжный поток байтов **между двумя сетевыми конечными точками**, а не формат SQL-запроса или HTTP-сообщения.

- Источник: https://www.rfc-editor.org/rfc/rfc9293.html

EN:

> “TCP is a connection-oriented, reliable, byte-stream transport protocol.”

RU:

> «TCP — это ориентированный на соединение, надёжный транспортный протокол потока байтов».

Следовательно, когда Java-приложение вызывает PostgreSQL или удалённый REST API, оно не «обходит» сетевой стек.
- Ответ удалённого сервера сначала приходит через TCP-соединение, а затем становится доступным приложению через его сокет.

Однако нельзя говорить, что **любое** I/O полностью идентично сетевому. Файловый I/O, работа с диском, `stdin`, IPC и сетевые сокеты имеют общую идею взаимодействия с ОС, но используют разные подсистемы, типы дескрипторов и особенности готовности к операциям.

***
---
# Краткий алгоритм работы Netty


### 1. Старт сервера: создаётся `LoopResources`

Reactor Netty создаёт или получает `LoopResources`. Этот объект предоставляет группы **event loop** и фабрики `Channel`.

- Источник: https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

EN:

> “`EventLoopGroup` selector with associated `Channel` factories.”

RU:

> «Селектор `EventLoopGroup` со связанными фабриками `Channel`.»

***

### 2. Создаются boss и worker группы

Для сервера используются две группы:

- **boss / acceptor group** — обслуживает серверный канал и **принимает подключения**;
- **worker group** — обслуживает дочерние каналы, то есть соединения конкретных клиентов.
- Источник: https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

EN:

> “Callback for server select `EventLoopGroup` creation, this is the `EventLoopGroup` for the acceptor channel.”

RU:

> `onServerSelect(boolean useNative)` - Метод (Callback), создающий **EventLoopGroup** для серверного канала, принимающего новые подключения ( acceptor channel.).

- Источник: https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

EN:

> “Callback for server `EventLoopGroup` creation, this is the `EventLoopGroup` for the child channel.”

RU:

> `onServer(boolean useNative)` - метод (Callback) для создания серверного `EventLoopGroup`, создающий **EventLoopGroup** `EventLoopGroup` для дочернего канала.»

***

### 3. Группы создают `EventLoop`

Каждая группа состоит из **объектов** `EventLoop`.

- Один `NioEventLoop` выполняется на одном Java-потоке.

- Источник: https://netty.io/4.1/api/io/netty/channel/nio/NioEventLoop.html

EN:

> “`SingleThreadEventLoop` implementation which register the `Channel`'s to a `Selector` and so does the multi-plexing of these in the event loop.”

RU:

> «Одно-поточная реализация `EventLoop`, которая регистрирует `Channel` в `Selector` и выполняет их мультиплексирование в event loop.»

**Worker group** по умолчанию создаёт количество worker _event loop_, равное _**числу доступных процессоров**_, но минимум 4.

- Источник: https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

EN:

> “Default worker thread count, fallback to available processor (but with a minimum value of 4).”

RU:

> «Число worker-потоков по умолчанию: количество доступных процессоров, но не менее 4.»

---

**Мультиплексирование** — это **механизм**, при котором один поток через `Selector` одновременно **отслеживает** множество `Channel` и **получает уведомление** только о тех, которые готовы к чтению, записи или принятию подключения.

Иначе: 
- один `EventLoop` обслуживает много соединений, а не ждёт каждое из них в отдельном потоке.

---

***

### 4. `EventLoop` создаёт `Selector`

При создании NIO **event loop** _создаётся_ его **рабочая инфраструктура**, включая `Selector`. 
- Затем этот **event loop** регистрирует в Selector каналы, которые будет обслуживать.

- Источник: https://netty.io/4.1/api/io/netty/channel/nio/NioEventLoop.html

EN:

> “`SingleThreadEventLoop` implementation which register the `Channel`'s to a `Selector` and so does the multi-plexing of these in the event loop.”

RU:

> «Однопоточная реализация `EventLoop`, которая регистрирует `Channel` в `Selector` и выполняет их мультиплексирование в event loop.»

Итого до первого клиентского подключения уже существуют:

 - boss `EventLoop` и его `Selector`;
 - worker `EventLoop`:
   - `Selector` **каждого** worker event loop.

***

### 5. Создаётся `ServerChannel`

При запуске сервер создаёт `ServerChannel`: 
 - объект Netty, представляющий серверный сокет, который слушает порт.

**Boss group** обслуживает этот **server channel** и ожидает новые подключения.

- Источник: https://netty.io/4.1/api/io/netty/bootstrap/ServerBootstrap.html

EN:

> “Set the `EventLoopGroup` for the parent (acceptor) and the child (client). These `EventLoopGroup`'s are used to handle all the events and IO for `ServerChannel` and `Channel`s.”

RU:

> «Устанавливает `EventLoopGroup` для родительского канала (тот что принял новый TCP-socket и создал новый Channel) и дочерних каналов (клиентских).
> 
> Эти `EventLoopGroup` используются для обработки всех событий и I/O `ServerChannel` и `Channel`.»

***

### 6. Boss принимает TCP-соединение

Когда клиент подключается, _boss_ **event loop** принимает TCP-соединение. 
- В этот момент Netty создаёт **дочерний client `Channel`** — Java-объект, представляющий **новое соединение** с конкретным клиентом.

**Boss group** _не создаёт_ **worker event loop** и _не создаёт_ worker Selector. 
- **Они уже существуют с момента старта сервера**.

***

### 7. Client `Channel` передаётся worker group

 - Boss передаёт новый client `Channel` в worker group. 
 - **Worker group** выбирает один из своих уже существующих event loop.

Именно выбранный **worker event loop** регистрирует этот client `Channel` в своём уже существующем Selector.

- Источник: https://netty.io/4.1/api/io/netty/channel/EventLoopGroup.html

EN:

> “Register a `Channel` with this `EventLoop`. The passed `ChannelFuture` will get notified once the registration was complete.”

RU:

> «Регистрирует `Channel` в этом `EventLoop`. Переданный `ChannelFuture` будет уведомлён после завершения регистрации.»
>

## Итоговая последовательность

1. `LoopResources` создаёт или предоставляет **boss** и **worker** `EventLoopGroup`.
2. Группы создают свои `EventLoop`.
3. Каждый _NIO_ `EventLoop` работает на одном Java-потоке и имеет свой `Selector`.
4. Создаётся `ServerChannel`; его обслуживает **boss group**.
5. Приходит TCP-подключение.
6. Boss принимает соединение и Netty создаёт client `Channel`.
7. Client `Channel` передаётся worker group.
8. Worker group выбирает существующий worker `EventLoop`.
9. Выбранный worker `EventLoop` регистрирует client `Channel` в своём Selector.

---

## 2. Что такое Event Pool и почему его называют селекторным механизмом

Под неформальным названием **Event Pool** обычно имеют в виду `EventLoopGroup`: группу рабочих **event loop**. 
- В **Reactor Netty** ресурс `LoopResources` создаёт и предоставляет `EventLoopGroup` и фабрики `Channel`.

- Источник: https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

EN:

> “Create a simple `LoopResources` to provide automatically for `EventLoopGroup` and `Channel` factories.”

RU:

> «Создаёт простой `LoopResources`, который автоматически предоставляет `EventLoopGroup` и фабрики `Channel`.»

Число **worker-потоков** по умолчанию берётся из числа доступных процессоров, но не может быть меньше четырёх.

- Источник: https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

EN:

> “Default worker thread count, fallback to available processor (but with a minimum value of 4).”

RU:

> «Число worker-потоков по умолчанию: количество доступных процессоров, но не менее 4.»

Для **Java NIO Netty** использует `NioEventLoop`. Это одно-поточный **event loop**, который регистрирует каналы в `Selector` и **мультиплексирует** их.

- Источник: https://netty.io/4.1/api/io/netty/channel/nio/NioEventLoop.html

EN:

> “`SingleThreadEventLoop` implementation which register the `Channel`'s to a `Selector` and so does the multi-plexing of these in the event loop.”

RU:

> «Одно-поточная реализация `EventLoop`, которая регистрирует `Channel` в `Selector` и тем самым выполняет их мультиплексирование в **event loop**.»

Именно для _**NIO**_ можно кратко представить `NioEventLoop` так: 
 - **один поток**, _один принадлежащий ему Selector_ и **множество зарегистрированных каналов**. 
 - Это не универсальное описание всех транспортов Netty: 
   - для **native transport** используются механизмы ОС, такие как `epoll`, `kqueue` или `io_uring`.

***

## 3. Что такое Selector на самом деле

`Selector` — объект Java NIO для регистрации каналов и выбора тех из них, которые готовы к операциям ввода-вывода. Он хранит набор всех зарегистрированных каналов и отдельный набор каналов, готовых к работе.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> “A selector maintains three sets of selection keys: The key set contains the keys representing the current channel registrations of this selector. … The selected-key set is the set of keys such that each key's channel was detected to be ready for at least one of the operations identified in the key's interest set.”

RU:

> «Selector поддерживает три набора ключей выбора: набор ключей содержит ключи, представляющие текущие регистрации каналов этого selector. … Набор выбранных ключей содержит ключи, чьи каналы были обнаружены готовыми хотя бы к одной из операций, указанных в наборе интересов ключа.»

Простыми словами: 

- **Selector** похож на общее табло. 
- На нём зарегистрированы все соединения, но после ожидания он показывает только те, по которым сейчас можно выполнять нужную операцию, например читать данные.

***

---

## Selector, EventLoop и worker group

Этот раздел относится уже не к модели `Publisher` и `Subscriber`, а к сетевому уровню Reactor Netty.

`Selector` из Java NIO позволяет одному потоку ожидать готовности операций сразу у множества неблокирующих каналов. Он не обрабатывает HTTP-запрос сам по себе и не выполняет бизнес-логику: его задача — сообщить, какие каналы готовы к `accept`, `read` или `write`.

- Источник: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Selector.html

EN:

> “A multiplexor of SelectableChannel objects.”

RU:

> «Мультиплексор объектов SelectableChannel».

- Источник: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Selector.html

EN:

> “A selector may be created by invoking the open method of this class, which will use the system's default selector provider to create a new selector.”

RU:

> «Selector можно создать вызовом метода `open`; он использует системный provider selector для создания нового selector».

### EventLoop

`EventLoop` — это поток, который циклически ожидает готовые сетевые события и запускает обработку соответствующих Channel. В Netty EventLoop также выполняет задачи, поставленные в его очередь.

Упрощённая модель:

```java
while (!stopped) {
    readyChannels = selector.select();

    for (Channel channel : readyChannels) {
        processNetworkEvent(channel);
    }

    executeQueuedTasks();
}
```

Это не исходный код Netty, а учебная модель.
- Она показывает основной принцип:
- **EventLoop** не ждёт данные от одного клиента; он ожидает события множества соединений.

- Источник: https://netty.io/4.1/api/io/netty/channel/EventLoop.html

EN:

> “Will handle all the I/O operations for a Channel once registered.”

RU:

> «После регистрации будет обрабатывать все I/O-операции для Channel».

### Worker group

`EventLoopGroup` — это набор **EventLoop**.
- Он распределяет каналы между потоками **EventLoop**, чтобы сервер мог параллельно обслуживать множество соединений и использовать несколько процессорных ядер.

- Источник: https://netty.io/4.1/api/io/netty/channel/EventLoopGroup.html

EN:

> “The EventLoopGroup is responsible for providing the EventLoop's for the newly created Channels.”

RU:

> «EventLoopGroup отвечает за предоставление EventLoop для вновь созданных Channel».

В Reactor Netty по умолчанию применяется Event Loop Group, а число worker-потоков связано с числом доступных процессоров.

- Источник: https://projectreactor.io/docs/netty/release/reference/index.html

EN:

> “By default Reactor Netty uses an ‘Event Loop Group’, where the number of the worker threads equals the number of processors available to the runtime.”

RU:

> «По умолчанию Reactor Netty использует Event Loop Group, в котором число worker-потоков равно числу процессоров, доступных среде выполнения».

---

## 4. Boss group и Worker group: кто регистрирует что

В сервере **Netty** обычно разделяют **две роли**: 

 - **acceptor** принимает новые подключения, а **worker** обслуживает уже принятые соединения. 
 - Reactor Netty прямо различает `EventLoopGroup` для **дочерних** каналов сервера и отдельную группу для **acceptor-канала**.

- Источник: https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

EN:

> “Callback for server `EventLoopGroup` creation, this is the `EventLoopGroup` for the child channel.”

RU:

> «Callback для создания серверного `EventLoopGroup`; это `EventLoopGroup` для дочернего канала.»

- Источник: https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

EN:

> “Callback for server select `EventLoopGroup` creation, this is the `EventLoopGroup` for the acceptor channel.”

RU:

> «Callback для создания серверного select `EventLoopGroup`; это `EventLoopGroup` для канала-акцептора.»

То есть **acceptor** следит за новыми подключениями, а **worker event loop** получает уже созданные каналы клиентов и обслуживает их I/O.

***

## 5. Что значит «блокируется» на `select()`

`Selector.select()` может приостановить поток. 
- Однако поток ждёт не данные одного конкретного соединения, а готовность хотя бы одного канала из набора, зарегистрированного в **Selector**.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> “This method performs a blocking selection operation. It returns only after at least one channel is selected, this selector's wakeup method is invoked, or the current thread is interrupted, whichever comes first.”

RU:

> «Этот метод выполняет блокирующую операцию выбора. Он возвращает управление только после того, как выбран хотя бы один канал, вызван метод `wakeup` этого selector или прерван текущий поток — в зависимости от того, что произойдёт раньше.»

- Это ожидание можно сравнить с **оператором**, который **ждёт звонка** _на одной из многих линий_. 
- Оператор свободен для обработки после того, как **сигнал** появляется хотя бы на одной линии.

***

## 6. Аналогия: диспетчер такси

Представьте диспетчерскую:

- `Selector` — табло, к которому подключены все машины;
- `Channel` — одна машина или одна линия связи;
- `EventLoop` — диспетчер;
- готовность канала — лампочка на табло, означающая, что по этой линии есть работа.

Диспетчер не звонит всем водителям по очереди. 
- Он смотрит на табло, получает список машин с сигналом и обрабатывает только их. 
- Это иллюстрация механизма регистрации и выбора готовых каналов, описанного в документации Java NIO.

***

## 7. `epoll_wait` и почему это не busy polling

- На Linux Netty может предпочитать native transport, включая `epoll`. 
- **Reactor Netty** прямо указывает `epoll`, `io_uring` и `kqueue` как доступные нативные транспорты.

- Источник: https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

EN:

> “Default value whether the native transport (epoll, io_uring, kqueue) will be preferred.”

RU:

> «Значение по умолчанию, определяющее, будет ли предпочтительным нативный транспорт (`epoll`, `io_uring`, `kqueue`).»

`epoll_wait()` ожидает событие, сигнал или истечение тайм-аута.

- Поэтому поток не обязан постоянно выполнять пустые проверки и расходовать процессорное время.

- Источник: https://man7.org/linux/man-pages/man2/epoll_wait.2.html

EN:

> “A call to epoll_wait() will block until either: a file descriptor delivers an event; the call is interrupted by a signal handler; or the timeout expires.”

**RU**:

> «Вызов `epoll_wait()` блокируется до тех пор, пока файловый дескриптор не доставит событие, вызов не будет прерван **обработчиком сигнала** либо не истечёт тайм-аут.»

***

## 8. Как канал становится готовым

Канал(**Channel**) считается готовым, когда ОС сообщает, что для него доступна хотя бы одна операция из набора интересов: 
- например, чтение данных или принятие нового подключения.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> “The underlying operating system is queried for an update as to the readiness of each remaining channel to perform any of the operations identified by its key's interest set.”

RU:

> «У базовой операционной системы запрашивается обновлённая информация о готовности каждого оставшегося канала выполнить одну из операций, указанных в наборе интересов его ключа.»

Если готов хотя бы один канал, его ключ добавляется в набор выбранных ключей, а в ключе отмечаются именно готовые операции.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> “If the channel's key is not already in the selected-key set then it is added to that set and its ready-operation set is modified to identify exactly those operations for which the channel is now reported to be ready.”

RU:

> «Если ключ канала ещё не находится в наборе выбранных ключей, он добавляется в этот набор, а набор готовых операций изменяется так, чтобы указывать именно те операции, к которым канал сейчас признан готовым.»

***

## 9. Как Selector определяет готовый канал

**Selector** не читает данные сам и не выполняет прикладную обработку. 
- Его задача — запросить у ОС состояние зарегистрированных каналов и предоставить ключи готовых каналов.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> “A selection operation queries the underlying operating system for an update as to the readiness of each registered channel to perform any of the operations identified by its key's interest set.”

RU:

> «Операция выбора запрашивает у базовой операционной системы обновлённую информацию о готовности каждого зарегистрированного канала выполнить одну из операций, указанных в наборе интересов его ключа.»

После этого метод `select()` выбирает ключи, чьи соответствующие каналы готовы для I/O.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> “Selects a set of keys whose corresponding channels are ready for I/O operations.”

RU:

> «Выбирает набор ключей, чьи соответствующие каналы готовы к операциям ввода-вывода.»

***

## 10. `OP_ACCEPT` и `OP_READ`

`OP_ACCEPT` и `OP_READ` — это флаги, задающие интересующие операции канала.

`OP_ACCEPT` используется серверным каналом: он означает интерес к моменту, когда можно принять новое подключение.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectionKey.html

EN:

> “Operation-set bit for socket-accept operations.”

RU:

> «Бит набора операций для операций принятия сокетного подключения.»

`OP_READ` означает интерес к операции чтения из канала.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectionKey.html

EN:

> “Operation-set bit for read operations.”

RU:

> «Бит набора операций для операций чтения.»

Поэтому серверный канал обычно ожидает `OP_ACCEPT`, а канал клиентского соединения — `OP_READ`.

***

## 11. Push или pull: модель Event Loop

Модель _event loop_ **не является чистым** push или чистым pull. 
- Приложение инициирует `select()`, но при этом **Selector** запрашивает готовность у ОС и блокируется до появления события.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> “A selection operation queries the underlying operating system for an update as to the readiness of each registered channel.”

RU:

> «Операция выбора запрашивает у базовой операционной системы обновлённую информацию о готовности каждого зарегистрированного канала.»

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> “It returns only after at least one channel is selected, this selector's wakeup method is invoked, or the current thread is interrupted.”

RU:

> «Он возвращает управление только после того, как выбран хотя бы один канал, вызван метод `wakeup` этого selector или прерван текущий поток.»

Практически это означает: 
 - **event loop** просит ОС сообщить о готовности и спит, пока работа не появится.

***

## 12. Мультиплексор и Reactor pattern

Мультиплексирование — это обслуживание нескольких каналов через один механизм выбора. 
- `NioEventLoop` регистрирует каналы в `Selector` и выполняет их мультиплексирование в event loop.

- Источник: https://netty.io/4.1/api/io/netty/channel/nio/NioEventLoop.html

EN:

> “`SingleThreadEventLoop` implementation which register the `Channel`'s to a `Selector` and so does the multi-plexing of these in the event loop.”

RU:

> «Однопоточная реализация `EventLoop`, которая регистрирует `Channel` в `Selector` и тем самым выполняет их мультиплексирование в event loop.»

В упрощённом Reactor pattern роли выглядят так:

- `Selector` получает информацию, какие каналы готовы;
- `EventLoop` обрабатывает готовые события в своём потоке;
- обработчики канала выполняют конкретную работу с данными.

Как аналогия: 
 - **Selector** — табло, 
 - **EventLoop** — диспетчер, а 
 - **обработчики** Netty — сотрудники, которым диспетчер передаёт поступившую задачу.

***

## 13. Ядро ОС и сетевые данные

Сначала **готовность сокета** определяет **операционная система**. 

Java NIO прямо указывает, что операция выбора запрашивает у ОС информацию о готовности каждого зарегистрированного канала.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> “A selection operation queries the underlying operating system for an update as to the readiness of each registered channel.”

RU:

> «Операция выбора запрашивает у базовой операционной системы обновлённую информацию о готовности каждого зарегистрированного канала.»

После этого приложение получает ключи готовых каналов и уже может запускать чтение или другую нужную операцию. 
- Сам `select()` выбирает именно **ключи каналов** (Selector key's), готовых для I/O.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> “Selects a set of keys whose corresponding channels are ready for I/O operations.”

RU:

> «Выбирает набор ключей, чьи соответствующие каналы готовы к операциям ввода-вывода.»

***

## 14. Практическая схема HTTP-запроса

Ниже представлена упрощённая последовательность обработки HTTP-запроса в Reactor Netty:

```text
Клиент
  ↓
TCP-пакеты
  ↓
Ядро ОС и сетевой стек
  ↓
Selector сообщает о готовности канала
  ↓
EventLoop обрабатывает событие
  ↓
Netty ChannelPipeline
  ↓
HTTP-декодер
  ↓
Ваш WebFlux-контроллер или обработчик
  ↓
HTTP-ответ
```

Для нового TCP-подключения серверный канал получает событие `ACCEPT`. Netty создаёт Channel нового клиента, назначает его одному EventLoop и затем отслеживает готовность этого соединения к чтению и записи.

После прихода HTTP-данных EventLoop запускает обработчики в `ChannelPipeline`. Pipeline преобразует сетевые байты в объекты HTTP-запроса, а затем код приложения формирует HTTP-ответ, который записывается обратно в Channel.

- Источник: https://netty.io/4.1/api/io/netty/channel/ChannelPipeline.html

EN:

> “A list of ChannelHandlers which handles or intercepts inbound events and outbound operations of a Channel.”

RU:

> «Список ChannelHandler, которые обрабатывают или перехватывают входящие события и исходящие операции Channel».

***

## 15. Project Reactor и Observer pattern

Project Reactor реализует Reactive Streams: стандарт асинхронной обработки потоков данных с неблокирующим backpressure.

- Источник: https://www.reactive-streams.org/

EN:

> “The purpose of Reactive Streams is to provide a standard for asynchronous stream processing with non-blocking backpressure.”

RU:

> «Цель Reactive Streams — предоставить стандарт асинхронной обработки потоков данных с неблокирующим обратным давлением.»

Подписчик управляет спросом через `Subscription.request(n)`: он сообщает, сколько элементов готов получить.

- Источник: https://github.com/reactive-streams/reactive-streams-jvm

EN:

> “A Subscriber MUST signal demand via Subscription.request(long n) to receive onNext signals.”

RU:

> «Подписчик обязан сигнализировать спрос через `Subscription.request(long n)`, чтобы получать сигналы `onNext`.»

Издатель не имеет права отправить больше `onNext`, чем запросил подписчик.

- Источник: https://github.com/reactive-streams/reactive-streams-jvm

EN:

> “The total number of onNext's signalled by a Publisher to a Subscriber MUST be less than or equal to the total number of elements requested by that Subscriber's Subscription at all times.”

RU:

> «Общее число сигналов `onNext`, отправленных Publisher подписчику, всегда должно быть меньше или равно общему числу элементов, запрошенных этим подписчиком через `Subscription`.»

Поэтому Project Reactor похож на Observer pattern тем, что есть Publisher и Subscriber, но отличается обязательным управлением спросом: подписчик задаёт допустимый объём входящих элементов.

---

Говорить, что Project Reactor — это **Observer pattern**, можно только как **о начальной аналогии**.

В обоих случаях есть **источник событий** и _получатель уведомлений_, но Reactive Streams определяет более строгий контракт взаимодействия.

В Reactive Streams `Publisher` передаёт элементы `Subscriber`, а подписчик получает `Subscription`, через который может управлять спросом на элементы методом `request(long n)`.

- Источник: https://www.reactive-streams.org/

EN:

> “A Publisher is a provider of a potentially unbounded number of sequenced elements, publishing them according to the demand received from its Subscriber(s).”

RU:

> «Publisher — это поставщик потенциально неограниченного числа упорядоченных элементов, публикующий их в соответствии со спросом, полученным от Subscriber».

Именно управление спросом отличает Reactive Streams от упрощённой модели «источник всегда отправляет события, как только они появились».

- Источник: https://www.reactive-streams.org/

EN:

> “The purpose of Reactive Streams is to provide a standard for asynchronous stream processing with non-blocking back pressure.”

RU:

> «Цель Reactive Streams — предоставить стандарт для асинхронной обработки потоков с неблокирующим обратным давлением».

## Project Reactor — это Observer pattern? Разбираем миф

Project Reactor действительно построен **на основе** _Observer pattern_ в своей базовой механике подписки: у вас есть Publisher (аналог Subject/Observable), к которому подписывается Subscriber (аналог Observer), и Publisher уведомляет подписчика о новых элементах через методы `onNext`, `onError`, `onComplete`.

Но называть это "просто Observer pattern" — упрощение, которое упускает главное отличие.
**Классический Observer** из "банды четырёх" **не предполагает** никакого **механизма управления потоком** данных — Subject просто пушит события подписчикам, как только они появляются, без обратной связи от подписчика.
- Project Reactor и вообще Reactive Streams добавляют критически важный элемент — **backpressure** (обратное давление): **Subscriber** может явно сообщить издателю "не присылай мне больше N элементов, пока я не попрошу" через метод `request(n)` .

- Чистый Observer pattern — это push-модель без ограничений скорости (издатель может завалить подписчика данными).
- Iterator pattern — это чистая pull-модель (подписчик сам вызывает `next()` когда готов).
- Reactive Streams (и Project Reactor) — это гибрид push-pull: издатель пушит данные, но только в рамках квоты, которую заранее запросил подписчик.

Так что более точная формулировка —  "Project Reactor основан на идее Observer pattern, но расширяет её механизмом backpressure, которого в классическом Observer нет".
- Люди, которые говорят просто "это Observer", не лгут, но дают урезанную картину — как назвать автомобиль просто "телегой с двигателем", не упомянув про тормоза.
