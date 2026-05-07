package com.example.greeting.n_plus_1.entity;

import jakarta.persistence.*;

/**
 * Строка заказа — таблица {@code iso_demo.order_lines}.
 *
 * <p>Эта сущность соответствует элементу коллекции
 * {@link Order#getItems()} и участвует в воспроизведении
 * двухуровневого N+1.</p>
 *
 * <p>Первый уровень N+1 возникает при ленивой загрузке коллекции
 * {@code Order.items}. Второй уровень N+1 возникает при ленивой
 * загрузке {@link #product}.</p>
 */
@Entity
@Table(name = "order_items", schema = "iso_demo")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Заказ-владелец строки.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    /**
     * Товар строки заказа.
     *
     * <p><b>⚠ {@code FetchType.LAZY} — источник второго уровня N+1.</b>
     * Каждый вызов {@code item.getProduct().getName()} или
     * {@code item.getProduct().getPrice()} без {@code JOIN FETCH}
     * порождает дополнительный SQL-запрос.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    protected OrderItem() {
    }

    /**
     * Возвращает идентификатор строки заказа.
     *
     * @return идентификатор строки
     */
    public Long getId() {
        return id;
    }

    /**
     * Возвращает заказ-владелец строки.
     *
     * @return заказ
     */
    public Order getOrder() {
        return order;
    }

    /**
     * Возвращает товар строки заказа.
     *
     * <p><b>Внимание:</b> вызов этого метода внутри цикла по строкам
     * без {@code JOIN FETCH} приводит к дополнительным запросам
     * к таблице {@code iso_demo.products}.</p>
     *
     * <pre>{@code
     * SELECT * FROM iso_demo.products WHERE id = ?
     * }</pre>
     *
     * @return товар строки
     */
    public Product getProduct() {
        return product;
    }

    /**
     * Возвращает количество единиц товара.
     *
     * @return количество
     */
    public Integer getQty() {
        return qty;
    }
}
