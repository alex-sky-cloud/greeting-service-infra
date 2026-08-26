package com.example.reactivestudy.reactor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.NoSuchElementException;

/**
 * Тесты по документу
 * {@code docs/interview/reactive/Context в Project Reactor полный разбор механизма.md}.
 * Код цепочек — как в гайде. Однострочные комментарии — кто Subscriber и что в Context.
 */
class ReactorContextTheoryTest {

    @Nested
    @DisplayName("§2 Почему не ThreadLocal")
    class NotThreadLocal {

        @Test
        void threadLocalIsBoundToThreadAndIsLost() {
            ThreadLocal<String> threadLocal = new ThreadLocal<>();
            threadLocal.set("from-test-thread"); // лежит на потоке теста, не на подписке

            Mono<String> fromThreadLocal = Mono.fromCallable(() -> {
                        String value = threadLocal.get(); // другой поток — ThreadLocal пуст
                        return value == null ? "нет" : value;
                    })
                    .subscribeOn(Schedulers.boundedElastic()); // смена потока

            StepVerifier.create(fromThreadLocal)
                    .expectNext("нет")
                    .verifyComplete();
        }

        @Test
        void contextIsBoundToSubscriptionAndSurvivesThreadSwitch() {
            Mono<String> fromContext =
                    Mono.just("invoice-42")
                            .publishOn(Schedulers.boundedElastic()) // другой поток
                            .flatMap(id -> Mono.deferContextual(ctx ->
                                    Mono.just(ctx.get("traceId") + ": " + id))) // та же подписка: {traceId=T-1}
                            .contextWrite(ctx -> ctx.put("traceId", "T-1"));

            StepVerifier.create(fromContext)
                    .expectNext("T-1: invoice-42")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("§3 Топология: данные вниз, подписка вверх")
    class Topology {

        @Test
        void invoiceChainFromTheGuide() {
            Mono<String> invoiceMono =
                    Mono.just("invoice-42")                              // (1) upstream: источник
                            .flatMap(id -> Mono.deferContextual(
                                    ctx -> Mono.just(ctx.get("traceId") + ": " + id)
                            ))                                                // (2) оператор преобразования
                            .contextWrite(ctx -> ctx.put("traceId", "T-1"));  // (3) downstream-оператор

            StepVerifier.create(invoiceMono)                              // (4) конечный Subscriber
                    .expectNext("T-1: invoice-42")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("§4 Матрёшка: каждый оператор — свой Subscriber")
    class Matryoshka {

        /**
         * Та же цепочка, что в §3. При {@code subscribe()} снизу вверх
         * на каждый оператор создаётся свой Subscriber — это и есть матрёшка из гайда.
         *
         * <pre>
         * subscribe()
         *     ▼ Subscriber #1 (contextWrite): empty → {traceId=T-1}   ← НОВЫЙ объект
         *     ▼ Subscriber #2 (flatMap): держит ссылку на {traceId=T-1}
         *     ▼ Subscriber #3 (источник Mono.just)
         * </pre>
         */
        @Test
        void eachOperatorGetsOwnSubscriber() {
            Mono<String> invoiceMono =
                    Mono.just("invoice-42") // Subscriber #3 — источник
                            .flatMap(id -> Mono.deferContextual(
                                    ctx -> Mono.just(ctx.get("traceId") + ": " + id)
                                    // Subscriber #2 — flatMap; Context = {traceId=T-1}
                            ))
                            .contextWrite(ctx -> ctx.put("traceId", "T-1"));
                            // Subscriber #1 — contextWrite: empty → {traceId=T-1}

            StepVerifier.create(invoiceMono) // конечный Subscriber; подписка идёт вверх
                    .expectNext("T-1: invoice-42")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("§5 N операторов и N элементов — не путать")
    class OperatorsVsElements {

        @Test
        void threeOnNextReuseContextBuiltOnceAtSubscribe() {
            Flux<String> flux =
                    Flux.just("a", "b", "c") // три onNext, не три Context
                            .flatMap(item -> Mono.deferContextual(ctx ->
                                    Mono.just(ctx.get("traceId") + ":" + item))) // один и тот же {traceId=T-1}
                            .contextWrite(ctx -> ctx.put("traceId", "T-1")); // один раз на subscribe()

            StepVerifier.create(flux)
                    .expectNext("T-1:a", "T-1:b", "T-1:c")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("§6 Правило расположения contextWrite")
    class Placement {

        @Test
        void writeAboveReadIsInvisible() {
            Mono<String> wrong = Mono.just("invoice-42")
                    .contextWrite(ctx -> ctx.put("traceId", "T-1")) // запись слишком высоко
                    .flatMap(id -> Mono.deferContextual(ctx ->
                            Mono.just(ctx.getOrDefault("traceId", "MISSING") + ": " + id)));
                            // чтение ниже write → ключа нет

            StepVerifier.create(wrong)
                    .expectNext("MISSING: invoice-42")
                    .verifyComplete();
        }

        @Test
        void writeBelowReadIsVisible() {
            Mono<String> correct = Mono.just("invoice-42")
                    .flatMap(id -> Mono.deferContextual(ctx ->
                            Mono.just(ctx.getOrDefault("traceId", "MISSING") + ": " + id)))
                            // чтение выше write → {traceId=T-1}
                    .contextWrite(ctx -> ctx.put("traceId", "T-1")); // ближе к subscribe()

            StepVerifier.create(correct)
                    .expectNext("T-1: invoice-42")
                    .verifyComplete();
        }

        @Test
        void readerSeesTheWriteClosestUnderIt() {
            String key = "message";
            Mono<String> r = Mono.deferContextual(ctx -> Mono.just("Hello " + ctx.get(key)))  // (3) видит "Reactor"
                    .contextWrite(ctx -> ctx.put(key, "Reactor"))                                  // (2)
                    .flatMap(s -> Mono.deferContextual(ctx -> Mono.just(s + " " + ctx.get(key))))  // (4) видит "World"
                    .contextWrite(ctx -> ctx.put(key, "World"));                                   // (1)

            StepVerifier.create(r)
                    .expectNext("Hello Reactor World")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("§7 Изоляция Context внутри flatMap")
    class FlatMapIsolation {

        @Test
        void innerWriteStaysInsideFlatMapLambda() {
            Mono<String> result = Mono.just("Hello")                       // внешняя цепочка
                    .flatMap(s -> {
                        Mono<String> inner = Mono.deferContextual(ctx ->
                                        Mono.just(s + " " + ctx.get("key")))
                                .contextWrite(ctx -> ctx.put("key", "Reactor"));    // только для inner
                        return inner;
                    })
                    .contextWrite(ctx -> ctx.put("key", "World"));              // для внешней цепочки

            StepVerifier.create(result)
                    .expectNext("Hello Reactor")
                    .verifyComplete();
        }

        @Test
        void outerWriteIsVisibleInsideFlatMap() {
            Mono<String> result = Mono.just("Hello")
                    .flatMap(s -> Mono.deferContextual(ctx ->
                            Mono.just(s + " " + ctx.get("key"))))   // читаем внутри лямбды
                    .contextWrite(ctx -> ctx.put("key", "World")); // пишем снаружи

            StepVerifier.create(result)
                    .expectNext("Hello World")
                    .verifyComplete();
        }

        @Test
        void twoFlatMapsInnerWriteDoesNotReplaceOuter() {
            String key = "message";
            Mono<String> r = Mono.just("Hello")
                    .flatMap(s -> Mono.deferContextual(ctx -> Mono.just(s + " " + ctx.get(key))))
                    // первый flatMap видит внешний {message=World}
                    .flatMap(s -> Mono.deferContextual(ctx -> Mono.just(s + " " + ctx.get(key)))
                            .contextWrite(ctx -> ctx.put(key, "Reactor")))  // только внутри этого flatMap
                    .contextWrite(ctx -> ctx.put(key, "World"));

            StepVerifier.create(r)
                    .expectNext("Hello World Reactor")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("§8 Чтение Context")
    class Reading {

        @Test
        void getThrowsIfKeyMissing() {
            Mono<String> readTrace = Mono.deferContextual(ctx ->
                    Mono.just(ctx.get("traceId").toString())); // ключа нет → исключение

            StepVerifier.create(readTrace)
                    .expectError(NoSuchElementException.class)
                    .verify();
        }

        @Test
        void getOrDefaultWhenKeyMissing() {
            Mono<String> readTrace = Mono.deferContextual(ctx ->
                    Mono.just(ctx.getOrDefault("traceId", "unknown"))); // ключа нет → "unknown"

            StepVerifier.create(readTrace)
                    .expectNext("unknown")
                    .verifyComplete();
        }

        @Test
        void transformDeferredContextualReadsTogetherWithMono() {
            Mono<String> transformed = Mono.just("payload")
                    .transformDeferredContextual((mono, ctx) ->
                            mono.map(payload -> payload + " for user " + ctx.get("userId")))
                    .contextWrite(ctx -> ctx.put("userId", "u-123"));

            StepVerifier.create(transformed)
                    .expectNext("payload for user u-123")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("§9 Типичная ошибка")
    class TypicalMistake {

        @Test
        void writeNearSourcePrintsUnknown() {
            Mono<String> wrong = Mono.just("start")
                    .contextWrite(ctx -> ctx.put("userId", "u-123"))
                    // ОШИБКА: contextWrite стоит ВЫШЕ deferContextual
                    .flatMap(v -> Mono.deferContextual(ctx ->
                            Mono.just("Order for user: " + ctx.getOrDefault("userId", "unknown"))));

            StepVerifier.create(wrong)
                    .expectNext("Order for user: unknown")
                    .verifyComplete();
        }

        @Test
        void writeNearSubscribePrintsUserId() {
            Mono<String> correct = Mono.deferContextual(ctx ->
                            Mono.just("Order for user: " + ctx.getOrDefault("userId", "unknown")))
                    .flatMap(Mono::just)
                    .contextWrite(ctx -> ctx.put("userId", "u-123")); // запись ниже чтения

            StepVerifier.create(correct)
                    .expectNext("Order for user: u-123")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("§10 Паттерн WebFilter")
    class WebFilterPattern {

        @Test
        void filterPutsContextAtTheEndOfReturnedChain() {
            Mono<String> handler = Mono.deferContextual(ctx ->
                    Mono.just("ok, trace=" + ctx.get("traceId"))); // контроллер / сервис

            Mono<String> afterFilter = handler
                    .contextWrite(ctx -> ctx.put("traceId", "from-filter")); // как WebFilter: write внизу

            StepVerifier.create(afterFilter)
                    .expectNext("ok, trace=from-filter")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("§11 traceId для счёта")
    class InvoiceTraceId {

        @Test
        void sameTraceIdAfterRepositoryAndConvert() {
            Mono<String> invoiceRepository = Mono.just("invoice-42");
            Mono<String> currencyClient = Mono.just("100 EUR");

            Mono<String> controller = invoiceRepository
                    .flatMap(invoiceId -> currencyClient.flatMap(convertedAmount ->
                            Mono.deferContextual(ctx -> {
                                String traceId = ctx.get("traceId").toString(); // тот же traceId, что положил фильтр
                                return Mono.just(traceId + " / " + invoiceId + " / " + convertedAmount);
                            })
                    ));
                    // subscribe() не вызываем — цепочку отдаём «инфраструктуре»

            Mono<String> withFilter = controller
                    .contextWrite(ctx -> ctx.put("traceId", "T-1")); // TraceIdWebFilter

            StepVerifier.create(withFilter)
                    .expectNext("T-1 / invoice-42 / 100 EUR")
                    .verifyComplete();
        }
    }
}
