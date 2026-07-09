package com.example.coldhotpublisher.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Снимок котировки валютной пары: bid/ask на момент тика. */
public record QuoteEvent(
    String symbol,
    BigDecimal bid,
    BigDecimal ask,
    Instant timestamp
) {}
