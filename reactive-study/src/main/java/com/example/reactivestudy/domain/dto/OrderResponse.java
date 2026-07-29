package com.example.reactivestudy.domain.dto;

import com.example.reactivestudy.domain.model.Order;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        Long userId,
        Integer productId,
        String productName,
        BigDecimal amount,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.userId(),
                order.productId(),
                order.productName(),
                order.amount(),
                order.status(),
                order.createdAt(),
                order.updatedAt()
        );
    }
}
