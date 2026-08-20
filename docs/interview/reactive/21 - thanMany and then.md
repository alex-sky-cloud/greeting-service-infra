# ThenMany and then

```java


 Flux<Void> importTransactions(Flux<Transaction> fileStream) {

        return fileStream
            .limitRate(50) // запрашиваем у файлового источника не более 50 строк за раз
            .flatMap(transaction ->
                Mono.fromCallable(() -> transactionRepository.save(transaction)) // запись в БД — медленная операция
                    .subscribeOn(Schedulers.boundedElastic())
            )
            .thenMany(Flux.empty());
    }

```

`thenMany(Flux.empty())` здесь нужен только потому, что метод объявлен как `Flux<Void>`. 
 - Он отбрасывает все результаты `save(...)`, ждёт завершения обработки всех транзакций, затем запускает пустой `Flux` и сразу завершает его.

## Почему не `then()`

`then()` делает почти то же смыслово:

- игнорирует все `onNext`;
- ждёт успешного завершения исходного `Flux`;
- пробрасывает ошибку, если она возникла;
- возвращает `Mono<Void>`.

Но ваш метод сейчас возвращает `Flux<Void>`:

```java
Flux<Void> importTransactions(...)
```

Поэтому так не скомпилируется:

```java
return fileStream
    // ...
    .then(); // Mono<Void>, а метод ожидает Flux<Void>
```


## Правильнее изменить тип

Для операции «импортировать и сообщить только об успешном завершении либо ошибке» естественный тип — `Mono<Void>`:

```java
Mono<Void> importTransactions(Flux<Transaction> fileStream) {
    return fileStream
        .limitRate(50)
        .flatMap(transaction ->
            Mono.fromCallable(() -> transactionRepository.save(transaction))
                .subscribeOn(Schedulers.boundedElastic())
        )
        .then();
}
```

То есть `then()` здесь **нужен**: он отбрасывает результаты сохранений и оставляет только terminal signal — `onComplete` или `onError`.

## Когда нужен `thenMany`

`thenMany(...)` нужен, когда после завершения исходного publisher надо запустить **другой `Flux` и вернуть его элементы**.

Например:

```java
Flux<ImportEvent> importTransactions(Flux<Transaction> fileStream) {
    return saveAll(fileStream)
        .thenMany(Flux.just(
            new ImportEvent("IMPORT_COMPLETED")
        ));
}
```

В исходном коде:

```java
.thenMany(Flux.empty())
```

второй `Flux` ничего не испускает, поэтому практического смысла в нём нет. Это лишь способ искусственно сохранить возвращаемый тип `Flux<Void>`.

```
Операторы `then()` и `thenMany(...)` именно так определены в [Reactor `Flux` API](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html): `then()` возвращает `Mono<Void>`, а `thenMany(...)` возвращает `Flux<V>` с элементами publisher, переданного в аргумент.
```
