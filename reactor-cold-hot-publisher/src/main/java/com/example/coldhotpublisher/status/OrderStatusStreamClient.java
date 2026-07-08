package com.example.coldhotpublisher.status;

import com.example.coldhotpublisher.dto.OrderStatusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * <p>Подписка на live-статусы заказа через SSE.</p>
 * <p>Два метода показывают разный ответ на один вопрос: «что увидит опоздавший UI?»</p>
 */
@Slf4j
@Service
public class OrderStatusStreamClient {

    private final WebClient orderWebClient;

    public OrderStatusStreamClient(@Qualifier("orderWebClient") WebClient orderWebClient) {
        this.orderWebClient = orderWebClient;
    }

    /**
     * <p>Общий поток без истории: опоздавший видит только будущие статусы.</p>
     * <p>Подходит, когда прошлые события не нужны (только live-лента).</p>
     */
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

    /**
     * <p>Буфер на один элемент: UI, подключившийся позже, сразу получает последний статус.</p>
     * <p>Нужно, когда экран должен показать текущее состояние, а не ждать следующего тика.</p>
     */
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
