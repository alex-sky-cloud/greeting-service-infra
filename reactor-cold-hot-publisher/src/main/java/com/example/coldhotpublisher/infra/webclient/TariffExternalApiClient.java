package com.example.coldhotpublisher.infra.webclient;

import com.example.coldhotpublisher.infra.webclient.stub.ExternalSystemStubExchange;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/** Исходящий {@code WebClient} к внешней службе тарифов доставки. */
@Component
public class TariffExternalApiClient implements ExternalApiClient {

    private final WebClient webClient;

    public TariffExternalApiClient(WebClient.Builder builder,
                                   ExternalSystemStubExchange stubExchange,
                                   ExchangeFilterFunction correlationIdFilter) {
        this.webClient = ExternalApiClientFactory.jsonClient(builder, stubExchange, correlationIdFilter);
    }

    @Override
    public ApiClientKind getKind() {
        return ApiClientKind.TARIFF;
    }

    @Override
    public WebClient webClient() {
        return webClient;
    }
}
