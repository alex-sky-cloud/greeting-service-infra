package com.example.coldhotpublisher.infra.webclient.stub;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>Подменяет сетевой вызов {@code WebClient}: по URI и методу возвращает учебный ответ.</p>
 * <p>В проде здесь был бы реальный HTTP-коннектор; в стенде ответ собирается в процессе.</p>
 */
@Component
@RequiredArgsConstructor
public class ExternalSystemStubExchange implements ExchangeFunction {

    private static final Pattern PRODUCT_PATH = Pattern.compile("^/products/([^/]+)$");
    private static final Pattern ORDER_STATUS_PATH = Pattern.compile("^/orders/([^/]+)/statuses/stream$");
    private static final Pattern FRAUD_PATH = Pattern.compile("^/fraud/check/([^/]+)$");
    private static final Pattern QUOTE_PATH = Pattern.compile("^/quotes/([^/]+)/stream$");

    private final ExternalSystemStubResponses responses;
    private final ObjectMapper objectMapper;
    private final DataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;

    @Override
    public Mono<ClientResponse> exchange(ClientRequest request) {
        String path = request.url().getPath();
        HttpMethod method = request.method();

        if (HttpMethod.GET.equals(method)) {
            Matcher product = PRODUCT_PATH.matcher(path);
            if (product.matches()) {
                return jsonResponse(responses.product(product.group(1)));
            }
            if ("/tariffs".equals(path)) {
                return jsonResponse(responses.tariffs());
            }
            Matcher orderStatus = ORDER_STATUS_PATH.matcher(path);
            if (orderStatus.matches()) {
                return eventStreamResponse(responses.orderStatusStream(orderStatus.group(1)));
            }
            Matcher quote = QUOTE_PATH.matcher(path);
            if (quote.matches()) {
                return eventStreamResponse(responses.quoteStream(quote.group(1)));
            }
        }

        if (HttpMethod.POST.equals(method)) {
            Matcher fraud = FRAUD_PATH.matcher(path);
            if (fraud.matches()) {
                return jsonResponse(responses.fraudDecision(fraud.group(1)));
            }
        }

        return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
    }

    private <T> Mono<ClientResponse> jsonResponse(Mono<T> body) {
        return body.map(this::toJsonResponse);
    }

    private <T> ClientResponse toJsonResponse(T value) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(value);
            return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(Flux.just(bufferFactory.wrap(bytes)))
                .build();
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to encode stub JSON response", e);
        }
    }

    private Mono<ClientResponse> eventStreamResponse(Flux<?> body) {
        Flux<DataBuffer> encoded = body.map(this::toSseBuffer);
        return Mono.just(ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
            .body(encoded)
            .build());
    }

    private DataBuffer toSseBuffer(Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            String sse = "data:" + json + "\n\n";
            return bufferFactory.wrap(sse.getBytes(StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to encode stub SSE event", e);
        }
    }
}
