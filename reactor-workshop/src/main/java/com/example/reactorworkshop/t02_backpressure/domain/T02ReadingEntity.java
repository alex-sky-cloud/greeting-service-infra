package com.example.reactorworkshop.t02_backpressure.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Строка {@code reactor_workshop.readings} (~100 000 записей).
 * В сюжете {@code limitRate} этот record стримится по всей таблице;
 * в сюжете {@code LIMIT}/{@code OFFSET} — только страница из native query.
 *
 * @param id          PK, по нему {@code ORDER BY} в SQL-странице
 * @param meterId     счётчик
 * @param kwh         показание
 * @param recordedAt  время записи
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
