
## Что такое `interest set`

`interest set` — это **набор событий**, которые приложение просит **Selector** отслеживать для конкретного **Channel**. 
- Он хранится в объекте `SelectionKey`, который создаётся при регистрации **Channel** в **Selector**.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectableChannel.html

EN:

> “In order to be used with a selector, an instance of this class must first be registered via the `register` method. This method returns a new `SelectionKey` object.”

RU:

> «Чтобы использовать экземпляр этого класса с selector, его сначала необходимо зарегистрировать через метод `register`. Этот метод возвращает новый объект `SelectionKey`.»

То есть `SelectionKey` — это **карточка регистрации** конкретного **Channel** в конкретном **Selector**.

## Как выглядит регистрация

Упрощённо регистрация выглядит так:

```java
SelectionKey key = channel.register(
    selector,
    SelectionKey.OP_READ
);
```

Здесь `OP_READ` — **interest set:** «следи за этим **Channel** и сообщи, когда из него можно читать».

Сам `interest set` определяет, какие операции Selector должен проверять при следующем вызове `select()`.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectionKey.html

EN:

> “The interest set determines which operation categories will be tested for readiness the next time one of the selector's selection methods is invoked.”

RU:

> «Набор интересов определяет, какие категории операций будут проверяться на готовность при следующем вызове одного из методов выбора selector.»

## Какие события бывают

`interest set` — это число, составленное из флагов:

- `OP_ACCEPT` — сообщить, когда серверный Channel может принять новое подключение.
- `OP_READ` — сообщить, когда Channel готов к чтению.
- `OP_WRITE` — сообщить, когда Channel готов к записи.
- `OP_CONNECT` — сообщить, когда клиентский Channel завершил подключение.

Например, для server socket обычно указывают `OP_ACCEPT`, а для client socket после принятия соединения — `OP_READ`.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectionKey.html

EN:

> “Operation-set bit for socket-accept operations.”

RU:

> «Бит набора операций для операций принятия сокетного подключения.»

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectionKey.html

EN:

> “Operation-set bit for read operations.”

RU:

> «Бит набора операций для операций чтения.»

## `interest set` и `ready set`

Это разные наборы внутри одного `SelectionKey`.


| Набор | Кто задаёт | Смысл |
| :-- | :-- | :-- |
| `interest set` | Приложение / Netty при регистрации | «Какие события мне нужны» |
| `ready set` | Selector после `select()` | «Какие нужные события произошли сейчас» |

Официальная документация прямо различает эти два набора: interest set задаёт, что проверять, а ready set показывает, к каким операциям Channel признан готовым.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectionKey.html

EN:

> “The ready set identifies the operation categories for which the key's channel has been detected to be ready by the key's selector.”

RU:

> «Набор готовности определяет категории операций, к которым канал ключа был обнаружен готовым selector этого ключа.»

## Пример одной итерации

1. Worker event loop уже зарегистрировал client Channel с `OP_READ`.
2. Значит, его `interest set` означает: «следи, появились ли данные для чтения».
3. Клиент отправляет HTTP-запрос.
4. ОС получает байты и сообщает Selector, что Channel готов к чтению.
5. После `select()` ключ этого Channel попадает в `selected-key set`.
6. В его `ready set` будет отмечен `OP_READ`.
7. Netty читает байты из Channel и передаёт их дальше в pipeline.

`selected-key set` содержит только те ключи, чьи каналы были обнаружены готовыми хотя бы к одной операции из их interest set.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html

EN:

> “The selected-key set is the set of keys such that each key's channel was detected to be ready for at least one of the operations identified in the key's interest set.”

RU:

> «Набор выбранных ключей — это набор ключей, чьи каналы были обнаружены готовыми хотя бы к одной из операций, указанных в наборе интересов ключа.»
