# Реактивный стек: как app-level demand смыкается с TCP rwnd

## Оглавление

- [1. Общая концепция](#1-общая-концепция)
- [2. Механизм: autoRead в Netty](#2-механизм-autoread-в-netty)
- [3. Уточнение неточности](#3-уточнение-неточности)
- [4. Почему это нужно именно из-за модели «поток на много соединений»](#4-почему-это-нужно-именно-из-за-модели-поток-на-много-соединений)
- [5. Итоговая цепочка](#5-итоговая-цепочка)

---

## 1. Общая концепция

Когда цепочка спроса подписчика (Subscription.request(n)) доходит до сетевого Publisher — драйвера R2DBC или реактивного веб-клиента — demand в какой-то момент исчерпывается (например, запросили 100 элементов, их отправили, demand стал 0, потому что приложение не успевает читать данные). В этот момент драйвер прекращает читать сокет, receive buffer ОС заполняется, и TCP receive window сжимается — вплоть до zero window. Это подтверждает исходную концепцию: app-level demand и TCP flow control — не два независимых механизма, а один сквозной, где app-level демонт становится триггером для транспортного уровня.

---

## 2. Механизм: autoRead в Netty

Технически это реализовано через флаг `ChannelConfig#setAutoRead()`. Когда downstream demand равен нулю, Reactor Netty (и построенные на нём R2DBC-драйверы) переводят `autoRead` в `false` — событийный поток перестаёт сам вызывать `channel.read()`.

- Источник: https://groups.google.com/g/netty/c/yTlfa78ye8Y

> "The high-level idea is to set autoread as false, and call... channel.read() when needed."

RU:

> «Общая идея — выставить autoread в false и вызывать... channel.read() только когда нужно.»

- Источник: https://github.com/netty/netty/issues/8953

> "The ChannelConfig#setAutoRead() and its underlying state will just control if read() will be automatically called upon receiving read-complete events."

RU:

> «ChannelConfig#setAutoRead() и связанное с ним внутреннее состояние управляют лишь тем, будет ли read() автоматически вызываться при получении событий завершения чтения.»

Как только `autoRead=false`, никто больше не вычерпывает байты из OS receive buffer на этом сокете — буфер заполняется, и ОС сама (без участия приложения) сжимает `rwnd`, вплоть до zero window.

- Источник: https://projectreactor.io/docs/core/release/reference/reactiveProgramming.html#reactive.backpressure

> "When implementing backpressure in Reactor, the way consumer pressure is propagated back to the source is by sending a request to the upstream operator."

RU:

> «При реализации backpressure в Reactor давление потребителя передаётся обратно к источнику путём отправки request вышестоящему оператору.»

- Источник: https://projectreactor.io/docs/core/release/reference/gettingStarted.html

> "Suited for Microservices Architecture, Reactor Netty offers backpressure-ready network engines for HTTP (including Websockets), TCP, and UDP."

RU:

> «Подходит для микросервисной архитектуры: Reactor Netty предлагает сетевые движки, готовые к backpressure, для HTTP (включая WebSocket), TCP и UDP.»

---

## 3. Уточнение неточности

Формулировка «приложение посылает в send buffer сигнал: не могу принять больше данных» описывает направление неверно. На самом деле приложение (через `autoRead=false`) просто **перестаёт читать свой receive buffer**. Дальше работает чистая механика TCP, без всякого «сигнала demand» на транспортном уровне: ОС сама детектирует, что буфер не освобождается, и в очередном исходящем ACK указывает уменьшившийся (или нулевой) Window — тот же стандартный flow control, что и в блокирующей модели. Разница только в том, что триггером здесь служит app-level флаг `autoRead`, а не блокировка потока на `read()`.

---

## 4. Почему это нужно именно из-за модели «поток на много соединений»

Если бы событийный цикл Netty продолжал вызывать `read()` для всех сокетов без учёта demand, единственный event-loop-поток либо забивал бы память чужими данными для медленных подписчиков, либо не мог бы физически применить backpressure per-соединение (нельзя «заснуть на read» для одного из тысяч сокетов, не заблокировав остальные). Поэтому именно `autoRead`, привязанный к состоянию demand конкретного канала, транслирует «на уровне приложения объекты закончились» в «на уровне транспорта хватит слать» — отдельно для каждого сокета, без блокировки самого event-loop-потока.

---

## 5. Итоговая цепочка

1. Подписчик исчерпывает demand (запросил N, N получено, дальше — 0).
2. Драйвер (Reactor Netty / R2DBC) выставляет `autoRead=false` для конкретного канала.
3. Event-loop-поток перестаёт вызывать `channel.read()` на этом сокете.
4. OS receive buffer сокета не вычерпывается и заполняется входящими данными.
5. Ядро ОС фиксирует нехватку места и в очередном ACK указывает уменьшённый Window, вплоть до zero window.
6. Отправитель на другой стороне видит Window=0 и прекращает слать новые сегменты — ровно тот же TCP flow control, что в блокирующей модели, только запущенный через app-level demand, а не через блокировку потока.
