package com.example.coldhotpublisher.dto;

import java.math.BigDecimal;

/** Цена доставки по зоне. */
public record TariffRow(
    String zone,
    BigDecimal price
) {}
