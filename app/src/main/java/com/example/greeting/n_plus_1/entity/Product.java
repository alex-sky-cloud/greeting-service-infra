package com.example.greeting.n_plus_1.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Товар из справочника {@code iso_demo.products}.
 *
 * <p>Является <b>листовым узлом</b> объектного графа
 * {@code Order → OrderLine → Product}.</p>
 *
 * <p>Обращение к {@link #getName()} внутри цикла по строкам заказа
 * вызывает <em>второй уровень N+1</em>:</p>
 * <pre>{@code
 * item.getProduct().getName();
 * // → SELECT * FROM iso_demo.products WHERE id = ?
 * }</pre>
 */
@Entity
@Table(name = "products", schema = "iso_demo")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Product() {}

    public Long getId() { return id; }

    /**
     * Возвращает название товара.
     *
     * <p><b>Внимание:</b> вызов без {@code JOIN FETCH} в рамках цикла
     * порождает {@code SELECT iso_demo.products WHERE id = ?} —
     * второй уровень N+1.</p>
     *
     * @return название товара
     */
    public String getName() { return name; }

    public BigDecimal getPrice() { return price; }
}
