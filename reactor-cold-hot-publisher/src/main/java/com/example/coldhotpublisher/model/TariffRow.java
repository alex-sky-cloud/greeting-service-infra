package com.example.coldhotpublisher.model;

import java.math.BigDecimal;

/** Стоимость доставки в конкретную географическую зону. */
public record TariffRow(
    String zone,
    BigDecimal price
) {}
