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

    private void coldMono() throws InterruptedException {
        var runner = demoProperties.getRunner();
        log.info("=== cold mono ===");
        productWidgetFacade.coldMonoDemo(runner.getProductId());
        Thread.sleep(runner.getColdMonoWaitMs());
    }

    private void sharedMono() throws InterruptedException {
        var runner = demoProperties.getRunner();
        log.info("=== shared mono ===");
        orderFraudOrchestrator.processOrder(runner.getFraudOrderId());
        Thread.sleep(runner.getSharedMonoWaitMs());
    }

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

    private void sharedFlux() throws InterruptedException {
        var runner = demoProperties.getRunner();
        log.info("=== shared flux ===");

        var shared = orderStatusStreamClient.liveStatusesShared(runner.getSharedFluxOrderId());

        shared.subscribe(e -> log.info("audit <- {}", e.status()));

        Thread.sleep(runner.getFluxLateSubscriberDelayMs());

        shared.subscribe(e -> log.info("ui-late <- {}", e.status()));

        Thread.sleep(runner.getFluxWaitMs());
    }

    private void replayFlux() throws InterruptedException {
        var runner = demoProperties.getRunner();
        log.info("=== replay flux ===");

        var replayed = orderStatusStreamClient.liveStatusesReplayLast(runner.getReplayFluxOrderId());

        replayed.subscribe(e -> log.info("audit <- {}", e.status()));

        Thread.sleep(runner.getFluxLateSubscriberDelayMs());

        replayed.subscribe(e -> log.info("ui-late <- {}", e.status()));

        Thread.sleep(runner.getFluxWaitMs());
    }

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
