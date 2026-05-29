package com.example.greeting.n_plus_1.repository;

import com.example.greeting.n_plus_1.entity.OrderN1;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Репозиторий заказов ({@code iso_demo.orders}).
 *
 * <p>Содержит два принципиально разных метода выборки заказов пользователя:</p>
 * <ul>
 *   <li>{@link #findByUserId(Long)} — стандартный derived query, возвращает
 *       {@code Order} с LAZY-коллекцией {@code items}. При последующем обходе
 *       в сервисе <b>воспроизводит N+1</b>;</li>
 *   <li>{@link #findByUserIdWithItemsAndProducts(Long)} — {@code JOIN FETCH}
 *       по обоим уровням, <b>устраняет N+1</b> одним запросом.</li>
 * </ul>
 */
@Repository
public interface OrderRepositoryN1 extends JpaRepository<OrderN1, Long> {

    /**
     * Возвращает заказы пользователя без жадной загрузки строк и продуктов.
     *
     * <p><b>⚠ Вызывает N+1</b> при последующем обращении к
     * {@code orderN1.getItems()} и {@code item.getProduct()} в цикле.</p>
     *
     * <p>SQL, генерируемый этим методом:</p>
     * <pre>{@code
     * SELECT * FROM iso_demo.orders WHERE user_id = ?
     * }</pre>
     *
     * <p>После чего в сервисе для каждого заказа добавляется:</p>
     * <pre>{@code
     * SELECT * FROM iso_demo.order_lines WHERE order_id = ?   -- x N
     * SELECT * FROM iso_demo.products WHERE id = ?            -- x N*M
     * }</pre>
     *
     * @param userId идентификатор пользователя
     * @return список заказов с LAZY-прокси на items
     */
    List<OrderN1> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("SELECT o FROM OrderN1 o WHERE o.userId = :userId")
    List<OrderN1> findByUserIdWithEntityGraph(@Param("userId") Long userId);

    /**
     * Возвращает заказы пользователя с жадной загрузкой строк и продуктов
     * через {@code JOIN FETCH}.
     *
     * <p>Генерирует <b>один SQL-запрос</b> вместо {@code 1 + N + N*M}:</p>
     * <pre>{@code
     * SELECT DISTINCT o
     * FROM Order o
     * JOIN FETCH o.items i
     * JOIN FETCH i.product
     * WHERE o.userId = :userId
     * }</pre>
     *
     * <p>{@code DISTINCT} необходим, чтобы Hibernate не дублировал
     * объекты {@code Order} в результирующем списке из-за JOIN
     * по коллекции {@code items}.</p>
     *
     * @param userId идентификатор пользователя
     * @return список заказов с полностью загруженным графом
     */
    @Query("""
            SELECT DISTINCT o
            FROM OrderN1 o
            JOIN FETCH o.items i
            JOIN FETCH i.productN1
            WHERE o.userId = :userId
            """)
    List<OrderN1> findByUserIdWithItemsAndProducts(@Param("userId") Long userId);
}
