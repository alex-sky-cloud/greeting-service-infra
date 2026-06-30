# Webhook-driven event integration: аргументы в пользу подхода

> Документ отвечает на вопрос: почему предложение интегрироваться через webhook лучше для обеих сторон, чем классический polling. Здесь собраны технические, архитектурные и бизнесовые аргументы — со ссылками на авторитетные источники и цитатами.

---

## Оглавление

1. [Что происходит в нашем сценарии](#что-происходит-в-нашем-сценарии)
2. [Возражение вендора и почему оно частично справедливо](#возражение-вендора-и-почему-оно-частично-справедливо)
3. [Аргументы в пользу webhook](#аргументы-в-пользу-webhook)
4. [Как снять архитектурное возражение полностью](#как-снять-архитектурное-возражение-полностью)
5. [Итоговая таблица аргументов](#итоговая-таблица-аргументов)
6. [Формулировка для переговоров](#формулировка-для-переговоров)

---

## Что происходит в нашем сценарии

Схема интеграции выглядит следующим образом:

1. У вендора появляются свежие данные — наступает событие.
2. Вендор делает HTTP POST на наш endpoint — **push-уведомление**.
3. Наш сервис получает сигнал: «данные обновились».
4. Наш сервис делает вызов в API вендора и забирает актуальные данные — **pull**.

Это классическая схема **push-to-notify, pull-to-fetch**: webhook работает как сигнал о событии, а не как транспорт всей бизнес-нагрузки. Такой подход хорошо описан у [ByteByteGo](https://bytebytego.com/guides/polling-vs-webhooks/) и [Authgear](https://www.authgear.com/post/webhooks-vs-apis-difference/):

> *«Webhooks are like having a built-in notification system. You don't continuously ask for information. Instead you create an endpoint in your application server and provide it as a callback to the external service. Every time something interesting happens, the external service calls the endpoint and provides the information»*
> — [ByteByteGo, Polling vs Webhooks](https://bytebytego.com/guides/polling-vs-webhooks/)

> Перевод: «Webhook — это встроенная система уведомлений. Вы не опрашиваете сервис непрерывно. Вместо этого вы регистрируете endpoint как callback у внешнего сервиса. Каждый раз, когда происходит что-то интересное, сервис сам вызывает ваш endpoint и передаёт информацию.»

---

## Возражение вендора и почему оно частично справедливо

Вендор апеллирует к **loose coupling**: источник данных не должен знать о своих потребителях. Если он начнёт поддерживать webhook для каждого клиента, ему придётся:

- хранить и управлять endpoint URL каждого подписчика;
- обеспечивать retry при недоступности endpoint;
- следить за версионированием payload;
- нести ответственность за надёжность доставки.

Это реальная инфраструктурная нагрузка, и возражение честное. Но — и это важно — **это не аргумент против webhook как паттерна**, это аргумент против **плохо реализованного** webhook. Ниже объясняем, как убрать эту нагрузку с вендора.

---

## Аргументы в пользу webhook

### 1. Webhook снижает нагрузку на API самого вендора

При polling клиент вынужден делать запросы **постоянно**, независимо от того, изменились данные или нет. При большом числе клиентов это превращается в тысячи холостых запросов в минуту к API вендора.

[Authgear](https://www.authgear.com/post/webhooks-vs-apis-difference/) прямо формулирует это так:

> *«Webhooks send data exactly once when an event fires. Polling sends continuous requests regardless of whether anything has changed, which wastes bandwidth and server resources»*
> — [Authgear, Webhook vs API](https://www.authgear.com/post/webhooks-vs-apis-difference/)

> Перевод: «Webhook отправляет данные ровно один раз — когда произошло событие. Polling отправляет непрерывные запросы вне зависимости от того, изменилось ли что-либо, что расходует полосу пропускания и серверные ресурсы.»

Таким образом webhook **выгоден самому вендору**: его API перестаёт обрабатывать лавину пустых запросов.

---

### 2. Свежесть данных — near real-time вместо задержки на интервал опроса

При polling задержка между событием и получением данных равна минимум одному интервалу опроса. Webhook устраняет эту задержку: уведомление приходит практически сразу после события.

[Design Gurus](https://designgurus.substack.com/p/polling-vs-webhooks-explained-with) характеризуют разницу следующим образом:

> *«Polling is client-driven (pulling updates on a schedule) and webhooks are server-driven (pushing updates in real-time)»*
> — [Design Gurus, Polling vs Webhooks Explained](https://designgurus.substack.com/p/polling-vs-webhooks-explained-with)

> Перевод: «Polling инициируется клиентом (по расписанию), webhook — сервером (в реальном времени).»

---

### 3. Webhook не создаёт coupling — это распространённое заблуждение

Ключевой аргумент против возражения вендора. Webhook — это **HTTP POST на зарегистрированный URL**. Вендор не знает, что стоит за этим URL: наш сервис, прокси, очередь или брокер. Он просто отправляет запрос и получает `200 OK`. Это **не coupling** — coupling возник бы, если бы вендор знал нашу доменную модель или зависел от нашей схемы данных.

[Svix](https://www.svix.com/resources/faq/webhooks-vs-long-polling/) описывает это так:

> *«Webhooks are user-defined HTTP callbacks, which are triggered by specific events in a server or service. When the event occurs, the server makes an HTTP request to the URL configured for the webhook»*
> — [Svix, Webhooks vs Long Polling](https://www.svix.com/resources/faq/webhooks-vs-long-polling/)

> Перевод: «Webhook — это пользовательский HTTP callback, который срабатывает при конкретных событиях на стороне сервера. Когда событие происходит, сервер делает HTTP-запрос на заранее настроенный URL.»

То есть со стороны вендора это просто **отправить письмо на адрес**. Ничего более.

---

### 4. Это отраслевой стандарт, а не экзотика

Webhook-интеграции использует любой крупный SaaS: **Stripe**, **GitHub**, **Slack**, **Twilio**, **Shopify**, **PayPal**. Это не нестандартное требование — это де-факто индустриальная практика.

[Apideck](https://www.apideck.com/blog/what-is-a-webhook) перечисляет реальные примеры:

> *«Real-world examples from Stripe, GitHub, Slack and more show how webhooks power real-time integrations across the industry»*
> — [Apideck, What Is a Webhook?](https://www.apideck.com/blog/what-is-a-webhook)

> Перевод: «Реальные примеры от Stripe, GitHub, Slack и других показывают, как webhook обеспечивает интеграцию в реальном времени в рамках всей индустрии.»

Если вендор говорит, что webhook «неправилен архитектурно» — он идёт вразрез с тем, что делают крупнейшие платформы.

---

### 5. Надёжность endpoint — наша ответственность, не вендора

Если вендор опасается брать на себя retry и гарантии доставки, это возражение снимается просто: мы сами обеспечиваем надёжность нашего endpoint.

Рекомендуемая схема описана у [Enterprise Webhook Architecture](https://www.shambix.com/enterprise-webhook-architecture-building-systems-scale/):

> *«Receiving a webhook is not the same as processing it. You should split these into two separate operations. First operation: receive the webhook, validate the signature, write the raw payload to a queue, return 200 OK immediately. Second operation: a worker process consumes the queue»*
> — [Shambix, Enterprise Webhook Architecture](https://www.shambix.com/enterprise-webhook-architecture-building-systems-scale/)

> Перевод: «Получение webhook — это не то же самое, что его обработка. Нужно разделить на две операции. Первая: принять webhook, проверить подпись, записать payload в очередь, вернуть 200 OK немедленно. Вторая: воркер обрабатывает очередь.»

То есть модель для вендора выглядит так: **fire and forget** — отправил, получил `200 OK`, забыл. Всё остальное — наша зона ответственности.

---

### 6. Нет холостой обработки на нашей стороне

При polling мы не только создаём лишний трафик, мы ещё и **обрабатываем пустые ответы**: парсим, сравниваем с предыдущим состоянием, делаем вывод «ничего не изменилось». Это лишняя CPU-нагрузка, лишний код, лишние тесты. Webhook устраняет весь этот слой: если событие пришло — оно реально произошло.

---

## Как снять архитектурное возражение полностью

Если вендора всё равно беспокоит то, что его система «знает» наш URL, есть изящное решение: **message broker как посредник**.

```
Вендор  →  Kafka / RabbitMQ / AWS EventBridge / SNS  →  наш сервис
```

В этом случае:

- вендор публикует событие в нейтральный брокер, не зная, кто его читает;
- мы подписываемся сами;
- coupling отсутствует полностью;
- вся экономика webhook-подхода сохраняется.

[CodeOpinion](https://codeopinion.com/building-a-webhooks-system-with-event-driven-architecture/) описывает именно такую модель:

> *«Using an event driven architecture and messaging can facilitate building a webhooks system that can be very robust, fault-tolerant, resilient, and decoupled»*
> — [CodeOpinion, Building a Webhooks System with Event Driven Architecture](https://codeopinion.com/building-a-webhooks-system-with-event-driven-architecture/)

> Перевод: «Использование event-driven архитектуры и брокера сообщений позволяет построить систему webhook, которая будет устойчивой, отказоустойчивой и слабосвязанной.»

---

## Итоговая таблица аргументов

| Аргумент | Кому выгодно |
|---|---|
| Снижение нагрузки на API вендора — нет холостых запросов | Вендору |
| Данные в near real-time, нет задержки интервала опроса | Нам |
| Нет нагрузки на нашу инфраструктуру от постоянного polling | Нам |
| Webhook — это просто HTTP POST, нет coupling на бизнес-модель | Вендору |
| Отраслевой стандарт: Stripe, GitHub, Slack, Twilio | Обоим |
| Надёжность endpoint — наша зона, вендор делает fire and forget | Вендору |
| Нет холостой обработки пустых ответов на нашей стороне | Нам |
| Брокер как посредник полностью убирает coupling при желании | Вендору |

---

## Формулировка для переговоров

Если нужна одна формулировка для разговора с вендором:

> «Ваше возражение про coupling мы слышим. Но webhook не означает, что вы зависите от нас: вы просто отправляете HTTP POST на URL и получаете `200 OK`. Что стоит за этим URL — наш сервис, очередь или брокер — вас не касается. Это fire and forget. Надёжность нашего endpoint — наша ответственность, не ваша. При этом ваш API перестаёт получать тысячи холостых polling-запросов от наших систем — что прямо снижает нагрузку на вашу инфраструктуру. Если coupling всё равно беспокоит — поставим брокер посередине, и вы вообще не будете знать, кто вас читает.»

