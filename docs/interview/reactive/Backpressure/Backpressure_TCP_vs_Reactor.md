# Backpressure: от TCP до Project Reactor

## Оглавление

- [1. Subscription как мост между Publisher и Subscriber](#1-subscription-как-мост-между-publisher-и-subscriber)
- [2. Backpressure в синхронной модели (TCP)](#2-backpressure-в-синхронной-модели-tcp)
- [3. Кто задаёт размер TCP-окна](#3-кто-задаёт-размер-tcp-окна)
- [4. Порционность окна и Silly Window Syndrome](#4-порционность-окна-и-silly-window-syndrome)
- [5. Сравнение TCP и Reactive Streams](#5-сравнение-tcp-и-reactive-streams)
- [6. Императивный vs реактивный стиль](#6-императивный-vs-реактивный-стиль)

---

## 1. Subscription как мост между Publisher и Subscriber

**Утверждение:**
`Subscription` — это объект, который появляется в результате подписки `Subscriber` на `Publisher`, и через него подписчик управляет потоком: запрашивает элементы (`request(n)`) или отменяет подписку (`cancel()`). Это не «переносчик данных», а канал управления и backpressure между источником и потребителем.

**Источник:** https://projectreactor.io/docs/core/release/reference/reactiveProgramming.html

> "By the act of subscribing, you tie the Publisher to a Subscriber, which triggers the flow of data in the whole chain. This is achieved internally by a single request signal from the Subscriber that is propagated upstream, all the way back to the source Publisher."

RU:
> "Актом подписки вы связываете Publisher с Subscriber, что запускает поток данных по всей цепочке. Это достигается внутренне единственным сигналом request от Subscriber, который распространяется вверх по цепочке, вплоть до исходного Publisher."

**Источник:** https://projectreactor.io/docs/core/release/reference/reactiveProgramming.html

> "A subscriber can work in unbounded mode and let the source push all the data at its fastest achievable rate or it can use the request mechanism to signal the source that it is ready to process at most n elements."

RU:
> "Подписчик может работать в неограниченном режиме и позволить источнику проталкивать все данные с максимально возможной скоростью, либо он может использовать механизм request, чтобы сигнализировать источнику о готовности обработать максимум n элементов."

---

## 2. Backpressure в синхронной модели (TCP)

**Утверждение:**
В синхронной модели «поток на запрос» backpressure реализован не в коде приложения, а на уровне транспортного протокола — через TCP receive window (rwnd). Приёмник анонсирует, сколько байт он готов принять без подтверждения, и отправитель не может слать больше этого объёма.

**Источник:** https://system-design.space/en/chapter/backpressure-flow-control/

> "In TCP the receiver advertises rwnd in every segment — how many bytes it is still ready to accept. The sender may not keep more than rwnd of unacknowledged data 'in flight.' This is window-based flow control, akin to a credit scheme: when the receiver buffer fills up, its window zeroes and stops the sender."

RU:
> "В TCP приёмник анонсирует rwnd в каждом сегменте — сколько байт он ещё готов принять. Отправитель не может держать 'в полёте' больше rwnd неподтверждённых данных. Это оконный флоу-контроль, похожий на кредитную схему: когда буфер приёмника заполняется, его окно обнуляется и останавливает отправителя."

**Утверждение:**
Когда окно достигает нуля (zero window), отправитель не «стучится» бесконечно, а переходит в специальный режим — периодически отправляет крошечный probe-пакет (persist timer), чтобы проверить статус окна.

**Источник:** https://blog.codefarm.me/tcp-ip-tcp-data-flow-and-window-management/

> "Zero Windows and the TCP Persist Timer"

RU (пересказ по смыслу раздела):
При обнулении окна отправитель запускает таймер persist и с определёнными интервалами шлёт зондирующий пакет (1 байт), чтобы узнать, не освободилось ли место у получателя; как только место появляется, получатель сам присылает обновление окна.

---

## 3. Кто задаёт размер TCP-окна

**Утверждение:**
Размер TCP-окна не фиксирован для всех приложений — он зависит от размера буфера приёма конкретного сокета, который управляется автотюнингом ядра ОС в зависимости от скорости чтения приложением и доступной памяти. Приложение может переопределить это вручную через `SO_RCVBUF`.

**Источник:** https://learn.microsoft.com/en-us/troubleshoot/windows-server/networking/description-tcp-features

> "The TCP receive window size is the amount of receive data (in bytes) that can be buffered during a connection. The sending host can send only that amount of data before it must wait for an acknowledgment and window update from the receiving host."

RU:
> "Размер TCP receive window — это объём данных приёма (в байтах), которые могут быть буферизованы во время соединения. Отправляющий хост может послать лишь такой объём данных, после чего должен ждать подтверждения и обновления окна от принимающего хоста."

**Утверждение:**
Изначально поле размера окна в заголовке TCP занимает 16 бит, что ограничивает окно значением 65535 байт. Чтобы обойти это ограничение, используется опция Window Scale (RFC 1323 / RFC 7323), позволяющая расширить окно до 1 ГиБ.

**Источник:** https://www.rfc-editor.org/info/rfc1323/

> "The TCP header uses a 16 bit field to report the receive window size to the sender. Therefore, the largest window that can be used is 2**16 = 65K bytes. To circumvent this problem, Section 2 of this memo defines a new TCP option, 'Window Scale,' to allow windows larger than 2**16."

RU:
> "Заголовок TCP использует 16-битное поле для сообщения размера receive window отправителю. Поэтому наибольшее возможное окно — 2^16 = 65K байт. Чтобы обойти эту проблему, раздел 2 этого документа определяет новую опцию TCP 'Window Scale', позволяющую использовать окна больше 2^16."

---

## 4. Порционность окна и Silly Window Syndrome

**Утверждение:**
Получатель не анонсирует любое минимальное освобождение буфера — иначе возникает Silly Window Syndrome (синдром глупого окна), при котором отправитель шлёт крошечные неэффективные сегменты. Решение (алгоритм Кларка): не анонсировать увеличение окна, пока не освободится либо целый MSS, либо половина буфера.

**Источник:** https://en.wikipedia.org/wiki/Silly_window_syndrome

> "Clark's solution closes the window until another segment of maximum segment size (MSS) can be received or the buffer is half empty."

RU:
> "Решение Кларка держит окно закрытым, пока не появится возможность принять ещё один сегмент максимального размера (MSS) либо пока буфер не опустеет наполовину."

**Утверждение:**
Причина синдрома в том, что получатель продвигает правую границу окна при появлении любого свободного места, а отправитель использует любое приращение окна, каким бы малым оно ни было, чтобы отправить ещё данные.

**Источник:** https://www.freesoft.org/CIE/RFC/1122/100.htm

> "SWS is caused by the receiver advancing the right window edge whenever it has any new buffer space available to receive data and by the sender using any incremental window, no matter how small, to send more data."

RU:
> "SWS вызывается тем, что получатель продвигает правую границу окна при появлении любого нового свободного места в буфере, а отправитель использует любое приращение окна, каким бы малым оно ни было, чтобы отправить больше данных."

---

## 5. Сравнение TCP и Reactive Streams

**Утверждение:**
И TCP, и Reactive Streams используют одну и ту же по сути кредитную схему — приёмник выдаёт «кредит» на объём данных, а источник не может его превысить. Разница в единице измерения: в TCP это байты, в Reactive Streams — элементы потока.

**Источник:** https://system-design.space/en/chapter/backpressure-flow-control/

> "The consumer issues the producer a 'credit': a request for N items. The producer sends no more than N and waits for a new credit. This is exactly request(n) — demand drives the flow, and the queue never grows faster than it is drained."

RU:
> "Потребитель выдаёт производителю 'кредит': запрос на N элементов. Производитель не отправляет больше N и ждёт нового кредита. Это именно request(n) — спрос управляет потоком, и очередь никогда не растёт быстрее, чем она опустошается."

**Утверждение:**
Reactive Streams формализует эту схему через четыре интерфейса: Publisher, Subscriber, Subscription и Processor, где ключевой метод — `Subscription.request(long n)`.

**Источник:** https://system-design.space/en/chapter/backpressure-flow-control/

> "The Reactive Streams specification... formalizes the credit scheme through four interfaces. Publisher produces elements on subscriber demand, Subscriber receives them, Subscription mediates one publisher-subscriber pair, and Processor combines both roles in the middle of a pipeline."

RU:
> "Спецификация Reactive Streams... формализует кредитную схему через четыре интерфейса. Publisher производит элементы по запросу подписчика, Subscriber их получает, Subscription опосредует одну пару publisher-subscriber, а Processor совмещает обе роли в середине конвейера."

### Таблица сравнения

| Аспект | TCP (транспортный уровень) | Reactive Streams (`request(n)`) |
|---|---|---|
| Единица измерения | Байты | Количество элементов |
| Кто инициирует | Приёмник анонсирует окно в каждом ACK | Подписчик явно вызывает `request(n)` |
| Порционность | Да — MSS или половина буфера (правило Кларка) | Да — ровно столько, сколько запрошено |
| Полная остановка | Zero window + persist timer (пробники) | Отсутствие вызова `request` — источник просто не шлёт `onNext` |
| Уровень реализации | Ядро ОС, автоматически | Явный код приложения |

---

## 6. Императивный vs реактивный стиль

**Утверждение:**
В реактивном программировании данные проталкиваются (push) от Publisher к Subscriber, в отличие от императивного использования итератора, где именно потребитель тянет (pull) данные вызовом `next()`. Reactive Streams превращает эту чистую push-модель в гибрид push-pull за счёт `request(n)`.

**Источник:** https://projectreactor.io/docs/core/release/reference/reactiveProgramming.html

> "Using an iterator is an imperative programming pattern... it is up to the developer to choose when to access the next() item in the sequence... it is the Publisher that notifies the Subscriber of newly available values as they come, and this push aspect is the key to being reactive."

RU:
> "Использование итератора — это императивный паттерн программирования... разработчик сам решает, когда обращаться к следующему элементу через next()... именно Publisher уведомляет Subscriber о новых доступных значениях по мере их появления, и этот аспект проталкивания (push) — ключ к реактивности."

**Утверждение:**
Итоговая модель Reactive Streams — это push-pull гибрид: подписчик может «вытянуть» n элементов, если они уже готовы, но если элементы ещё не готовы, они проталкиваются по мере производства.

**Источник:** https://projectreactor.io/docs/core/release/reference/reactiveProgramming.html

> "This transforms the push model into a push-pull hybrid, where the downstream can pull n elements from upstream if they are readily available. But if the elements are not ready, they get pushed by the upstream whenever they are produced."

RU:
> "Это превращает push-модель в push-pull гибрид, где нижестоящий участник может вытянуть n элементов из вышестоящего, если они уже доступны. Но если элементы не готовы, они проталкиваются вышестоящим участником по мере производства."
