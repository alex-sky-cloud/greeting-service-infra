package com.example.coldhotpublisher.dto;

/** Упрощённый ответ orchestrator после проверки fraud. */
public record FraudResponseDto(
    String orderId,
    String status
) {}
