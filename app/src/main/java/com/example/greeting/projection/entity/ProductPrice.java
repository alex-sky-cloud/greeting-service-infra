package com.example.greeting.projection.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Запись об исторической цене товара.
 *
 * <p>Актуальная цена — строка с наибольшим {@code validFrom} при
 * {@code active = true}. Устаревшие записи не удаляются, что позволяет
 * воспроизводить цену на дату оформления заказа.</p>
 */
@Entity
@Table(schema = "shop_demo", name = "product_price")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProductPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(nullable = false)
    private boolean active;

    // getters / setters опущены для краткости
}
