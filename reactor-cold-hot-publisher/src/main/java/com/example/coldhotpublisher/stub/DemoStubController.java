package com.example.coldhotpublisher.stub;

import com.example.coldhotpublisher.config.DemoProperties;
import com.example.coldhotpublisher.dto.FraudCheckRequest;
import com.example.coldhotpublisher.dto.FraudDecision;
import com.example.coldhotpublisher.dto.OrderStatusEvent;
import com.example.coldhotpublisher.dto.ProductDto;
import com.example.coldhotpublisher.dto.QuoteEvent;
import com.example.coldhotpublisher.dto.TariffRow;
import com.example.coldhotpublisher.dto.TariffTable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class DemoStubController {

    private final DemoProperties demoProperties;

    @GetMapping("/products/{id}")
    public Mono<ProductDto> getProduct(@PathVariable String id) {
        var stubData = demoProperties.getStubData();
        var stubTiming = demoProperties.getStubTiming();

        return Mono.just(new ProductDto(
                id,
                stubData.getProductNamePrefix() + id,
                stubData.getProductPrice()
            ))
            .delayElement(Duration.ofMillis(stubTiming.getProductDelayMs()));
    }

    @PostMapping("/fraud/check")
    public Mono<FraudDecision> checkFraud(@RequestBody FraudCheckRequest request) {
        var stubData = demoProperties.getStubData();
        var stubTiming = demoProperties.getStubTiming();

        return Mono.just(new FraudDecision(
                request.orderId(),
                stubData.getFraudStatus(),
                stubData.getFraudReason()
            ))
            .delayElement(Duration.ofMillis(stubTiming.getFraudDelayMs()));
    }

    @GetMapping("/tariffs")
    public Mono<TariffTable> getTariffs() {
        var stubData = demoProperties.getStubData();
        var stubTiming = demoProperties.getStubTiming();

        List<TariffRow> rows = stubData.getTariffRows().stream()
            .map(row -> new TariffRow(row.getZone(), row.getPrice()))
            .toList();

        return Mono.just(new TariffTable(stubData.getTariffVersion(), rows))
            .delayElement(Duration.ofMillis(stubTiming.getTariffDelayMs()));
    }

    @GetMapping(value = "/orders/{id}/statuses/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<OrderStatusEvent> streamStatuses(@PathVariable String id) {
        var stubData = demoProperties.getStubData();
        var stubTiming = demoProperties.getStubTiming();
        Instant baseTime = Instant.now();

        Flux<OrderStatusEvent> events = Flux.fromIterable(stubData.getOrderStatuses())
            .index()
            .map(tuple -> new OrderStatusEvent(
                id,
                tuple.getT2(),
                baseTime.plusSeconds(tuple.getT1() * stubTiming.getStatusStepSeconds())
            ));

        return events.delayElements(Duration.ofMillis(stubTiming.getStatusElementDelayMs()));
    }

    @GetMapping(value = "/quotes/{symbol}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<QuoteEvent> streamQuotes(@PathVariable String symbol) {
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
