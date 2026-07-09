package com.example.coldhotpublisher.infra.webclient;

import com.example.coldhotpublisher.infra.webclient.stub.ExternalSystemStubExchange;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/** Исходящий {@code WebClient} к внешней службе статусов заказа (SSE). */
@Component
public class OrderStatusExternalApiClient implements ExternalApiClient {

    private final WebClient webClient;

    public OrderStatusExternalApiClient(WebClient.Builder builder,
                                        ExternalSystemStubExchange stubExchange,
                                        ExchangeFilterFunction correlationIdFilter) {
        this.webClient = ExternalApiClientFactory.eventStreamClient(builder, stubExchange, correlationIdFilter);
    }

    @Override
    public ApiClientKind getKind() {
        return ApiClientKind.ORDER_STATUS;
    }

    @Override
    public WebClient webClient() {
        return webClient;
    }
}
