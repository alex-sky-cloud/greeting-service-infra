package com.example.reactorworkshop.t02_backpressure.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Показание из {@code reactor_workshop.readings} (~100 000 строк).
 */
@Table(value = "readings", schema = "reactor_workshop")
public record T02ReadingEntity(

        @Id
        Long id,

        @Column("meter_id")
        Long meterId,

        BigDecimal kwh,

        @Column("recorded_at")
        Instant recordedAt
) {
}
