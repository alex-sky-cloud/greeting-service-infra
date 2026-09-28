# limitRate vs limitRequest в Project Reactor

## Оглавление

- [1. Итог: кто перепутан](#1-итог-кто-перепутан)
- [2. limitRequest(N) — общий потолок спроса](#2-limitrequestn--общий-потолок-спроса)
- [3. limitRate(N) — дозирование запроса порциями](#3-limitraten--дозирование-запроса-порциями)
- [4. Как это связано с prefetch и replenishing](#4-как-это-связано-с-prefetch-и-replenishing)
- [5. Мнемоника для запоминания](#5-мнемоника-для-запоминания)

---

## 1. Итог: кто перепутан

Да, в вашей формулировке `limitRequest` и `limitRate` были перепутаны местами. То, что вы описали как «дай мне данные заданными порциями», по официальной документации относится к `limitRate`, а не к `limitRequest`. Ниже — точные цитаты из первоисточника, подтверждающие каждое утверждение.

---

## 2. limitRequest(N) — общий потолок спроса

**Утверждение:** `limitRequest(N)` ограничивает суммарный спрос сверху — сколько бы раз подписчик ни вызывал `request()`, в сумме издателю никогда не будет запрошено больше N элементов. После того как N элементов эмитировано, поток завершается сам (`onComplete`), а источник отменяется.

- Источник: https://docs.spring.io/projectreactor/reactor-core/docs/3.7.0-M3/reference/html/coreFeatures/simple-ways-to-create-a-flux-or-mono-and-subscribe-to-it.html

EN:

> "`limitRequest(N)`, on the other hand, **caps** the downstream request to a maximum total demand. It adds up requests up to `N`. If a single `request` does not make the total demand overflow over `N`, that particular request is wholly propagated upstream. After that amount has been emitted by the source, `limitRequest` considers the sequence complete, sends an `onComplete` signal downstream, and cancels the source."

RU:

> «`limitRequest(N)`, в свою очередь, **ограничивает** запрос от подписчика максимальным суммарным спросом. Он суммирует запросы до значения `N`. Если отдельный `request` не приводит к превышению общего спроса над `N`, этот конкретный запрос целиком передаётся вверх по цепочке. После того как источник эмитировал это количество элементов, `limitRequest` считает последовательность завершённой, отправляет сигнал `onComplete` вниз по цепочке и отменяет источник.»

Иными словами, это аналог `take(N)` — «возьми только первые N и хватит», а не про порционную выдачу.

---

## 3. limitRate(N) — дозирование запроса порциями

**Утверждение:** `limitRate(N)` не ограничивает общее количество элементов, а разбивает крупные запросы подписчика на более мелкие партии перед тем, как передать их вверх к издателю. Например, если подписчик запросил 100 элементов, а установлен `limitRate(10)`, вверх будут уходить запросы по 10 штук за раз.

- Источник: https://docs.spring.io/projectreactor/reactor-core/docs/3.7.0-M3/reference/html/coreFeatures/simple-ways-to-create-a-flux-or-mono-and-subscribe-to-it.html

EN:

> "`limitRate(N)` splits the downstream requests so that they are propagated upstream in smaller batches. For instance, a request of `100` made to `limitRate(10)` would result in, at most, `10` requests of `10` being propagated to the upstream. Note that, in this form, `limitRate` actually implements the replenishing optimization discussed earlier."

RU:

> «`limitRate(N)` разбивает запросы от подписчика так, что они передаются вверх по цепочке более мелкими партиями. Например, запрос в `100`, сделанный к `limitRate(10)`, приведёт к тому, что вверх будет передано не более `10` запросов по `10`. Обратите внимание, что в этой форме `limitRate` фактически реализует оптимизацию с подкачкой (replenishing), описанную ранее.»

Именно это и есть «дай мне данные заданными порциями» — ровно ваша формулировка, но она относится к `limitRate`, а не к `limitRequest`.

---

## 4. Как это связано с prefetch и replenishing

Документация также поясняет, что у `limitRate` есть вариант с настройкой размера подкачки (`lowTide`), и что без этой настройки применяется эвристика «выполнено 75% партии → запросить ещё 75% сверху» — тот же принцип, что используют операторы с параметром `prefetch` (например, `flatMap`).

- Источник: https://docs.spring.io/projectreactor/reactor-core/docs/3.7.0-M3/reference/html/coreFeatures/simple-ways-to-create-a-flux-or-mono-and-subscribe-to-it.html

EN:

> "The operator has a variant that also lets you tune the replenishing amount (referred to as the `lowTide` in the variant): `limitRate(highTide, lowTide)`. Choosing a `lowTide` of `0` results in **strict** batches of `highTide` requests, instead of batches further reworked by the replenishing strategy."

RU:

> «У оператора есть вариант, позволяющий настроить объём подкачки (называемый `lowTide` в этом варианте): `limitRate(highTide, lowTide)`. Выбор `lowTide`, равного `0`, приводит к **строгим** партиям размером `highTide` запросов, вместо партий, дополнительно скорректированных стратегией подкачки.»

---

## 5. Мнемоника для запоминания

| Оператор | Что делает | Аналогия |
|---|---|---|
| `limitRequest(N)` | Ограничивает **общий** спрос за весь поток; после N элементов — `onComplete` | Касса с лимитом покупок на клиента |
| `limitRate(N)` | Дозирует **скорость** подкачки: большие запросы режутся на партии по N | Повар просит продукты не мешком, а маленькими партиями |

- Источник: https://docs.spring.io/projectreactor/reactor-core/docs/3.7.0-M3/reference/html/coreFeatures/simple-ways-to-create-a-flux-or-mono-and-subscribe-to-it.html
