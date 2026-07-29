D - Пояснение исходного кода contactWith

Исходник метода:
[Flux.java в reactor-core](https://github.com/reactor/reactor-core/blob/main/reactor-core/src/main/java/reactor/core/publisher/Flux.java)
Javadoc по `Flux`:
[Flux API](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html)

```java
public final Flux<T> concatWith(Publisher<? extends T> other) {
    if (this instanceof FluxConcatArray) {
        FluxConcatArray<T> fluxConcatArray = (FluxConcatArray<T>) this;

        return fluxConcatArray.concatAdditionalSourceLast(other);
    }
    return concat(this, other);
}
```


## Что делает метод

Этот метод не запускает подписку и не “выполняет” поток. Он только решает, **как именно собрать новый `Flux` конкатенации**: через расширение уже существующего `FluxConcatArray` или через создание нового concat-оператора.

## По строкам

### `public final Flux<T> concatWith(Publisher<? extends T> other)`

- Это instance-метод.
- `this` — текущий `Flux`, на котором вызвали метод.
- `other` — поток, который надо прицепить **справа**.
- Возвращается новый `Flux<T>`.

Если вызов такой:

```java
left.concatWith(right)
```

то:

- `this == left`
- `other == right`

***

### `if (this instanceof FluxConcatArray)`

Это проверка:
“Текущий объект уже является внутренним concat-оператором, который хранит источники в массиве, или нет?”

`FluxConcatArray` — это внутренняя реализация для vararg-конкатенации. Она создаётся, например, здесь:

```java
public static <T> Flux<T> concat(Publisher<? extends T>... sources) {
    return onAssembly(new FluxConcatArray<>(false, sources));
}
```

То есть если раньше уже был собран concat, Reactor может не строить новый слой поверх старого, а просто расширить уже существующий набор источников.

***

```
### `FluxConcatArray<T> fluxConcatArray = (FluxConcatArray<T>) this;`
```

Здесь происходит обычное приведение типа.

Зачем оно нужно:

- до этого `this` виден как абстрактный `Flux<T>`;
- после приведения можно вызвать метод, которого у обычного `Flux` нет;
- а именно: `concatAdditionalSourceLast(other)`.

То есть смысл строки:
“раз уж мы убедились, что `this` — это именно `FluxConcatArray`, будем работать с ним как с `FluxConcatArray`”.

***

### `return fluxConcatArray.concatAdditionalSourceLast(other);`

Вот здесь главный смысл всей оптимизации.

По названию метод означает:

- `concatAdditionalSource`
— добавить ещё один источник в concat;
- `Last`
— добавить **в конец**.

То есть если внутри уже был массив источников:

```java
[a, b]
```

а теперь вызвали `.concatWith(c)`, то вместо вложенной схемы:

```java
concat(concat(a, b), c)
```

Reactor старается получить плоскую:

```java
[a, b, c]
```

И уже из неё построить новый `FluxConcatArray`.

### Почему именно “в конец”

Потому что семантика `concat` зависит от порядка.

`concat` означает:

1. сначала первый источник,
2. потом второй,
3. потом третий,
4. и так далее.

Поэтому `other` надо присоединить не куда угодно, а именно в хвост.
`left.concatWith(right)` всегда значит: текущая левая последовательность идёт первой, `other` — после неё.

***

### `return concat(this, other);`

Эта ветка работает, если `this` **не** является `FluxConcatArray`.

Например, если `this` — это:

- `FluxJust`
- `FluxMap`
- `FluxFilter`
- `FluxRange`
- другой любой оператор

Тогда расширять нечего: внутри ещё нет concat-массива источников.
Поэтому Reactor просто вызывает обычный статический `concat(this, other)`.

А тот, в свою очередь, делает:

```java
public static <T> Flux<T> concat(Publisher<? extends T>... sources) {
    return onAssembly(new FluxConcatArray<>(false, sources));
}
```

То есть из двух источников:

- `this`
- `other`

создаётся новый `FluxConcatArray`.

## Что такое `false` в `new FluxConcatArray<>(false, sources)`

Это важный флаг режима ошибок.

Есть две версии:

```java
new FluxConcatArray<>(false, sources)
new FluxConcatArray<>(true, sources)
```

Они соответствуют двум режимам:

- `false` — обычный `concat`
- `true` — `concatDelayError`


### Что означает `false`

`false` означает: **ошибку не задерживать**.

То есть алгоритм такой:

1. подписались на текущий источник;
2. читаем его элементы;
3. если он завершился успешно — идём к следующему;
4. если он дал `onError` — сразу пробрасываем ошибку вниз и больше ни к каким следующим источникам не переходим.

### Что означал бы `true`

Если бы был `true`, это был бы режим delayError:

- ошибку не выбрасывать сразу;
- попытаться дочитать оставшиеся источники;
- отдать ошибку потом.

Но `concatWith` использует обычный `concat`, а значит здесь именно **немедленное завершение по ошибке**.

## Что реально хранится внутри

Если упростить, `FluxConcatArray` хранит примерно такую идею:

```java
boolean delayError;
Publisher<? extends T>[] sources;
```

То есть:

- флаг обработки ошибок;
- массив источников, которые надо подписывать по очереди.

Тогда `concatAdditionalSourceLast(other)` по смыслу делает что-то вроде:

1. взять старый массив `sources`;
2. создать новый массив на 1 элемент длиннее;
3. скопировать старые источники;
4. положить `other` в последний индекс;
5. вернуть новый `FluxConcatArray` с тем же флагом `delayError`.

Не буквально этим кодом обязательно, но логически именно так.

## Алгоритм метода целиком

Если убрать синтаксис Java и оставить только смысл, метод делает вот что:

1. Берёт текущий поток из `this`.
2. Берёт присоединяемый поток из `other`.
3. Проверяет, не является ли текущий поток уже concat-массивом.
4. Если является:
    - не создаёт вложенный concat;
    - добавляет `other` в конец уже существующего массива источников.
5. Если не является:
    - создаёт новый concat из двух источников: `this` и `other`.
6. Возвращает новый `Flux`, ничего не исполняя прямо сейчас.

## Что будет потом при `subscribe`

Уже **после** этого метода, когда кто-то подпишется на итоговый `Flux`, начнёт работать семантика concat:

1. подписка на первый источник;
2. ожидание его `onComplete`;
3. подписка на следующий источник;
4. и так по порядку;
5. при обычном `concat` первая ошибка останавливает последовательность сразу.

То есть сам `concatWith` — это не этап исполнения, а этап **сборки структуры**.

## Очень коротко

Если перевести этот метод на человеческий язык:

- если слева уже concat из массива источников — просто допиши `other` в конец массива;
- если слева ещё не concat — создай новый concat из `this` и `other`.

Если хочешь, следующим сообщением я разберу **уже сам `FluxConcatArray`: какие поля у него есть, как устроен `subscribe`, как он переключается с одного source на другой и где именно используется флаг `delayError`**.

