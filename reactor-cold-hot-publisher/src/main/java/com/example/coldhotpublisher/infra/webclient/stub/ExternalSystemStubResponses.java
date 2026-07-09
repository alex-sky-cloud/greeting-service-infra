package com.example.coldhotpublisher.infra.webclient.stub;

import com.example.coldhotpublisher.config.DemoProperties;
import com.example.coldhotpublisher.model.FraudDecision;
import com.example.coldhotpublisher.model.OrderStatusEvent;
import com.example.coldhotpublisher.model.ProductDto;
import com.example.coldhotpublisher.model.QuoteEvent;
import com.example.coldhotpublisher.model.TariffRow;
import com.example.coldhotpublisher.model.TariffTable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>Формирует учебные ответы «внешних» систем для {@link ExternalSystemStubExchange}.</p>
 * <p>Не HTTP-контроллер: данные подставляются на уровне {@code WebClient}, без сетевого round-trip.</p>
 */
@Component
@RequiredArgsConstructor
public class ExternalSystemStubResponses {

    private final DemoProperties demoProperties;

    public Mono<ProductDto> product(String id) {
        var stubData = demoProperties.getStubData();
        var stubTiming = demoProperties.getStubTiming();

        return Mono.just(new ProductDto(
                id,
                stubData.getProductNamePrefix() + id,
                stubData.getProductPrice()
            ))
            .delayElement(Duration.ofMillis(stubTiming.getProductDelayMs()));
    }

    public Mono<FraudDecision> fraudDecision(String orderId) {
        var stubData = demoProperties.getStubData();
        var stubTiming = demoProperties.getStubTiming();

        return Mono.just(new FraudDecision(
                orderId,
                stubData.getFraudStatus(),
                stubData.getFraudReason()
            ))
            .delayElement(Duration.ofMillis(stubTiming.getFraudDelayMs()));
    }

    public Mono<TariffTable> tariffs() {
        var stubData = demoProperties.getStubData();
        var stubTiming = demoProperties.getStubTiming();

        List<TariffRow> rows = stubData.getTariffRows().stream()
            .map(row -> new TariffRow(row.getZone(), row.getPrice()))
            .toList();

        return Mono.just(new TariffTable(stubData.getTariffVersion(), rows))
            .delayElement(Duration.ofMillis(stubTiming.getTariffDelayMs()));
    }

    public Flux<OrderStatusEvent> orderStatusStream(String orderId) {
        var stubData = demoProperties.getStubData();
        var stubTiming = demoProperties.getStubTiming();
        Instant baseTime = Instant.now();

        return Flux.fromIterable(stubData.getOrderStatuses())
            .index()
            .map(tuple -> new OrderStatusEvent(
                orderId,
                tuple.getT2(),
                baseTime.plusSeconds(tuple.getT1() * stubTiming.getStatusStepSeconds())
            ))
            .delayElements(Duration.ofMillis(stubTiming.getStatusElementDelayMs()));
    }

    public Flux<QuoteEvent> quoteStream(String symbol) {
        var stubData = demoProperties.getStubData();
        var stubTiming = demoProperties.getStubTiming();

        return Flux.interval(Duration.ofMillis(stubTiming.getQuoteIntervalMs()))
            .map(i -> {
                var bid = stubData.getQuoteBaseBid()
                    .add(stubData.getQuoteBidStep().multiply(BigDecimal.valueOf(i)));
                var ask = bid.add(stubData.getQuoteAskSpread());
                return new QuoteEvent(symbol, bid, ask, Instant.now());
            })
            .take(stubData.getQuoteMaxEvents());
    }
}
