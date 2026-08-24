package com.example.reactorworkshop.t01_map_flatmap.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Счётчик в схеме {@code reactor_workshop}.
 */
@Table(value = "meters", schema = "reactor_workshop")
public record T01MeterEntity(

        @Id
        Long id,

        @Column("serial_no")
        String serialNo,

        String city,

        @Column("installed_at")
        Instant installedAt
) {
}
