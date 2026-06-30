# Mediator на примере чата: три полных варианта реализации

> Документ показывает, как один и тот же учебный пример `ChatRoom` можно реализовать по-разному в зависимости от требований: простой broadcast через `List`, адресация и удаление через `Map`, а также оптимизация через `Map + group/channel`, когда сообщение рассылается не всем, а только участникам конкретной комнаты.

---

## Оглавление

1. [Что здесь показывает паттерн Mediator](#1-что-здесь-показывает-паттерн-mediator)
2. [Вариант 1 — `List` и broadcast всем](#2-вариант-1--list-и-broadcast-всем)
3. [Вариант 2 — `Map` и быстрый доступ по id](#3-вариант-2--map-и-быстрый-доступ-по-id)
4. [Вариант 3 — `Map + group/channel`](#4-вариант-3--map--groupchannel)
5. [Когда какой вариант выбирать](#5-когда-какой-вариант-выбирать)

---

## 1. Что здесь показывает паттерн Mediator

Паттерн **Mediator** вводит центральный объект-посредник, через который компоненты взаимодействуют друг с другом, вместо прямых вызовов между собой. Refactoring.Guru формулирует это так: *«The pattern restricts direct communications between the objects and forces them to collaborate only via a mediator object»* — https://refactoring.guru/design-patterns/mediator

На примере чата это означает простое правило: `ChatUser` **не знает** других `ChatUser` напрямую, а общается только через `ChatRoom`. Именно `ChatRoom` решает, кому передать сообщение, кого зарегистрировать, кого удалить и как организовать маршрутизацию — https://refactoring.guru/design-patterns/mediator и https://en.wikipedia.org/wiki/Mediator_pattern

---

## 2. Вариант 1 — `List` и broadcast всем

Это самый простой и наглядный учебный пример. Он хорошо показывает саму идею Mediator: пользователи знают только посредника, а посредник сам рассылает сообщения всем участникам, кроме отправителя.

### Что важно понять

- Структура простая и хорошо объясняет паттерн.
- Но удаление конкретного пользователя и поиск конкретного адресата здесь неудобны: нужен обход списка.
- Broadcast всем участникам имеет сложность `O(n)` — и это нормально, потому что сообщение действительно нужно доставить всем.

### Полный пример

```java
import java.util.ArrayList;
import java.util.List;

/**
 * ROLE: Mediator
 *
 * <p>Центральный посредник в паттерне Mediator.</p>
 * <p>Все участники чата общаются только через {@code ChatRoom}.
 * Пользователи не знают друг друга напрямую.</p>
 */
class ChatRoom {

    /**
     * Список всех участников, известных посреднику.
     * <p>Mediator хранит ссылки на всех коллег,
     * чтобы управлять их взаимодействием.</p>
     */
    private final List<ChatUser> users = new ArrayList<>();

    /**
     * Регистрирует участника в посреднике.
     *
     * @param user новый участник чата
     */
    public void join(ChatUser user) {
        users.add(user);
    }

    /**
     * Удаляет конкретного участника.
     * <p>В этой версии используется {@link List}, поэтому поиск и удаление
     * конкретного пользователя требуют линейного обхода структуры.</p>
     *
     * @param user участник, которого нужно удалить
     */
    public void leave(ChatUser user) {
        users.remove(user);
    }

    /**
     * Рассылает сообщение всем участникам, кроме отправителя.
     *
     * <p>Отправитель не знает, кто получит сообщение —
     * это решение посредника.</p>
     *
     * <p>Сложность: {@code O(n)}, потому что нужно пройтись
     * по всем участникам списка.</p>
     *
     * @param sender  участник, отправивший сообщение
     * @param message текст сообщения
     */
    public void send(ChatUser sender, String message) {
        for (ChatUser user : users) {
            if (user != sender) {
                user.receive(sender.getName(), message);
            }
        }
    }
}

/**
 * ROLE: Colleague
 *
 * <p>Участник системы, который взаимодействует
 * с другими участниками только через посредника.</p>
 */
class ChatUser {

    private final String name;
    private final ChatRoom room;

    /**
     * @param name имя пользователя
     * @param room посредник ({@link ChatRoom})
     */
    public ChatUser(String name, ChatRoom room) {
        this.name = name;
        this.room = room;
        room.join(this);
    }

    /**
     * Отправляет сообщение через посредника.
     *
     * @param message текст сообщения
     */
    public void send(String message) {
        room.send(this, message);
    }

    /**
     * Покидает чат.
     */
    public void leave() {
        room.leave(this);
    }

    /**
     * Получает сообщение от посредника.
     *
     * @param from    имя отправителя
     * @param message текст сообщения
     */
    public void receive(String from, String message) {
        System.out.println(name + " получил от " + from + ": " + message);
    }

    /**
     * @return имя пользователя
     */
    public String getName() {
        return name;
    }
}

/**
 * ROLE: Client
 *
 * <p>Клиентский код собирает Mediator и его коллег.</p>
 */
public class ChatMediatorListDemo {

    public static void main(String[] args) {
        ChatRoom room = new ChatRoom();

        ChatUser alice = new ChatUser("Alice", room);
        ChatUser bob = new ChatUser("Bob", room);
        ChatUser carol = new ChatUser("Carol", room);

        alice.send("Привет всем!");
        // Bob получил от Alice: Привет всем!
        // Carol получил от Alice: Привет всем!

        bob.leave();
        alice.send("Bob уже вышел из комнаты");
        // Carol получил от Alice: Bob уже вышел из комнаты
    }
}
```

### Пояснение по Mediator

В этом варианте посредник максимально нагляден: `ChatUser` не имеет ссылок на других пользователей и не вызывает их напрямую. Вместо этого каждый `ChatUser` знает только `ChatRoom`, а `ChatRoom` централизует всю коммуникацию, что и соответствует определению Mediator — https://refactoring.guru/design-patterns/mediator и https://en.wikipedia.org/wiki/Mediator_pattern

---

## 3. Вариант 2 — `Map` и быстрый доступ по id

Этот вариант нужен, когда кроме broadcast появляются операции вида «удалить пользователя по id», «найти пользователя по id» или «отправить личное сообщение конкретному адресату». Тогда `Map` даёт заметную пользу: базовые операции поиска и удаления по ключу в `HashMap` обычно имеют среднюю сложность `O(1)` — https://stackoverflow.com/questions/13075233/hashmap-remove-complexity, https://javabypatel.blogspot.com/2015/10/time-complexity-of-hashmap-get-and-put-operation.html, https://www.freecodecamp.org/news/what-is-a-hash-map/

### Что важно понять

- Broadcast всем участникам всё ещё остаётся `O(n)`.
- Но удаление конкретного пользователя и поиск по id теперь работают быстрее.
- Это уже более реалистичный вариант Mediator для прикладной системы.

### Полный пример

```java
import java.util.HashMap;
import java.util.Map;

/**
 * ROLE: Mediator
 *
 * <p>Посредник, который хранит участников по уникальному ключу.</p>
 * <p>Это позволяет быстро находить и удалять пользователя по id,
 * не делая линейный поиск по списку.</p>
 */
class ChatRoom {

    /**
     * Все участники чата, зарегистрированные у посредника.
     * <p>Ключ — уникальный идентификатор пользователя.</p>
     */
    private final Map<String, ChatUser> users = new HashMap<>();

    /**
     * Регистрирует участника.
     *
     * @param user новый участник
     */
    public void join(ChatUser user) {
        users.put(user.getId(), user);
    }

    /**
     * Удаляет конкретного участника по id.
     *
     * <p>Для {@link HashMap} такая операция обычно имеет
     * среднюю сложность {@code O(1)}.</p>
     *
     * @param userId идентификатор пользователя
     */
    public void leave(String userId) {
        users.remove(userId);
    }

    /**
     * Рассылает сообщение всем, кроме отправителя.
     *
     * <p>Несмотря на использование {@link Map}, broadcast всё равно
     * требует обхода всех получателей, поэтому сложность — {@code O(n)}.</p>
     *
     * @param senderId id отправителя
     * @param message  текст сообщения
     */
    public void send(String senderId, String message) {
        ChatUser sender = users.get(senderId);
        if (sender == null) {
            return;
        }

        for (ChatUser user : users.values()) {
            if (!user.getId().equals(senderId)) {
                user.receive(sender.getName(), message);
            }
        }
    }

    /**
     * Отправляет личное сообщение конкретному пользователю.
     *
     * @param senderId   id отправителя
     * @param receiverId id получателя
     * @param message    текст сообщения
     */
    public void sendTo(String senderId, String receiverId, String message) {
        ChatUser sender = users.get(senderId);
        ChatUser receiver = users.get(receiverId);

        if (sender == null || receiver == null) {
            return;
        }

        receiver.receive(sender.getName(), message);
    }
}

/**
 * ROLE: Colleague
 *
 * <p>Участник чата, который знает только посредника.</p>
 */
class ChatUser {

    private final String id;
    private final String name;
    private final ChatRoom room;

    /**
     * @param id   уникальный идентификатор пользователя
     * @param name имя пользователя
     * @param room посредник ({@link ChatRoom})
     */
    public ChatUser(String id, String name, ChatRoom room) {
        this.id = id;
        this.name = name;
        this.room = room;
        room.join(this);
    }

    /**
     * Broadcast-сообщение всем участникам.
     *
     * @param message текст сообщения
     */
    public void send(String message) {
        room.send(id, message);
    }

    /**
     * Личное сообщение конкретному пользователю.
     *
     * @param receiverId id получателя
     * @param message    текст сообщения
     */
    public void sendTo(String receiverId, String message) {
        room.sendTo(id, receiverId, message);
    }

    /**
     * Покидает чат.
     */
    public void leave() {
        room.leave(id);
    }

    /**
     * Получает сообщение от посредника.
     *
     * @param from    имя отправителя
     * @param message текст сообщения
     */
    public void receive(String from, String message) {
        System.out.println(name + " получил от " + from + ": " + message);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

/**
 * ROLE: Client
 */
public class ChatMediatorMapDemo {

    public static void main(String[] args) {
        ChatRoom room = new ChatRoom();

        ChatUser alice = new ChatUser("u1", "Alice", room);
        ChatUser bob = new ChatUser("u2", "Bob", room);
        ChatUser carol = new ChatUser("u3", "Carol", room);

        alice.send("Привет всем!");
        // Bob получил от Alice: Привет всем!
        // Carol получил от Alice: Привет всем!

        alice.sendTo("u2", "Bob, это личное сообщение");
        // Bob получил от Alice: Bob, это личное сообщение

        bob.leave();
        alice.send("Bob удалён по id, без линейного поиска в клиентском коде");
        // Carol получил от Alice: Bob удалён по id, без линейного поиска в клиентском коде
    }
}
```

### Пояснение по Mediator

Здесь паттерн остаётся тем же: `ChatUser` не знает других пользователей и не маршрутизирует сообщения сам. Но посредник становится умнее: он уже не просто рассылает сообщения, а ещё и выполняет адресацию, удаление по ключу и private messaging — https://refactoring.guru/design-patterns/mediator и https://javabypatel.blogspot.com/2015/10/time-complexity-of-hashmap-get-and-put-operation.html

---

## 4. Вариант 3 — `Map + group/channel`

Это более сильная версия примера, когда сообщение нужно отправлять **не всем пользователям системы**, а только участникам конкретной комнаты или канала. Такой вариант хорошо показывает, как Mediator может не только связывать участников, но и управлять маршрутизацией по группам — https://refactoring.guru/design-patterns/mediator

### Что важно понять

- Участники по-прежнему не знают друг друга напрямую.
- Посредник знает пользователей и комнаты.
- Broadcast теперь делается **внутри канала**, а не по всем зарегистрированным пользователям.
- Если в системе много пользователей, а в комнате мало, это заметно эффективнее глобальной рассылки.

### Полный пример

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ROLE: Mediator
 *
 * <p>Посредник, который знает всех пользователей и все каналы.</p>
 * <p>Пользователи не общаются напрямую — они всегда обращаются
 * к посреднику, а он уже решает, кому и в какую комнату доставить сообщение.</p>
 */
class ChatRoom {

    /**
     * Все пользователи системы по id.
     */
    private final Map<String, ChatUser> users = new HashMap<>();

    /**
     * Каналы/комнаты по имени.
     * <p>Каждая комната хранит набор id пользователей.</p>
     */
    private final Map<String, Set<String>> channels = new HashMap<>();

    /**
     * Регистрирует пользователя в системе.
     *
     * @param user новый пользователь
     */
    public void join(ChatUser user) {
        users.put(user.getId(), user);
    }

    /**
     * Удаляет пользователя из системы и из всех комнат.
     *
     * @param userId id пользователя
     */
    public void leave(String userId) {
        users.remove(userId);

        for (Set<String> members : channels.values()) {
            members.remove(userId);
        }
    }

    /**
     * Создаёт канал, если его ещё нет.
     *
     * @param channelName имя канала
     */
    public void createChannel(String channelName) {
        channels.putIfAbsent(channelName, new HashSet<>());
    }

    /**
     * Добавляет пользователя в канал.
     *
     * @param userId      id пользователя
     * @param channelName имя канала
     */
    public void joinChannel(String userId, String channelName) {
        if (!users.containsKey(userId)) {
            return;
        }

        channels.putIfAbsent(channelName, new HashSet<>());
        channels.get(channelName).add(userId);
    }

    /**
     * Удаляет пользователя из канала.
     *
     * @param userId      id пользователя
     * @param channelName имя канала
     */
    public void leaveChannel(String userId, String channelName) {
        Set<String> members = channels.get(channelName);
        if (members != null) {
            members.remove(userId);
        }
    }

    /**
     * Отправляет сообщение всем участникам конкретного канала,
     * кроме отправителя.
     *
     * <p>Сложность зависит уже не от общего числа пользователей,
     * а от числа участников в конкретной комнате.</p>
     *
     * @param senderId    id отправителя
     * @param channelName имя канала
     * @param message     текст сообщения
     */
    public void sendToChannel(String senderId, String channelName, String message) {
        ChatUser sender = users.get(senderId);
        Set<String> members = channels.get(channelName);

        if (sender == null || members == null) {
            return;
        }

        for (String memberId : members) {
            if (!memberId.equals(senderId)) {
                ChatUser receiver = users.get(memberId);
                if (receiver != null) {
                    receiver.receive("#" + channelName + " / " + sender.getName(), message);
                }
            }
        }
    }

    /**
     * Отправляет личное сообщение конкретному пользователю.
     *
     * @param senderId   id отправителя
     * @param receiverId id получателя
     * @param message    текст сообщения
     */
    public void sendPrivate(String senderId, String receiverId, String message) {
        ChatUser sender = users.get(senderId);
        ChatUser receiver = users.get(receiverId);

        if (sender == null || receiver == null) {
            return;
        }

        receiver.receive("private / " + sender.getName(), message);
    }
}

/**
 * ROLE: Colleague
 *
 * <p>Пользователь знает только посредника ({@link ChatRoom})
 * и никогда не общается с другими пользователями напрямую.</p>
 */
class ChatUser {

    private final String id;
    private final String name;
    private final ChatRoom room;

    /**
     * @param id   уникальный id пользователя
     * @param name имя пользователя
     * @param room посредник
     */
    public ChatUser(String id, String name, ChatRoom room) {
        this.id = id;
        this.name = name;
        this.room = room;
        room.join(this);
    }

    /**
     * Входит в канал.
     *
     * @param channelName имя канала
     */
    public void joinChannel(String channelName) {
        room.joinChannel(id, channelName);
    }

    /**
     * Покидает канал.
     *
     * @param channelName имя канала
     */
    public void leaveChannel(String channelName) {
        room.leaveChannel(id, channelName);
    }

    /**
     * Отправляет сообщение в канал.
     *
     * @param channelName имя канала
     * @param message     текст сообщения
     */
    public void sendToChannel(String channelName, String message) {
        room.sendToChannel(id, channelName, message);
    }

    /**
     * Отправляет личное сообщение.
     *
     * @param receiverId id получателя
     * @param message    текст сообщения
     */
    public void sendPrivate(String receiverId, String message) {
        room.sendPrivate(id, receiverId, message);
    }

    /**
     * Полностью выходит из системы.
     */
    public void leaveSystem() {
        room.leave(id);
    }

    /**
     * Получает сообщение от посредника.
     *
     * @param from    отправитель или контекст канала
     * @param message текст сообщения
     */
    public void receive(String from, String message) {
        System.out.println(name + " получил от " + from + ": " + message);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

/**
 * ROLE: Client
 */
public class ChatMediatorChannelDemo {

    public static void main(String[] args) {
        ChatRoom room = new ChatRoom();

        ChatUser alice = new ChatUser("u1", "Alice", room);
        ChatUser bob = new ChatUser("u2", "Bob", room);
        ChatUser carol = new ChatUser("u3", "Carol", room);
        ChatUser dave = new ChatUser("u4", "Dave", room);

        room.createChannel("java");
        room.createChannel("spring");

        alice.joinChannel("java");
        bob.joinChannel("java");
        carol.joinChannel("spring");
        dave.joinChannel("java");
        dave.joinChannel("spring");

        alice.sendToChannel("java", "Коллеги, обсудим Mediator");
        // Bob получил от #java / Alice: Коллеги, обсудим Mediator
        // Dave получил от #java / Alice: Коллеги, обсудим Mediator

        carol.sendToChannel("spring", "Кто смотрел Spring Events?");
        // Dave получил от #spring / Carol: Кто смотрел Spring Events?

        bob.sendPrivate("u3", "Carol, напишу тебе отдельно");
        // Carol получил от private / Bob: Carol, напишу тебе отдельно
    }
}
```

### Пояснение по Mediator

Этот вариант особенно хорошо показывает силу паттерна. `ChatRoom` становится полноценным координатором: он знает пользователей, комнаты, membership и правила доставки. При этом пользователи по-прежнему остаются слабо связанными, потому что не знают друг друга напрямую — всё взаимодействие идёт только через посредника — https://refactoring.guru/design-patterns/mediator и https://refactoring.guru/design-patterns/mediator/typescript/example

---

## 5. Когда какой вариант выбирать

| Вариант | Когда подходит | Плюсы | Ограничения |
|---|---|---|---|
| `List` + broadcast | Для первого учебного примера паттерна | Максимально наглядно показывает Mediator | Удаление и поиск конкретного пользователя неудобны |
| `Map` + id | Когда нужны поиск, удаление и личные сообщения | Быстрый доступ по ключу, более практичный пример | Broadcast всем всё равно остаётся `O(n)` — https://stackoverflow.com/questions/13075233/hashmap-remove-complexity и https://javabypatel.blogspot.com/2015/10/time-complexity-of-hashmap-get-and-put-operation.html |
| `Map + group/channel` | Когда в системе есть комнаты, каналы, группы | Сообщение идёт только нужным участникам; пример ближе к реальной архитектуре | Логика сложнее, чем в базовом учебном примере |

Mediator полезен тогда, когда хочется убрать прямые связи между компонентами и вынести правила взаимодействия в один центр координации. Именно это и показывают все три примера: меняются структуры данных и маршрутизация, но сама идея паттерна остаётся одной и той же — https://refactoring.guru/design-patterns/mediator и https://en.wikipedia.org/wiki/Mediator_pattern