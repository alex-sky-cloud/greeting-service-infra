package com.example.greeting.projection.entity;

import com.example.greeting.n_plus_1.entity.OrderItem;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Заказ покупателя.
 *
 * <p>Допустимые статусы: {@code NEW}, {@code CONFIRMED},
 * {@code SHIPPED}, {@code DELIVERED}, {@code CANCELLED}.</p>
 *
 * @implNote Связь {@code items} — ленивая. Загрузка строк заказа без
 *           явного JOIN FETCH при итерации по списку заказов порождает N+1.
 *           В примерах проекций показывается, как этого избежать через
 *           интерфейсные и DTO-проекции.
 */
@Entity
@Table(schema = "shop_demo", name = "order")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderItem> items;

    // getters / setters опущены для краткости
}