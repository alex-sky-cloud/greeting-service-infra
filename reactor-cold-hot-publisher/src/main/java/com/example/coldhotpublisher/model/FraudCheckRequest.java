package com.example.coldhotpublisher.model;

/** Запрос службе anti-fraud: проверить указанный заказ. */
public record FraudCheckRequest(
    String orderId
) {}
