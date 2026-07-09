package com.example.coldhotpublisher.service.status;

import com.example.coldhotpublisher.model.OrderStatusEvent;
import com.example.coldhotpublisher.infra.webclient.ApiClientKind;
import com.example.coldhotpublisher.infra.webclient.ExternalApiClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * <p>Подключается к службе отслеживания заказов и отдаёт поток смены статусов.</p>
 * <p>Два режима демонстрируют разницу для опоздавшего UI: только будущие этапы
 * или сразу последний известный статус плюс дальнейшие обновления.</p>
 */
@Slf4j
@Service
public class OrderStatusStreamClient implements OrderStatusStream {

    private final WebClient orderWebClient;

    public OrderStatusStreamClient(ExternalApiClientRegistry externalApiClients) {
        this.orderWebClient = externalApiClients.webClient(ApiClientKind.ORDER_STATUS);
    }

    @Override
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

    @Override
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
