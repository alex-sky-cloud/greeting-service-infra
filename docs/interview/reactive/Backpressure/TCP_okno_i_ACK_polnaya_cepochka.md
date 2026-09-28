# TCP: полная цепочка событий Window + ACK Number

## Оглавление

- [1. Два разных поля в одном сегменте](#1-два-разных-поля-в-одном-сегменте)
- [2. Итоговая цепочка событий (уточнённая)](#2-итоговая-цепочка-событий-уточнённая)
- [3. Ключевое разграничение: за что отвечает Window](#3-ключевое-разграничение-за-что-отвечает-window)
- [4. Ключевое разграничение: за что отвечает ACK Number](#4-ключевое-разграничение-за-что-отвечает-ack-number)
- [5. Формула двух границ окна](#5-формула-двух-границ-окна)
- [6. Мнемоника](#6-мнемоника)

---

## 1. Два разных поля в одном сегменте

Прежде чем читать цепочку событий, нужно закрепить: в каждом TCP-сегменте одновременно едут два *независимых* поля — **Acknowledgment Number** (32 бита) и **Window Size** (16 бит). Первое отвечает за очистку send buffer у отправителя, второе — только за то, сколько новых данных можно слать. Путать их — источник почти всех ошибок в понимании flow control.

- Источник: https://blog.codefarm.me/tcp-ip-tcp-data-flow-and-window-management/

> "Because every TCP segment contains both an ACK number and a window advertisement, a TCP sender adjusts the window structure based on both values whenever an incoming segment arrives. The left edge of the window cannot move to the left, because this edge is controlled by the ACK number received from the other end that is cumulative and never goes backward."

RU:

> «Поскольку каждый TCP-сегмент содержит и номер ACK, и объявление окна, отправитель TCP корректирует структуру окна на основе обоих значений при получении входящего сегмента. Левый край окна не может двигаться влево, потому что этот край управляется номером ACK, полученным от другой стороны — а он кумулятивный и никогда не идёт назад.»

---

_**Октет**_ — это единица данных ровно из 8 бит, синоним байта

---

## 2. Итоговая цепочка событий (уточнённая)

1. **БД (отправитель)** пишет данные через `send()` — они уходят в сеть и попадают в **TCP receive buffer** на стороне вашего приложения (уровень ОС).

2. **Приложение** (модель «поток на запрос») медленно читает из receive buffer, потому что поток занят обработкой — буфер заполняется.

3. **Получатель шлёт ACK**, в котором едут сразу два поля:
   - **Window Size** — актуальный размер *свободного места* в receive buffer (не индикатор «готовности приложения обработать», а именно счётчик свободных байт);
   - **ACK Number** — номер, до которого все байты точно доставлены и могут быть вычищены из send buffer отправителя.

   Когда место в receive buffer кончается, Window = 0.

   - Источник: https://blog.codefarm.me/tcp-ip-tcp-data-flow-and-window-management/

   > "The Window Size field in each TCP header indicates the amount of empty space, in bytes, remaining in the receive buffer... The Window Size field contains a byte offset relative to the ACK number."

   RU:

   > «Поле Window Size в каждом TCP-заголовке указывает объём свободного места, в байтах, оставшегося в приёмном буфере... Поле Window Size содержит смещение в байтах относительно номера ACK.»

4. **Отправитель видит Window = 0**, прекращает слать новые сегменты данных (но не разрывает соединение), периодически шлёт **window probe** — специальный пробный сегмент с одним октетом данных.

   - Источник: https://superuser.com/questions/1728394/tcp-window-probe

   > "The sending TCP must be prepared to accept from the user and send at least one octet of new data even if the send window is zero... there is no special packet format, or header, or other identifier for a window probe packet."

   RU:

   > «Отправляющий TCP должен быть готов принять от пользователя и отправить хотя бы один октет новых данных, даже если окно отправки равно нулю... у window-probe пакета нет отдельного формата, заголовка или идентификатора.»

5. Параллельно с этим, **уже отправленные, но неподтверждённые данные** продолжают лежать в send buffer отправителя — независимо от Window. Они очищаются оттуда только когда приходит сегмент с новым, увеличенным **ACK Number**.

   - Источник: https://stackoverflow.com/questions/14241235/what-happens-when-i-write-data-to-a-blocking-socket-faster-than-the-other-side

   > "Data is cleared from the buffer when the remote side acknowledges it. This is an OS thing and is not dependent upon the remote application actually reading the data."

   RU:

   > «Данные удаляются из буфера, когда удалённая сторона их подтверждает. Это происходит на уровне ОС и не зависит от того, читает ли удалённое приложение данные на самом деле.»

6. **Как только приложение освобождает место в буфере** (дочитывает данные), очередной ACK от получателя приходит с **Window ≠ 0** (и, как правило, с продвинувшимся ACK Number, если новые байты параллельно подтверждались).

7. **Отправитель получает этот ACK**, видит ненулевое окно и возобновляет отправку обычных сегментов данных в receive buffer. Отправленные ранее байты, подтверждённые новым ACK Number, к этому моменту уже вычищены из send buffer — освобождая там место для новых данных.

---

## 3. Ключевое разграничение: за что отвечает Window

Window — это **не сигнал «да/нет»**, а числовое поле в 16 бит (0–65535 байт, а с опцией Window Scaling — значительно больше). Оно отвечает только за **правую границу** окна: сколько ещё новых данных можно отправить, не дожидаясь подтверждения.

- Источник: https://blog.codefarm.me/tcp-ip-tcp-data-flow-and-window-management/

> "The window opens when the right edge moves to the right, allowing more data to be sent. This happens when the receiving process on the other end reads acknowledged data, freeing up space in its TCP receive buffer."

RU:

> «Окно "открывается", когда правый край сдвигается вправо, позволяя отправить больше данных. Это происходит, когда принимающий процесс на другой стороне читает подтверждённые данные, освобождая место в своём TCP receive buffer.»

---

## 4. Ключевое разграничение: за что отвечает ACK Number

ACK Number отвечает за **левую границу** окна: он двигает её вправо каждый раз, когда получатель подтверждает очередную порцию байт. Именно это поле — а не Window — служит сигналом для send buffer «можно очистить эти байты, они точно доставлены».

- Источник: https://blog.codefarm.me/tcp-ip-tcp-data-flow-and-window-management/

> "If the left edge reaches the right edge, it is called a zero window. This stops the sender from transmitting any data."

RU:

> «Если левый край достигает правого края, это называется zero window. Это останавливает отправителя от передачи любых данных.»

---

## 5. Формула двух границ окна

| Граница | Что двигает | За что отвечает |
|---|---|---|
| Левая | Новый, больший **ACK Number** | Очистка send buffer у отправителя (какие байты точно доставлены) |
| Правая | Актуальное значение **Window Size** | Сколько новых данных можно слать без ожидания подтверждения |

Когда левая граница «догоняет» правую — это и есть **zero window**: отправитель физически не может послать ни байта новых данных, пока получатель не прочитает часть буфера и не пришлёт ACK с бóльшим Window.

---

## 6. Мнемоника

- **Window** = «сколько места осталось в баке» (правый край, обновляется по мере вычитывания приложением).
- **ACK Number** = «до какого номера письма точно дошли и приняты» (левый край, двигается только вперёд, никогда назад).
- **Zero Window** = «бак полон, левый край догнал правый» — отправитель стоит и стучится window probe'ами, пока получатель не разгрузит бак.
