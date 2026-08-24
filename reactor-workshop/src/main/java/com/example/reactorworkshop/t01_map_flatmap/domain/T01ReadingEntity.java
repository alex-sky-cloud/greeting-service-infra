package com.example.reactorworkshop.t01_map_flatmap.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Показание счётчика ({@code readings}).
 */
@Table(value = "readings", schema = "reactor_workshop")
public record T01ReadingEntity(

        @Id
        Long id,

        @Column("meter_id")
        Long meterId,

        BigDecimal kwh,

        @Column("recorded_at")
        Instant recordedAt
) {
}
