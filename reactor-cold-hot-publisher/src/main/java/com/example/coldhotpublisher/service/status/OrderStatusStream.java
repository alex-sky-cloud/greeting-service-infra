package com.example.coldhotpublisher.service.status;

import com.example.coldhotpublisher.model.OrderStatusEvent;
import reactor.core.publisher.Flux;

/** Поток обновлений статуса заказа в реальном времени — для трекинга и личного кабинета. */
public interface OrderStatusStream {

    Flux<OrderStatusEvent> liveStatusesShared(String orderId);

    Flux<OrderStatusEvent> liveStatusesReplayLast(String orderId);
}
