package com.example.coldhotpublisher.dto;

import java.math.BigDecimal;

/** Товар из каталога. */
public record ProductDto(
    String id,
    String name,
    BigDecimal price
) {}
