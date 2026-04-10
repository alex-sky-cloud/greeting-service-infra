package com.example.greeting.model;

/**
 * DTO ответа. Используем record — максимально лаконично для Java 21.
 */
public record GreetingResponse(
        String message,
        String environment,
        String version,
        String timestamp
) {}
