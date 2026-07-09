package com.example.coldhotpublisher.model;

import java.time.Instant;

/** Этап жизненного цикла заказа (создан, оплачен, отгружен…) с меткой времени. */
public record OrderStatusEvent(
    String orderId,
    String status,
    Instant createdAt
) {}
