package com.example.reactivestudy.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Table(name = "orders", schema = "reactive_study")
public record Order(
        @Id Long id,
        @Column("user_id") Long userId,
        @Column("product_id") Integer productId,
        @Column("product_name") String productName,
        BigDecimal amount,
        String status,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt
) {
}
