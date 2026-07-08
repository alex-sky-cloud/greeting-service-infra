package com.example.coldhotpublisher.dto;

/** Решение anti-fraud по заказу. */
public record FraudDecision(
    String orderId,
    String status,
    String reason
) {}
