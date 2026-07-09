package com.example.coldhotpublisher.infra.webclient;

import com.example.coldhotpublisher.infra.webclient.stub.ExternalSystemStubExchange;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/** Исходящий {@code WebClient} к внешнему поставщику котировок (SSE). */
@Component
public class MarketExternalApiClient implements ExternalApiClient {

    private final WebClient webClient;

    public MarketExternalApiClient(WebClient.Builder builder,
                                   ExternalSystemStubExchange stubExchange,
                                   ExchangeFilterFunction correlationIdFilter) {
        this.webClient = ExternalApiClientFactory.eventStreamClient(builder, stubExchange, correlationIdFilter);
    }

    @Override
    public ApiClientKind getKind() {
        return ApiClientKind.MARKET;
    }

    @Override
    public WebClient webClient() {
        return webClient;
    }
}
