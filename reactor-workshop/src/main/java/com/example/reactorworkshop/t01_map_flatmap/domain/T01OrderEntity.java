package com.example.reactorworkshop.t01_map_flatmap.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Строка {@code reactor_workshop.orders} для лабы t01.
 */
@Table(value = "orders", schema = "reactor_workshop")
public record T01OrderEntity(

        @Id
        Long id,

        @Column("user_id")
        Long userId,

        @Column("product_name")
        String productName,

        BigDecimal amount,

        @Column("created_at")
        Instant createdAt
) {
}
