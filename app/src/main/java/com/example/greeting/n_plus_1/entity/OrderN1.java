package com.example.greeting.n_plus_1.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;



/**
 * Заказ пользователя — таблица {@code iso_demo.orders}.
 *
 * <p>Первый уровень N+1 возникает при обращении к {@link #getItems()}.
 * При загрузке {@code N} заказов каждый вызов метода в цикле
 * генерирует отдельный SQL:</p>
 *
 * <pre>{@code
 * // Запрос 1 — findByUserId:
 * SELECT * FROM iso_demo.orders WHERE user_id = ?;
 *
 * // Запросы 2..N+1 — order.getItems():
 * SELECT * FROM iso_demo.order_lines WHERE order_id = ?;
 * }</pre>
 *
 * @see OrderItemN1
 * @see ProductN1
 */
@Entity
@Table(name = "orders", schema = "iso_demo")
public class OrderN1 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Строки заказа.
     *
     * <p><b>⚠ {@code FetchType.LAZY} — источник первого уровня N+1.</b>
     * Hibernate не загружает коллекцию при загрузке заказа.
     * Первое обращение к полю вне {@code JOIN FETCH} выполняет:</p>
     *
     * <pre>{@code
     * SELECT * FROM iso_demo.order_lines WHERE order_id = ?
     * }</pre>
     */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "orderN1")
    private List<OrderItemN1> items;

    protected OrderN1() {
    }

    /**
     * Возвращает идентификатор заказа.
     *
     * @return идентификатор заказа
     */
    public Long getId() {
        return id;
    }

    /**
     * Возвращает идентификатор пользователя.
     *
     * @return идентификатор пользователя
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Возвращает статус заказа.
     *
     * @return статус заказа
     */
    public String getStatus() {
        return status;
    }

    /**
     * Возвращает дату и время создания заказа.
     *
     * @return дата и время создания
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Возвращает строки заказа.
     *
     * <p><b>Внимание:</b> каждый вызов этого метода в цикле по заказам
     * без предварительного {@code JOIN FETCH} порождает отдельный
     * SQL-запрос к {@code iso_demo.order_lines} — первый уровень N+1.</p>
     *
     * @return список строк заказа
     */
    public List<OrderItemN1> getItems() {
        return items;
    }
}