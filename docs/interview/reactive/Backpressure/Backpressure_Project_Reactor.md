# Backpressure в Project Reactor: единый механизм от кода до TCP-окна

## Оглавление

- [1. Главная мысль: demand управляет TCP-окном, а не существует отдельно от него](#1-главная-мысль-demand-управляет-tcp-окном-а-не-существует-отдельно-от-него)
- [2. Механизм demand и request(n) на уровне Java-объектов](#2-механизм-demand-и-requestn-на-уровне-java-объектов)
- [3. Кто реально вызывает request — не бизнес-код](#3-кто-реально-вызывает-request--не-бизнес-код)
- [4. Граница между кодом и сетью: autoRead в Netty](#4-граница-между-кодом-и-сетью-autoread-в-netty)
- [5. Как demand физически достигает TCP-окна](#5-как-demand-физически-достигает-tcp-окна)
- [6. Пример из Spring WebFlux целиком](#6-пример-из-spring-webflux-целиком)

---

## 1. Главная мысль: demand управляет TCP-окном, а не существует отдельно от него

**Утверждение:**
Backpressure в Reactor — это не изолированный механизм уровня приложения, существующий сам по себе. Это управляющий сигнал, который в конце цепочки доходит до реактивного драйвера (Netty, R2DBC-клиент), и именно этот драйвер физически решает, читать ли байты из TCP-сокета прямо сейчас или нет. Без транспортного уровня Reactor backpressure не имел бы смысла для удалённого Publisher — он не может "материализовать" данные, которых ещё нет на проводе, он может только разрешить или запретить их считывание.

**Источник:** https://projectreactor.io/docs/core/3.5.14-SNAPSHOT/reference

> "When implementing backpressure in Reactor, the way consumer pressure is propagated back to the source is by sending a request to the upstream operator."

RU:
> "При реализации backpressure в Reactor давление потребителя передаётся обратно к источнику путём отправки request вышестоящему оператору."

Ключевая формула для запоминания: **Reactor backpressure — это решение "когда читать сокет", а TCP-окно — это то, через что это решение физически доходит до отправителя на другом конце соединения.** Это один сквозной механизм с двумя уровнями реализации, а не два параллельных механизма.

---

## 2. Механизм demand и request(n) на уровне Java-объектов

**Утверждение:**
Внутри одной JVM, без сети (например, при работе с `Flux.range()` или в тестах), backpressure — это действительно чистый счётчик demand между Java-объектами: подписчик говорит источнику "дай мне N элементов", и источник не эмитирует больше, чем разрешено. Это тот же механизм, что и на границе с сетью, просто в этом случае "источник" — обычный объект в памяти, а не сокет.

**Источник:** https://projectreactor.io/docs/core/3.5.14-SNAPSHOT/reference

> "The sum of current requests is sometimes referenced to as the current 'demand', or 'pending request'. Demand is capped at Long.MAX_VALUE, representing an unbounded request."

RU:
> "Сумма текущих запросов иногда называется текущим 'спросом' (demand), или 'ожидающим запросом'. Спрос ограничен значением Long.MAX_VALUE, что представляет неограниченный запрос."

Важно понимать: этот же самый счётчик demand, когда цепочка доходит до сетевого Publisher, не "заканчивается в коде" — он передаётся дальше, драйверу, который стоит на границе с транспортным уровнем. Раздел 4 показывает, что происходит на этой границе.

---

## 3. Кто реально вызывает request — не бизнес-код

**Утверждение:**
В бизнес-коде метод `request(n)` почти никогда не вызывается напрямую — эту работу делает скрытая цепочка internal Subscriber'ов, которую Reactor строит автоматически при подписке, от терминального подписчика вплоть до сетевого драйвера в самом низу.

**Источник:** https://eherrera.net/project-reactor-course/02-working-with-mono-and-flux/nothing-happens-until-subscribe.html

> "Once you subscribe, a chain of Subscriber objects is created, backward (up the chain) to the first publisher. This is effectively hidden from you."

RU:
> "Как только вы подписываетесь, создаётся цепочка объектов Subscriber, идущая назад (вверх по цепочке) до первого publisher'а. Это фактически скрыто от вас."

**Пример (Spring Boot):**

```java
@GetMapping("/orders")
public Flux<Order> getOrders() {
    return orderRepository.findAll()
        .limitRate(50);
}
```

Никто не пишет `subscription.request(50)` — этот вызов происходит внутри реализации `limitRate`, а затем спускается ниже, вплоть до R2DBC-драйвера, который транслирует его в реальное чтение данных из соединения с БД.

---

## 4. Граница между кодом и сетью: autoRead в Netty

**Утверждение:**
Именно здесь Java-уровень demand перестаёт быть "просто счётчиком" и превращается в физическое управление сетью. Reactor Netty связывает demand с флагом `autoRead` канала: пока demand есть — `autoRead = true`, и event loop продолжает вызывать `read()` на сокете; как только demand исчерпан — `autoRead = false`, и event loop прекращает читать канал вообще, независимо от того, что происходит на другом конце соединения.

**Источник:** https://stackoverflow.com/questions/19747826/propagate-back-pressure-between-two-netty-channels

> "You can check the status of the channel and enable auto read on the source channel."

RU:
> "Вы можете проверить статус канала и включить auto read на исходном канале."

Это и есть точка, где два "уровня" backpressure — счётчик Java-объектов и физика TCP — становятся одним процессом, а не двумя раздельными системами.

---

## 5. Как demand физически достигает TCP-окна

**Утверждение:**
Цепочка причин и следствий выглядит так: `request(n)` в Reactor → Netty выставляет `autoRead = false` → event loop не вызывает `channel.read()` → приложение не забирает байты из буфера приёма сокета → буфер заполняется → ядро ОС само уменьшает и в пределе обнуляет TCP receive window → удалённый отправитель физически не может продолжать слать данные, потому что протокол TCP это запрещает.

**Источник:** https://learn.microsoft.com/en-us/troubleshoot/windows-server/networking/description-tcp-features

> "The TCP receive window size is the amount of receive data (in bytes) that can be buffered during a connection. The sending host can send only that amount of data before it must wait for an acknowledgment and window update from the receiving host."

RU:
> "Размер TCP receive window — это объём данных приёма (в байтах), которые могут быть буферизованы во время соединения. Отправляющий хост может послать лишь такой объём данных, после чего должен ждать подтверждения и обновления окна от принимающего хоста."

Отсюда и главный вывод: Reactor не создаёт свой собственный, изолированный backpressure для сети — он управляет тем же самым TCP flow control, который существовал бы и без Reactor, просто делает это декларативно через `request(n)`, а не через явный блокирующий `read()`, как в императивном коде.

---

## 6. Пример из Spring WebFlux целиком

```java
@GetMapping(value = "/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
public Flux<Event> streamEvents() {
    return eventPublisher.getEventStream()
        .limitRate(100);
}
```

Путь demand в этом примере: клиент читает HTTP-ответ медленно → серверный Netty-канал, пишущий ответ, видит, что буфер записи заполнен (`channelWritabilityChanged`) → он сигнализирует "стоп" внутреннему Subscriber'у → тот прекращает вызывать `request(n)` у `eventPublisher` → `limitRate` не пропускает новый demand выше → источник (`eventPublisher`) физически не эмитирует новые события, пока клиент не начнёт читать быстрее.

**Источник:** https://www.baeldung.com/spring-webflux-backpressure

> "In Reactive Streams, backpressure also defines how to regulate the transmission of stream elements... the client subscribes to the Flux and then processes the events based on its demand."

RU:
> "В Reactive Streams backpressure также определяет, как регулировать передачу элементов потока... клиент подписывается на Flux, а затем обрабатывает события на основе своего спроса."

Итог: то, что видно в коде как оператор `.limitRate(100)`, на другом конце цепочки прямо влияет на то, будет ли сервер читать TCP-сокет клиента или держать его окно закрытым — это один сквозной механизм, а не два независимых.
