package com.example.coldhotpublisher.service.market;

import com.example.coldhotpublisher.model.QuoteEvent;
import reactor.core.publisher.Flux;

/** Поток биржевых котировок для экранов, где цена привязана к курсу валютной пары. */
public interface MarketDataStream {

    Flux<QuoteEvent> sharedQuotes(String symbol);
}
