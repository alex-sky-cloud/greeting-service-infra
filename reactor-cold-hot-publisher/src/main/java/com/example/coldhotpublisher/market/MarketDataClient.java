package com.example.coldhotpublisher.market;

import com.example.coldhotpublisher.dto.QuoteEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * <p>Котировки как «дорогой» SSE: открывать соединение имеет смысл только когда
 * есть минимум два реальных потребителя (UI и риск-модуль).</p>
 */
@Slf4j
@Service
public class MarketDataClient {

    private final WebClient marketWebClient;

    public MarketDataClient(@Qualifier("marketWebClient") WebClient marketWebClient) {
        this.marketWebClient = marketWebClient;
    }

    /**
     * <p>{@code refCount(2)} откладывает подключение к upstream до второго подписчика.</p>
     * <p>Следите за {@code quotes -> OPEN}: он не должен появиться после первого alone subscribe.</p>
     */
    public Flux<QuoteEvent> sharedQuotes(String symbol) {
        return marketWebClient.get()
            .uri("/quotes/{symbol}/stream", symbol)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(QuoteEvent.class)
            .doOnSubscribe(s -> log.info("quotes -> OPEN /quotes/{}/stream", symbol))
            .doOnNext(q -> log.info("quotes <- symbol={}, bid={}, ask={}", q.symbol(), q.bid(), q.ask()))
            .doFinally(signal -> log.info("quotes xx CLOSE symbol={}, signal={}", symbol, signal))
            .publish()
            .refCount(2);
    }
}
