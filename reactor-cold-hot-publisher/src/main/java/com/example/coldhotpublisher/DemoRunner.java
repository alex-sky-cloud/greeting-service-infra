package com.example.coldhotpublisher;

import com.example.coldhotpublisher.catalog.ProductWidgetFacade;
import com.example.coldhotpublisher.config.DemoProperties;
import com.example.coldhotpublisher.fraud.OrderFraudOrchestrator;
import com.example.coldhotpublisher.market.MarketDataClient;
import com.example.coldhotpublisher.status.OrderStatusStreamClient;
import com.example.coldhotpublisher.tariff.TariffDirectoryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * <p>При profile {@code demo} после старта Spring по очереди воспроизводит шесть учебных ситуаций.</p>
 * <p>Цель — не бизнес-логика, а <em>читаемые логи</em>: по ним видно, сколько реальных HTTP/SSE
 * запусков произошло и что получил подписчик, подключившийся с опозданием.</p>
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoRunner implements CommandLineRunner {

    private final DemoProperties demoProperties;
    private final ProductWidgetFacade productWidgetFacade;
    private final OrderFraudOrchestrator orderFraudOrchestrator;
    private final TariffDirectoryClient tariffDirectoryClient;
    private final OrderStatusStreamClient orderStatusStreamClient;
    private final MarketDataClient marketDataClient;

    @Override
    public void run(String... args) throws Exception {
        coldMono();
        sharedMono();
        cachedMono();
        sharedFlux();
        replayFlux();
        refCountFlux();
    }

    /**
     * <p>Проверяем cold-поведение HTTP: подписка — это новый запрос.</p>
     * <p>В логах должны быть <b>две</b> строки {@code catalog -> GET}.</p>
     */
    private void coldMono() throws InterruptedException {
        var runner = demoProperties.getRunner();
        log.info("=== cold mono ===");
        productWidgetFacade.coldMonoDemo(runner.getProductId());
        Thread.sleep(runner.getColdMonoWaitMs());
    }

    /**
     * <p>Проверяем, что {@code share()} не дублирует дорогой вызов между audit/metrics/response.</p>
     * <p>В логах — <b>одна</b> строка {@code fraud -> POST}.</p>
     */
    private void sharedMono() throws InterruptedException {
        var runner = demoProperties.getRunner();
        log.info("=== shared mono ===");
        orderFraudOrchestrator.processOrder(runner.getFraudOrderId());
        Thread.sleep(runner.getSharedMonoWaitMs());
    }

    /**
     * <p>Проверяем, что {@code cache()} отдаёт готовый результат второму подписчику.</p>
     * <p>В логах — <b>одна</b> строка {@code tariff -> GET}, два {@code request-N <-}.</p>
     */
    private void cachedMono() throws InterruptedException {
        var runner = demoProperties.getRunner();
        log.info("=== cached mono ===");

        tariffDirectoryClient.getTariffs()
            .subscribe(t -> log.info("request-1 <- version={}", t.version()));

        Thread.sleep(runner.getCachedMonoBetweenRequestsMs());

        tariffDirectoryClient.getTariffs()
            .subscribe(t -> log.info("request-2 <- version={}", t.version()));

        Thread.sleep(runner.getCachedMonoWaitMs());
    }

    /**
     * <p>Проверяем {@code Flux.share()}: UI подключается позже audit и не видит старые статусы.</p>
     * <p>Сравните набор {@code ui-late <-} с прогоном {@link #replayFlux()}.</p>
     */
    private void sharedFlux() throws InterruptedException {
        var runner = demoProperties.getRunner();
        log.info("=== shared flux ===");

        var shared = orderStatusStreamClient.liveStatusesShared(runner.getSharedFluxOrderId());

        shared.subscribe(e -> log.info("audit <- {}", e.status()));

        Thread.sleep(runner.getFluxLateSubscriberDelayMs());

        shared.subscribe(e -> log.info("ui-late <- {}", e.status()));

        Thread.sleep(runner.getFluxWaitMs());
    }

    /**
     * <p>Проверяем {@code replay(1)}: опоздавший UI сразу получает последний известный статус.</p>
     * <p>Отличие от {@link #sharedFlux()} — в логах у {@code ui-late} будет не пустой старт.</p>
     */
    private void replayFlux() throws InterruptedException {
        var runner = demoProperties.getRunner();
        log.info("=== replay flux ===");

        var replayed = orderStatusStreamClient.liveStatusesReplayLast(runner.getReplayFluxOrderId());

        replayed.subscribe(e -> log.info("audit <- {}", e.status()));

        Thread.sleep(runner.getFluxLateSubscriberDelayMs());

        replayed.subscribe(e -> log.info("ui-late <- {}", e.status()));

        Thread.sleep(runner.getFluxWaitMs());
    }

    /**
     * <p>Проверяем {@code refCount(2)}: пока один подписчик — SSE не открывается.</p>
     * <p>{@code quotes -> OPEN} появится только после второго {@code subscribe()}.</p>
     */
    private void refCountFlux() throws InterruptedException {
        var runner = demoProperties.getRunner();
        log.info("=== refCount(2) flux ===");

        var quotes = marketDataClient.sharedQuotes(runner.getQuoteSymbol());

        quotes.subscribe(q -> log.info("ui <- {}", q));

        Thread.sleep(runner.getRefCountSecondSubscriberDelayMs());

        quotes.subscribe(q -> log.info("audit <- {}", q));

        Thread.sleep(runner.getRefCountWaitMs());
    }
}
