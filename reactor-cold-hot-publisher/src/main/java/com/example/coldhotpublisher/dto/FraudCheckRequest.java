package com.example.coldhotpublisher.dto;

/** Запрос проверки заказа на fraud. */
public record FraudCheckRequest(
    String orderId
) {}
