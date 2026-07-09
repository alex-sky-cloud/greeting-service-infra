package com.example.coldhotpublisher.infra.webclient;

import com.example.coldhotpublisher.infra.webclient.stub.ExternalSystemStubExchange;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/** Исходящий {@code WebClient} к внешней службе каталога. */
@Component
public class CatalogExternalApiClient implements ExternalApiClient {

    private final WebClient webClient;

    public CatalogExternalApiClient(WebClient.Builder builder,
                                    ExternalSystemStubExchange stubExchange,
                                    ExchangeFilterFunction correlationIdFilter) {
        this.webClient = ExternalApiClientFactory.jsonClient(builder, stubExchange, correlationIdFilter);
    }

    @Override
    public ApiClientKind getKind() {
        return ApiClientKind.CATALOG;
    }

    @Override
    public WebClient webClient() {
        return webClient;
    }
}
