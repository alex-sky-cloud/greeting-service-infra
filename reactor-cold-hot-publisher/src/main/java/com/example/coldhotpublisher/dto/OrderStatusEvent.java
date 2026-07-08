package com.example.coldhotpublisher.dto;

import java.time.Instant;

/** Одно событие смены статуса заказа в SSE-потоке. */
public record OrderStatusEvent(
    String orderId,
    String status,
    Instant createdAt
) {}
