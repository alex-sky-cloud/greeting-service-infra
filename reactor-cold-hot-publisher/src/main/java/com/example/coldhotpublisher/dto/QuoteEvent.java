package com.example.coldhotpublisher.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record QuoteEvent(
    String symbol,
    BigDecimal bid,
    BigDecimal ask,
    Instant timestamp
) {}
