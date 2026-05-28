package com.example.greeting.projection.entity;

import com.example.greeting.n_plus_1.entity.OrderItem;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Товар в каталоге.
 *
 * <p>Содержит историю цен через {@code prices} и входит в строки заказов
 * через {@code orderItems}. Обе связи — ленивые.</p>
 *
 * @implNote Актуальная цена не хранится в этой сущности напрямую — она
 *           вычисляется через {@link ProductPrice} (строка с максимальным
 *           {@code validFrom} при {@code active = true}).
 *           При проекционных запросах цена подтягивается через JOIN,
 *           а не через ленивую загрузку коллекции {@code prices}.
 */
@Entity
@Table(schema = "shop_demo", name = "product")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<ProductPrice> prices;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<OrderItem> orderItems;

    // getters / setters опущены для краткости
}
