package com.example.coldhotpublisher.infra.webclient;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * <p>Сборка {@code WebClient} к внешним системам.</p>
 * <p>В учебном стенде вместо сети — {@link com.example.coldhotpublisher.infra.webclient.stub.ExternalSystemStubExchange}.</p>
 */
final class ExternalApiClientFactory {

    /** Виртуальный baseUrl: реальный HTTP не выполняется, URI нужен только для маршрутизации в ExchangeFunction. */
    static final String VIRTUAL_BASE_URL = "http://external-systems.local";

    private ExternalApiClientFactory() {
    }

    static WebClient jsonClient(WebClient.Builder builder,
                                ExchangeFunction stubExchange,
                                ExchangeFilterFunction correlationIdFilter) {
        return builder.clone()
            .baseUrl(VIRTUAL_BASE_URL)
            .exchangeFunction(stubExchange)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .filter(correlationIdFilter)
            .build();
    }

    static WebClient eventStreamClient(WebClient.Builder builder,
                                       ExchangeFunction stubExchange,
                                       ExchangeFilterFunction correlationIdFilter) {
        return builder.clone()
            .baseUrl(VIRTUAL_BASE_URL)
            .exchangeFunction(stubExchange)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
            .filter(correlationIdFilter)
            .build();
    }
}
