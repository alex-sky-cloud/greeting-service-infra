package com.example.coldhotpublisher.model;

import java.math.BigDecimal;

/** Карточка товара в каталоге: идентификатор, название, цена на витрине. */
public record ProductDto(
    String id,
    String name,
    BigDecimal price
) {}
