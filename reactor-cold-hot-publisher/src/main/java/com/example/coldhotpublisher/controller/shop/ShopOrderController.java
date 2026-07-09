package com.example.coldhotpublisher.controller.shop;

import com.example.coldhotpublisher.model.FraudResponseDto;
import com.example.coldhotpublisher.model.OrderStatusEvent;
import com.example.coldhotpublisher.service.fraud.OrderFraudOrchestrator;
import com.example.coldhotpublisher.service.status.OrderStatusStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>HTTP API магазина: приём заказа и трекинг статуса.</p>
 * <p>Каждый вызов инициирует бизнес-слой; обращения к anti-fraud и службе статусов
 * уходят через исходящий {@code WebClient} (учебная подмена в {@code ExternalSystemStubExchange}).</p>
 */
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopOrderController {

    private final OrderFraudOrchestrator orderFraudOrchestrator;
    private final OrderStatusStream orderStatusStream;

    @PostMapping("/orders/{orderId}/process")
    public Mono<FraudResponseDto> processOrder(@PathVariable String orderId) {
        return orderFraudOrchestrator.processOrder(orderId);
    }

  /**
   * SSE-трекинг заказа. Каждое подключение клиента — отдельный подписчик.
   * {@code mode=shared} — только новые этапы; {@code mode=replay} — последний статус и дальше.
   */
    @GetMapping(value = "/orders/{orderId}/statuses/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<OrderStatusEvent> streamStatuses(
        @PathVariable String orderId,
        @RequestParam(defaultValue = "shared") String mode
    ) {
        if ("replay".equalsIgnoreCase(mode)) {
            return orderStatusStream.liveStatusesReplayLast(orderId);
        }
        return orderStatusStream.liveStatusesShared(orderId);
    }
}
