package com.example.coldhotpublisher.model;

/** Краткий ответ клиенту после проверки заказа (без служебных полей службы anti-fraud). */
public record FraudResponseDto(
    String orderId,
    String status
) {}
