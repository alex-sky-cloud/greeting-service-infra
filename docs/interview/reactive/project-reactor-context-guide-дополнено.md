# Project Reactor: Context — как он видит ключи в цепочке операторов

## Оглавление

1. [Краткий ответ](#краткий-ответ)
2. [Как работает Context в Project Reactor](#как-работает-context-в-project-reactor)
3. [Направление видимости](#направление-видимости)
4. [Несколько contextWrite с одинаковым ключом](#несколько-contextwrite-с-одинаковым-ключом)
5. [Разные ключи](#разные-ключи)
6. [Уточнение по вашей формулировке](#уточнение-по-вашей-формулировке)
7. [Готовый пример в стиле Spring WebFlux](#готовый-пример-в-стиле-spring-webflux)
   - [Пример метода](#пример-метода)
   - [Что покажет этот код](#что-покажет-этот-код)
   - [Ожидаемый результат](#ожидаемый-результат)
   - [Где это похоже на Spring](#где-это-похоже-на-spring)

---

## Краткий ответ

Да, вы правильно поняли: самый верхний оператор в цепочке может читать ключи, записанные ниже него по цепочке (ближе к `subscribe()`). Если одинаковый ключ записан несколько раз, оператор увидит значение из ближайшего к нему `contextWrite`. Если ключи разные, оператор увидит все ключи, записанные ниже него. При этом `contextWrite` внутри `flatMap` изолирован и виден только внутри этого inner publisher.

---

## Как работает Context в Project Reactor

**Утверждение:**  
Context в Project Reactor — это неизменяемая структура, привязанная к каждому `Subscriber` в цепочке операторов, которая распространяется снизу вверх через механизм `Subscription`.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "A Context is tied to each Subscriber in a chain. It uses the Subscription propagation mechanism to make itself available to each operator, starting with the final subscribe and moving up the chain."

**RU:**

> "Context привязан к каждому Subscriber в цепочке. Он использует механизм распространения Subscription, чтобы стать доступным для каждого оператора, начиная с финального subscribe и двигаясь вверх по цепочке."

**Утверждение:**  
Context можно записать только в момент подписки, через оператор `contextWrite`.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "In order to populate the Context, which can only be done at subscription time, you need to use the contextWrite operator."

**RU:**

> "Для заполнения Context, что может быть сделано только во время подписки, необходимо использовать оператор contextWrite."

**Утверждение:**  
ContextView — это read-only интерфейс, который предоставляется операторам для чтения из Context.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "In order to read from the Context without misleading users into thinking one can write to it while data is running through the pipeline, only the ContextView is exposed by the operators above."

**RU:**

> "Для чтения из Context без введения пользователей в заблуждение, что можно писать в него во время прохождения данных через pipeline, операторами выше предоставляется только ContextView."

---

## Направление видимости

**Утверждение:**  
Оператор видит только тот Context, который был собран из всех `contextWrite`, находящихся ниже него в цепочке (ближе к `subscribe`).

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "The Context is immutable and its content can only be seen by operators above it."

**RU:**

> "Context неизменяем, и его содержимое может быть увидено только операторами, расположенными выше него."

**Утверждение:**  
Если оператор находится выше оператора `contextWrite(key, value)`, то он увидит это значение.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "Operators that read the Context see the value that was set closest to being under them."

**RU:**

> "Операторы, читающие Context, видят значение, которое было установлено ближе всего под ними."

**Утверждение:**  
Если оператор находится ниже оператора `contextWrite`, то он не увидит это значение.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "In your chain of operators, the relative positions of where you write to the Context and where you read from it matters."

**RU:**

> "В вашей цепочке операторов имеют значение относительные позиции того, где вы записываете в Context и где вы читаете из него."

---

## Несколько contextWrite с одинаковым ключом

**Утверждение:**  
Если в цепочке несколько `contextWrite` пишут один и тот же ключ, оператор читает значение, записанное ближайшим к нему `contextWrite` снизу.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "Similarly, in the case of several attempts to write the same key to the Context, the relative order of the writes matters, too. Operators that read the Context see the value that was set closest to being under them."

**RU:**

> "Аналогично, в случае нескольких попыток записать один и тот же ключ в Context, относительный порядок записей тоже имеет значение. Операторы, читающие Context, видят значение, которое было установлено ближе всего под ними."

**Пример:**

```java
String key = "message";
Mono<String> r = Mono
    .deferContextual(ctx -> Mono.just("Hello " + ctx.get(key)))
    .contextWrite(ctx -> ctx.put(key, "Reactor"))
    .contextWrite(ctx -> ctx.put(key, "World"));

// Оператор deferContextual увидит "Reactor", а не "World"
```

---

## Разные ключи

**Утверждение:**  
Если разные `contextWrite` записывают разные ключи, оператор, находящийся выше обоих, увидит оба ключа в своём ContextView.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "You can also merge two contexts into a new one by using putAll(ContextView)."

**RU:**

> "Вы также можете объединить два контекста в новый, используя putAll(ContextView)."

**Утверждение:**  
Если верхний оператор хочет прочитать ключ, которого он сам не записывал, но который записан ниже, он его получит, при условии, что между ним и этим `contextWrite` нет другого `contextWrite`, перезаписывающего этот ключ.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "The Context is propagated from the bottom of the chain towards the top."

**RU:**

> "Context распространяется снизу цепочки кверху."

---

## Уточнение по вашей формулировке

**Утверждение:**  
Оператор видит значения, записанные ниже него по цепочке (ближе к subscribe), потому что Context собирается при движении подписки снизу вверх.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "It uses the Subscription propagation mechanism to make itself available to each operator, starting with the final subscribe and moving up the chain."

**RU:**

> "Он использует механизм распространения Subscription, чтобы стать доступным для каждого оператора, начиная с финального subscribe и двигаясь вверх по цепочке."

---

## Готовый пример в стиле Spring WebFlux

### Пример метода

**Утверждение:**  
Inner publisher внутри `flatMap` наследует внешний Context снизу, но может локально поверх него записать свои ключи только для себя.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "Propagation and immutability isolate the Context in operators that create intermediate inner sequences such as flatMap."

**RU:**

> "Распространение и неизменяемость изолируют Context в операторах, которые создают промежуточные внутренние последовательности, такие как flatMap."

**Утверждение:**  
`contextWrite`, записанный внутри `flatMap`, не влияет на main sequence снаружи.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "This contextWrite does not impact anything outside of its flatMap."

**RU:**

> "Этот contextWrite не влияет ни на что за пределами своего flatMap."

```java
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * <p>
 * Демонстрационный бизнес-кейс для Spring WebFlux / Project Reactor:
 * обработка HTTP-запроса на создание заказа с трассировочными и security-метаданными в {@code Context}.
 * </p>
 *
 * <p>
 * Метод специально построен так, чтобы показать ключевые правила работы {@link reactor.util.context.Context}:
 * </p>
 *
 * <ul>
 *   <li>
 *     <b>Context читается только сверху относительно {@code contextWrite}</b>:
 *     оператор может видеть только те ключи, которые были записаны ниже него по цепочке,
 *     ближе к {@code subscribe()}.
 *   </li>
 *   <li>
 *     <b>При одинаковом ключе побеждает ближайший снизу {@code contextWrite}</b>:
 *     если один и тот же ключ записан несколько раз, верхний оператор увидит значение
 *     из самого близкого к нему {@code contextWrite}, расположенного ниже по цепочке.
 *   </li>
 *   <li>
 *     <b>Если ключи разные, они объединяются</b>:
 *     верхний оператор может одновременно читать и ближайшее переопределённое значение,
 *     и другие ключи, пришедшие из более нижних {@code contextWrite}.
 *   </li>
 *   <li>
 *     <b>Внутренний {@code contextWrite} внутри {@code flatMap} изолирован</b>:
 *     такой Context виден только внутреннему {@code Subscriber}, созданному для inner publisher,
 *     и не влияет на основной внешний pipeline.
 *   </li>
 *   <li>
 *     <b>Inner publisher может читать внешний Context</b>:
 *     внутренний publisher внутри {@code flatMap} наследует внешний downstream Context,
 *     но может локально поверх него записать свои ключи только для себя.
 *   </li>
 * </ul>
 *
 * <p>
 * В этом примере используются такие ключи:
 * </p>
 *
 * <ul>
 *   <li>{@code traceId} — корреляционный идентификатор запроса.</li>
 *   <li>{@code userId} — идентификатор пользователя.</li>
 *   <li>{@code authToken} — токен аутентификации.</li>
 *   <li>{@code auditScope} — локальный audit-контекст, записываемый только во внутреннем publisher.</li>
 * </ul>
 *
 * <p>
 * Ожидаемая семантика:
 * </p>
 *
 * <ol>
 *   <li>
 *     Самый верхний оператор читает {@code traceId}, {@code userId} и {@code authToken}.
 *   </li>
 *   <li>
 *     {@code traceId} записан дважды ниже по цепочке, поэтому верхний оператор увидит
 *     ближайшее к нему значение.
 *   </li>
 *   <li>
 *     {@code userId} и {@code authToken} не перекрываются ближе кверху, поэтому верхний оператор
 *     увидит нижние значения.
 *   </li>
 *   <li>
 *     Внутри {@code flatMap} создаётся inner publisher с локальным {@code contextWrite(auditScope=...)}.
 *     Этот ключ доступен только внутри inner publisher и не виден наружу.
 *   </li>
 * </ol>
 *
 * <p>
 * Официально Reactor описывает это так:
 * Context привязан к каждому {@code Subscriber}, передаётся через механизм {@code Subscription},
 * а inner sequence, созданная, например, через {@code flatMap}, изолирует собственные локальные изменения Context
 * от внешней main sequence.
 * </p>
 *
 * @return {@link Mono} со строкой-отчётом, в которой явно видно, какие ключи были доступны
 *         внешнему и внутреннему участкам цепочки.
 */
public Mono<String> buildOrderProcessingReport() {
    return Mono.deferContextual(outerCtx -> {
                // ===== TOP OPERATOR =====
                // Этот самый верхний оператор читает Context, собранный всеми contextWrite ниже него.
                // Он увидит:
                // - traceId = "trace-from-service-layer", потому что этот ключ был переопределён
                //   ближайшим снизу contextWrite.
                // - userId = "user-42", потому что этот ключ записан только в нижнем contextWrite.
                // - authToken = "token-abc", потому что этот ключ тоже пришёл из нижнего contextWrite.
                String topTraceId = outerCtx.get("traceId");
                String topUserId = outerCtx.get("userId");
                String topAuthToken = outerCtx.get("authToken");

                String outerRead =
                    "OUTER_READ[" +
                    "traceId=" + topTraceId + ", " +
                    "userId=" + topUserId + ", " +
                    "authToken=" + topAuthToken +
                    "]";

                return Mono.just("create-order-command")
                    .flatMap(command ->
                        Mono.deferContextual(innerCtx -> {
                            // ===== INNER OPERATOR INSIDE flatMap =====
                            // Inner publisher наследует внешний Context снизу,
                            // поэтому он тоже может читать:
                            // - traceId = "trace-from-service-layer"
                            // - userId = "user-42"
                            // - authToken = "token-abc"
                            //
                            // Но здесь мы ещё локально добавим auditScope через contextWrite ниже.
                            // Поэтому именно этот inner subscriber увидит:
                            // - auditScope = "inner-db-save"
                            //
                            // Этот auditScope НЕ выйдет наружу в main sequence.
                            String innerTraceId = innerCtx.get("traceId");
                            String innerUserId = innerCtx.get("userId");
                            String innerAuthToken = innerCtx.get("authToken");
                            String innerAuditScope = innerCtx.get("auditScope");

                            return Mono.just(
                                outerRead +
                                " -> INNER_READ[" +
                                "traceId=" + innerTraceId + ", " +
                                "userId=" + innerUserId + ", " +
                                "authToken=" + innerAuthToken + ", " +
                                "auditScope=" + innerAuditScope +
                                "]"
                            );
                        })
                        .contextWrite(ctx -> ctx.put("auditScope", "inner-db-save"))
                    )
                    .flatMap(reportFromInner ->
                        Mono.deferContextual(afterInnerCtx -> {
                            // ===== OUTER OPERATOR AFTER INNER flatMap =====
                            // Мы снова находимся во внешней цепочке.
                            // Здесь auditScope уже НЕ виден, потому что он был локальным
                            // для inner publisher внутри flatMap.
                            //
                            // Поэтому:
                            // - traceId будет виден как "trace-from-service-layer"
                            // - userId будет виден как "user-42"
                            // - authToken будет виден как "token-abc"
                            // - auditScope отсутствует
                            String afterTraceId = afterInnerCtx.get("traceId");
                            String afterUserId = afterInnerCtx.get("userId");
                            String afterAuthToken = afterInnerCtx.get("authToken");
                            String afterAuditScope = afterInnerCtx.getOrDefault("auditScope", "<not-visible-outside-inner>");

                            return Mono.just(
                                reportFromInner +
                                " -> OUTER_AFTER_INNER[" +
                                "traceId=" + afterTraceId + ", " +
                                "userId=" + afterUserId + ", " +
                                "authToken=" + afterAuthToken + ", " +
                                "auditScope=" + afterAuditScope +
                                "]"
                            );
                        })
                    );
            })
            // Ближайший к верхнему оператору contextWrite для traceId.
            // Именно это значение traceId увидят верхние операторы.
            .contextWrite(ctx -> ctx.put("traceId", "trace-from-service-layer"))

            // Более нижний contextWrite.
            // Он записывает traceId, userId и authToken.
            // Но traceId будет перекрыт более близким к верху contextWrite выше.
            .contextWrite(ctx -> ctx
                .put("traceId", "trace-from-http-filter")
                .put("userId", "user-42")
                .put("authToken", "token-abc")
            );
}
```

---

### Что покажет этот код

**Утверждение:**  
Если выполнить этот `Mono`, результат будет логически таким: верхний оператор увидит `traceId=trace-from-service-layer`, а не `trace-from-http-filter`, потому что при одинаковом ключе читается ближайшее снизу значение.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "Operators that read the Context see the value that was set closest to being under them."

**RU:**

> "Операторы, читающие Context, видят значение, которое было установлено ближе всего под ними."

**Утверждение:**  
При этом `userId` и `authToken` он тоже увидит, потому что эти ключи не были перекрыты более близким `contextWrite`.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "You can also merge two contexts into a new one by using putAll(ContextView)."

**RU:**

> "Вы также можете объединить два контекста в новый, используя putAll(ContextView)."

**Утверждение:**  
Внутренний publisher внутри `flatMap` увидит внешний Context и дополнительно увидит свой локальный `auditScope=inner-db-save`, потому что inner sequence наследует внешний Context, но может локально обогатить его для собственного `Subscriber`.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "Propagation and immutability isolate the Context in operators that create intermediate inner sequences such as flatMap."

**RU:**

> "Распространение и неизменяемость изолируют Context в операторах, которые создают промежуточные внутренние последовательности, такие как flatMap."

**Утверждение:**  
После выхода из inner sequence внешний pipeline уже не увидит `auditScope`, потому что `contextWrite` внутри `flatMap` не влияет на main sequence снаружи.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "This contextWrite does not impact anything outside of its flatMap."

**RU:**

> "Этот contextWrite не влияет ни на что за пределами своего flatMap."

---

### Ожидаемый результат

Пример итоговой строки:

```text
OUTER_READ[traceId=trace-from-service-layer, userId=user-42, authToken=token-abc]
-> INNER_READ[traceId=trace-from-service-layer, userId=user-42, authToken=token-abc, auditScope=inner-db-save]
-> OUTER_AFTER_INNER[traceId=trace-from-service-layer, userId=user-42, authToken=token-abc, auditScope=<not-visible-outside-inner>]
```

Здесь видно сразу три вещи: верхний оператор читает нижние ключи, одинаковый `traceId` перекрывается ближайшей записью, а локальный `auditScope` существует только внутри inner publisher.

---

### Где это похоже на Spring

**Утверждение:**  
Такой паттерн похож на реальный WebFlux-кейс, где нижний `contextWrite` делает, например, `WebFilter`, который кладёт `userId`, `authToken`, базовый `traceId`.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "In order to propagate a correlation ID, it would be called as follows: doPut(\"www.example.com\", Mono.just(\"Walter\")).contextWrite(Context.of(HTTP_CORRELATION_ID, \"2-j3r9afaf92j-afkaf\"))"

**RU:**

> "Для распространения correlation ID вызов может выглядеть так: doPut(\"www.example.com\", Mono.just(\"Walter\")).contextWrite(Context.of(HTTP_CORRELATION_ID, \"2-j3r9afaf92j-afkaf\"))"

**Утверждение:**  
Выше сервисный слой переопределяет `traceId` для конкретной операции, а внутри `flatMap` на репозиторий или внешний клиент создаётся локальный audit-контекст только для внутренней операции.

**Источник:** https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "Propagation and immutability isolate the Context in operators that create intermediate inner sequences such as flatMap."

**RU:**

> "Распространение и неизменяемость изолируют Context в операторах, которые создают промежуточные внутренние последовательности, такие как flatMap."
