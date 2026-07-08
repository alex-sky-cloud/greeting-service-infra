package com.example.coldhotpublisher.dto;

import java.time.Instant;

public record OrderStatusEvent(
    String orderId,
    String status,
    Instant createdAt
) {}
