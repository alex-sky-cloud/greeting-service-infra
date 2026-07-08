package com.example.coldhotpublisher.dto;

import java.math.BigDecimal;

public record TariffRow(
    String zone,
    BigDecimal price
) {}
