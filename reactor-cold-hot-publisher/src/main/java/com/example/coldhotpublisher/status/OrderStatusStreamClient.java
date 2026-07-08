package com.example.coldhotpublisher.status;

import com.example.coldhotpublisher.dto.OrderStatusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class OrderStatusStreamClient {

    private final WebClient orderWebClient;

    public OrderStatusStreamClient(@Qualifier("orderWebClient") WebClient orderWebClient) {
        this.orderWebClient = orderWebClient;
    }

    public Flux<OrderStatusEvent> liveStatusesShared(String orderId) {
        return orderWebClient.get()
            .uri("/orders/{id}/statuses/stream", orderId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(OrderStatusEvent.class)
            .doOnSubscribe(s -> log.info("status -> OPEN /orders/{}/statuses/stream", orderId))
            .doOnNext(e -> log.info("status <- orderId={}, status={}", e.orderId(), e.status()))
            .doOnError(e -> log.error("status !! failed orderId={}", orderId, e))
            .share();
    }

    public Flux<OrderStatusEvent> liveStatusesReplayLast(String orderId) {
        return orderWebClient.get()
            .uri("/orders/{id}/statuses/stream", orderId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(OrderStatusEvent.class)
            .doOnSubscribe(s -> log.info("status(replay) -> OPEN /orders/{}/statuses/stream", orderId))
            .doOnNext(e -> log.info("status(replay) <- orderId={}, status={}", e.orderId(), e.status()))
            .replay(1)
            .autoConnect(1);
    }
}
