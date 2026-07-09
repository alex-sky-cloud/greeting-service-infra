package com.example.coldhotpublisher.controller.shop;

import com.example.coldhotpublisher.model.QuoteEvent;
import com.example.coldhotpublisher.service.market.MarketDataStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * <p>HTTP API магазина: поток котировок для витрины.</p>
 * <p>Два параллельных SSE-подключения клиента — учебный {@code refCount(2)}.</p>
 */
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopMarketController {

    private final MarketDataStream marketDataStream;

    @GetMapping(value = "/quotes/{symbol}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<QuoteEvent> streamQuotes(@PathVariable String symbol) {
        return marketDataStream.sharedQuotes(symbol);
    }
}
