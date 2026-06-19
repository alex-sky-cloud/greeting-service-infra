package com.example.reactivedemo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * <p>Сущность заказа. Таблица {@code reactive_demo.orders}.</p>
 *
 * <p>Связь с пользователем — по полю {@code user_id}. Загрузка заказов по пользователю
 * выполняется через {@link com.example.reactivedemo.repository.OrderRepository#findByUserId(Long)}.</p>
 *
 * @param id          первичный ключ
 * @param userId      владелец заказа
 * @param productName название товара
 * @param amount      сумма заказа
 * @param createdAt   время создания
 */
@Table(name = "orders", schema = "reactive_demo")
public record Order(
        @Id Long id,
        @Column("user_id") Long userId,
        @Column("product_name") String productName,
        BigDecimal amount,
        @Column("created_at") Instant createdAt
) {
}
