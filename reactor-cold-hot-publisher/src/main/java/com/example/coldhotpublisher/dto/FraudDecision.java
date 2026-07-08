package com.example.coldhotpublisher.dto;

public record FraudDecision(
    String orderId,
    String status,
    String reason
) {}
