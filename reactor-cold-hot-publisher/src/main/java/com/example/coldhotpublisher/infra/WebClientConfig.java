package com.example.coldhotpublisher.infra;

import com.example.coldhotpublisher.config.DemoProperties;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final DemoProperties demoProperties;

    @Bean
    public ExchangeFilterFunction correlationIdFilter() {
        return (request, next) -> next.exchange(
            ClientRequest.from(request)
                .header("X-Correlation-Id", UUID.randomUUID().toString())
                .build()
        );
    }

    @Bean
    @Qualifier("catalogWebClient")
    public WebClient catalogWebClient(WebClient.Builder builder,
                                      ExchangeFilterFunction correlationIdFilter) {
        return buildJsonWebClient(builder, correlationIdFilter);
    }

    @Bean
    @Qualifier("fraudWebClient")
    public WebClient fraudWebClient(WebClient.Builder builder,
                                    ExchangeFilterFunction correlationIdFilter) {
        return buildJsonWebClient(builder, correlationIdFilter);
    }

    @Bean
    @Qualifier("tariffWebClient")
    public WebClient tariffWebClient(WebClient.Builder builder,
                                     ExchangeFilterFunction correlationIdFilter) {
        return buildJsonWebClient(builder, correlationIdFilter);
    }

    @Bean
    @Qualifier("orderWebClient")
    public WebClient orderWebClient(WebClient.Builder builder,
                                    ExchangeFilterFunction correlationIdFilter) {
        return buildEventStreamWebClient(builder, correlationIdFilter);
    }

    @Bean
    @Qualifier("marketWebClient")
    public WebClient marketWebClient(WebClient.Builder builder,
                                     ExchangeFilterFunction correlationIdFilter) {
        return buildEventStreamWebClient(builder, correlationIdFilter);
    }

    private WebClient buildJsonWebClient(WebClient.Builder builder,
                                         ExchangeFilterFunction correlationIdFilter) {
        return builder
            .baseUrl(demoProperties.stubBaseUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .filter(correlationIdFilter)
            .build();
    }

    private WebClient buildEventStreamWebClient(WebClient.Builder builder,
                                                ExchangeFilterFunction correlationIdFilter) {
        return builder
            .baseUrl(demoProperties.stubBaseUrl())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
            .filter(correlationIdFilter)
            .build();
    }
}
