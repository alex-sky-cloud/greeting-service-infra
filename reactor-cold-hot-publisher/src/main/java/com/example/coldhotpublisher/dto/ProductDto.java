package com.example.coldhotpublisher.dto;

import java.math.BigDecimal;

public record ProductDto(
    String id,
    String name,
    BigDecimal price
) {}
