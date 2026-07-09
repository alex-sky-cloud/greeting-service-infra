package com.example.coldhotpublisher.model;

/** Вердикт anti-fraud: разрешить заказ в обработку или отклонить, с указанием причины. */
public record FraudDecision(
    String orderId,
    String status,
    String reason
) {}
