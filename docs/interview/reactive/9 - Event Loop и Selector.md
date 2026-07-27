# Event Loop и Selector: поток vs объект

## Оглавление

1. [Краткий итог](#краткий-итог)
2. [Что такое Selector в Java NIO](#что-такое-selector-в-java-nio)
3. [Где появляется поток и event loop](#где-появляется-поток-и-event-loop)
4. [Принимает ли Selector запросы от клиентских сокетов](#принимает-ли-selector-запросы-от-клиентских-сокетов)
5. [Связь с Netty / Reactor Netty](#связь-с-netty--reactor-netty)

***

## Краткий итог

**Утверждение:**  
В Java NIO `Selector` сам по себе не является потоком и не является «входным потоком». Это объект-мультиплексор, который сообщает о готовности зарегистрированных каналов к операциям ввода-вывода. В типичной модели Netty поток event loop вызывает у `Selector` метод `select()`, получает набор готовых `SelectionKey` и выполняет обработку соответствующих событий. `Selector` не «принимает запросы» от сокетов и не «отправляет их» в event loop: поток event loop сам запрашивает у него результаты операции выбора.

**Источник:**  
https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> "A selector maintains three sets of selection keys. The key set contains the keys representing the current channel registrations of this selector. The selected-key set is the set of keys such that each key's channel was detected to be ready for at least one of the operations identified in the key's interest set."

RU:

> «Селектор поддерживает три набора ключей. Набор ключей содержит ключи, представляющие текущие регистрации каналов этим селектором. Набор выбранных ключей — это набор ключей, для которых канал был обнаружен готовым по крайней мере к одной из операций, указанных в наборе интересов ключа.»

***

## Что такое Selector в Java NIO

**Утверждение:**  
`Selector` — это объект Java NIO, мультиплексор объектов `SelectableChannel`. Он хранит набор зарегистрированных `SelectionKey` и набор ключей, чьи каналы готовы к операциям ввода-вывода. Операция `select()` обращается к механизму селекции ОС и определяет, какие зарегистрированные каналы готовы к операциям из `interestOps`.

Один `Selector` может использоваться для отслеживания множества каналов. Java NIO не определяет, что объект `Selector` обязан использоваться строго одним потоком, однако в Netty обычно конкретный `NioEventLoop` владеет своим `Selector` и выполняется в одном выделенном потоке.

`Selector` не является ни потоком, ни «стримом ввода/вывода». Он сам не читает и не обрабатывает данные из сокетов, а предоставляет информацию о готовности каналов. После уведомления о готовности код event loop вызывает нужную операцию канала, например `accept()`, `read()` или `write()`.

**Источник:**  
https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> "A multiplexor of SelectableChannel objects. A selector may be created by invoking the open method of this class, which will use the system's default selector provider to create a new selector."

RU:

> «Мультиплексор объектов `SelectableChannel`. Селектор может быть создан путём вызова метода `open` этого класса, который использует поставщика селекторов по умолчанию системы для создания нового селектора.»

***

## Где появляется поток и event loop

**Утверждение:**  
Полезно различать несколько связанных, но разных понятий:

- **event loop** — логический цикл: ожидание I/O-событий, обработка готовых событий и выполнение поставленных задач;
- **`EventLoop` в Netty** — интерфейс и исполнитель этой модели;
- **`NioEventLoop`** — NIO-реализация `SingleThreadEventLoop`, использующая `Selector`;
- **event-loop thread** — поток, в котором выполняется конкретный экземпляр `NioEventLoop`.

В Netty `NioEventLoop` — это реализация `SingleThreadEventLoop`: один экземпляр event loop выполняется одним потоком. Внутри этого потока работает цикл обработки: ожидание готовых I/O-событий через `Selector`, обход выбранных ключей и выполнение очередей задач. Именно поток event loop вызывает `select()` у селектора, а затем обрабатывает сетевые события. Сам `Selector` только участвует в мультиплексировании и сообщает о готовности каналов.

Важно не путать `EventLoop` и `EventLoopGroup`: один `EventLoop` в Netty обычно работает в одном потоке и может обслуживать несколько `Channel`, а `EventLoopGroup` содержит множество таких event loop-ов.

**Источник (описание NioEventLoop):**  
https://netty.io/4.1/api/io/netty/channel/nio/NioEventLoop.html

EN:

> "SingleThreadEventLoop implementation which register the Channels to a Selector and so does the multi-plexing of these in the event loop."

RU:

> «Реализация `SingleThreadEventLoop`, которая регистрирует каналы в `Selector` и выполняет их мультиплексирование в event loop.»

**Дополнительный источник (описание EventLoop):**  
https://netty.io/4.1/api/io/netty/channel/EventLoop.html

EN:

> "Will handle all the I/O operations for a `Channel` once registered. One `EventLoop` instance will usually handle more than one `Channel` but this may depend on implementation details and internals."

RU:

> «Будет обрабатывать все операции ввода-вывода для `Channel` после регистрации. Один экземпляр `EventLoop` обычно обслуживает более одного `Channel`, хотя это может зависеть от деталей реализации.»

**Пояснение по циклу (упрощённый псевдокод):**

```java
while (!stopped) {
    int readyCount = selector.select();

    if (readyCount > 0) {
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();

            processSelectedKey(key);
        }
    }

    executeQueuedTasks();
}
```

Здесь видно, что:

- цикл принадлежит потоку event loop;
- `selector.select()` вызывается из этого потока;
- обрабатываются не «готовые каналы» напрямую, а `SelectionKey`, связанные с каналами;
- ключ удаляется из `selected-key set` сразу после извлечения, чтобы не обработать его повторно на следующей итерации;
- обработка событий и задач тоже происходит в этом же потоке.

Этот псевдокод упрощён: в реальном Netty `NioEventLoop` логика сложнее и включает дополнительные внутренние механизмы, например работу с очередями задач, wakeup-логику и балансировку времени между I/O и выполнением задач.

***

## Принимает ли Selector запросы от клиентских сокетов

**Утверждение:**  
Фраза «Selector принимает запросы из клиентских сокетов и отправляет их в event loop» неточна. Селектор не принимает HTTP-запросы и не пересылает данные: он участвует только в определении готовности зарегистрированных каналов к операциям ввода-вывода.

Для серверного сокета используется `ServerSocketChannel`: его регистрируют с интересом `OP_ACCEPT`. Когда ключ готов к этой операции, код event loop вызывает `accept()` и получает новый `SocketChannel` для принятого клиентского соединения.

Установленное клиентское соединение представлено `SocketChannel`. Обычно его регистрируют с `OP_READ`, а `OP_WRITE` включают, когда действительно есть ожидающие данные для отправки. Для неблокирующего исходящего подключения применяют `OP_CONNECT`.

Когда выполняется `select()`, система определяет, какие зарегистрированные каналы готовы к интересующим операциям. Соответствующие `SelectionKey` появляются в `selected-key set`, после чего поток event loop сам решает, что делать: принять соединение, завершить подключение, прочитать данные или записать данные.

Более точная формулировка: `Selector` не принимает и не пересылает «запросы», а отслеживает готовность каналов к операциям ввода-вывода. Реальная обработка байтов и протоколов выполняется кодом event loop и зарегистрированными обработчиками.

**Источник:**  
https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> "A selection operation queries the underlying operating system for an update as to the readiness of each registered channel to perform any of the operations identified by its keys' interest set."

RU:

> «Операция выбора запрашивает у базовой операционной системы обновлённую информацию о готовности каждого зарегистрированного канала выполнять какие-либо операции, указанные в его наборе интересов.»

EN:

> "Selects a set of keys whose corresponding channels are ready for I/O operations."

RU:

> «Выбирает набор ключей, соответствующие каналы которых готовы к операциям ввода-вывода.»

***

## Связь с Netty / Reactor Netty

**Утверждение:**  
В Netty используется `EventLoopGroup`, который управляет набором event loop-ов. Для Java NIO-транспорта каждый `NioEventLoop` работает со своим `Selector` и обслуживает назначенные ему `Channel`.

В типичной серверной конфигурации Netty могут использоваться отдельная группа для acceptor-канала (`ServerSocketChannel`) и отдельная группа для клиентских (`child`) каналов, на которых обрабатываются чтение и запись. Но это распространённая серверная схема, а не обязательное правило для любого использования Netty или Reactor Netty.

В Reactor Netty это абстрагируется через `LoopResources`, который создаёт и предоставляет `EventLoopGroup` и фабрики каналов. Есть отдельные точки расширения для selector group и worker group, однако настройки по умолчанию не означают, что во всех сценариях будут созданы два полностью независимых набора потоков.

В частности, значение числа selector threads по умолчанию равно `-1`, то есть отдельная selector group по умолчанию не создаётся; worker-потоки также выполняют работу селекции и I/O-обработки.

**Источник (LoopResources и EventLoopGroup в Reactor Netty):**  
https://projectreactor.io/docs/netty/release/api/reactor/netty/resources/LoopResources.html

EN:

> "An `EventLoopGroup` selector with associated `Channel` factories."

RU:

> «Селектор `EventLoopGroup` с соответствующими фабриками каналов.»

EN:

> "Create a simple LoopResources to provide automatically for EventLoopGroup and Channel factories."

RU:

> «Создаёт простой `LoopResources`, который автоматически предоставляет `EventLoopGroup` и фабрики каналов.»

EN:

> "Callback for server select EventLoopGroup creation, this is the EventLoopGroup for the acceptor channel."

RU:

> «Колбэк для создания серверного `select EventLoopGroup`, это `EventLoopGroup` для acceptor-канала.»

EN:

> "Callback for server EventLoopGroup creation, this is the EventLoopGroup for the child channel."

RU:

> «Колбэк для создания серверного `EventLoopGroup`, это `EventLoopGroup` для дочерних каналов.»

**Дополнительный источник (дефолтные настройки LoopResources):**  
https://github.com/reactor/reactor-netty/blob/master/reactor-netty-core/src/main/java/reactor/netty/resources/LoopResources.java

EN:

> "Default worker thread count, fallback to available processor (but with a minimum value of 4)."

RU:

> «Количество worker-потоков по умолчанию определяется по числу доступных процессоров, но не меньше 4.»

EN:

> "Default selector thread count, fallback to -1 (no selector thread)."

RU:

> «Количество selector-потоков по умолчанию равно -1, то есть отдельный selector thread по умолчанию не используется.»

**Вывод:**  
Если говорить просто, то `Selector` — это механизм уведомления о готовности каналов, `NioEventLoop` — однопоточный исполнитель цикла обработки, а `EventLoopGroup` / `LoopResources` — уровень управления набором таких циклов и их конфигурацией.
