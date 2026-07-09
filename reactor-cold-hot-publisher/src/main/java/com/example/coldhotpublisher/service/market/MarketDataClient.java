package com.example.coldhotpublisher.service.market;

import com.example.coldhotpublisher.model.QuoteEvent;
import com.example.coldhotpublisher.infra.webclient.ApiClientKind;
import com.example.coldhotpublisher.infra.webclient.ExternalApiClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * <p>Подключается к поставщику котировок и раздаёт поток цен нескольким потребителям.</p>
 * <p>Платное соединение с биржей открывается только когда котировки реально нужны
 * и UI, и фоновому аудиту одновременно.</p>
 */
@Slf4j
@Service
public class MarketDataClient implements MarketDataStream {

    private final WebClient marketWebClient;

    public MarketDataClient(ExternalApiClientRegistry externalApiClients) {
        this.marketWebClient = externalApiClients.webClient(ApiClientKind.MARKET);
    }

    @Override
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
