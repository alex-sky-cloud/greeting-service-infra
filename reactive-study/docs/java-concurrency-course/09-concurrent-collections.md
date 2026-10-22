# Тема 09. Concurrent-коллекции

> **Статус:** теория · **Дальше:** интервью в чате, затем [практика](09-concurrent-collections-tasks.md)

---

## Оглавление

- [1. Почему не HashMap](#1-почему-не-hashmap)
- [2. ConcurrentHashMap](#2-concurrenthashmap)
- [3. computeIfAbsent и merge](#3-computeifabsent-и-merge)
- [4. Итератор](#4-итератор)
- [5. CopyOnWriteArrayList](#5-copyonwritearraylist)
- [Типичные ошибки](#типичные-ошибки)
- [Вопросы для интервью](#вопросы-для-интервью)

---

## 1. Почему не HashMap

**Аналогия:**

Общий блокнот без правил: двое пишут в одну клетку — строка рвётся, оглавление врёт.

Javadoc `HashMap` (JDK 17):

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html

> Note that this implementation is not synchronized. If multiple threads access a hash map concurrently, and at least one of the threads modifies the map structurally, it must be synchronized externally.

RU:

> Эта реализация не синхронизирована. Если несколько потоков работают с map одновременно и хотя бы один меняет её структурно, нужна внешняя синхронизация.

Итератор — fail-fast: чужая структурная правка → `ConcurrentModificationException` (best effort).

`Collections.synchronizedMap` — **один** замок на всю map. Безопасно, но все стоят в одной очереди.

**Правило:** общий `HashMap` без внешнего замка в многопотоке нельзя.

---

## 2. ConcurrentHashMap

Javadoc пакета `java.util.concurrent`:

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html

> A concurrent collection is thread-safe, but not governed by a single exclusion lock. In the particular case of ConcurrentHashMap, it safely permits any number of concurrent reads as well as a large number of concurrent writes.

RU:

> Concurrent-коллекция потокобезопасна, но **не** подчинена одному исключающему замку. `ConcurrentHashMap` допускает любое число одновременных чтений и много одновременных записей.

Чтения обычно не блокируются. `null` ключи и значения **нельзя**.

**Правило:** много потоков — `ConcurrentHashMap`, не «обернутый» `HashMap`, если не нужен один большой замок на составную операцию снаружи.

---

## 3. computeIfAbsent и merge

**Аналогия:**

«Если ключа нет — посчитай и положи». Это должен сделать **один**, не двое одновременно.

Javadoc `ConcurrentHashMap.computeIfAbsent` (JDK 17):

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#computeIfAbsent(K,java.util.function.Function)

> The entire method invocation is performed atomically. The supplied function is invoked exactly once per invocation of this method if the key is absent, else not at all.

RU:

> Весь вызов метода выполняется атомарно. Функция вызывается ровно один раз на этот вызов, если ключа не было, иначе ни разу.

```java
// плохо: два шага
if (!map.containsKey(k)) map.put(k, new ArrayList<>());

// один шаг
map.computeIfAbsent(k, key -> new ArrayList<>());
map.merge(word, 1, Integer::sum);
```

**Правило:** «проверил и положил» снаружи — гонка. Берите атомарный метод map.

---

## 4. Итератор

Javadoc `ConcurrentHashMap`:

> Iterators, Spliterators and Enumerations return elements reflecting the state of the hash table at some point at or since the creation of the iterator/enumeration. They do not throw `ConcurrentModificationException`.

RU:

> Итераторы отражают состояние таблицы в некоторый момент **в момент или после** создания итератора. Они не бросают `ConcurrentModificationException`.

Это weakly consistent: можно не увидеть свежую вставку, дублей «как при fail-fast» нет.

**Правило:** обход не взрывается. Снимок «как прямо сейчас вся map» не обещан.

---

## 5. CopyOnWriteArrayList

**Аналогия:**

Подписчики: почти всегда читают список, редко добавляют. На каждое изменение — **новая копия** листа.

- Источник: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html

> This is ordinarily too costly, but may be more efficient than alternatives when traversal operations vastly outnumber mutations … The "snapshot" style iterator … is guaranteed not to throw `ConcurrentModificationException`. The iterator will not reflect additions, removals, or changes to the list since the iterator was created.

RU:

> Обычно слишком дорого, но выгоднее, когда обходов гораздо больше, чем изменений. Итератор-снимок не бросает `ConcurrentModificationException` и **не видит** правки после создания.

**Правило:** слушатели, редкий add — да. Частое добавление в середину — нет.

---

## Типичные ошибки

| Ошибка | Что получится |
|---|---|
| `HashMap` в сервисе на много потоков | CME, потеря записей, в старых JDK — зависание |
| `containsKey` + `put` | два потока создадут двух «первых» |
| Ждать от итератора CHM полную актуальность | пропущенные ключи |
| COW-список как очередь задач | копирование на каждую правку |

---

## Вопросы для интервью

1. Почему `HashMap` нельзя делить между потоками без замка?
2. Чем `ConcurrentHashMap` лучше `synchronizedMap`?
3. Зачем `computeIfAbsent` вместо if-put?
4. Что видит итератор CHM? Бросает ли CME?
5. Когда уместен `CopyOnWriteArrayList`?
6. Почему в CHM нет `null`?

---

**Практика:** [09-concurrent-collections-tasks.md](09-concurrent-collections-tasks.md)

*Тема 09 курса [`00-plan.md`](00-plan.md).*
