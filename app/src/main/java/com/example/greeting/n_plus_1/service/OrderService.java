package com.example.greeting.n_plus_1.service;

import com.example.greeting.n_plus_1.dto.OrderDto;
import com.example.greeting.n_plus_1.dto.ItemDto;

import com.example.greeting.n_plus_1.entity.Order;
import com.example.greeting.n_plus_1.entity.OrderItem;
import com.example.greeting.n_plus_1.entity.Product;
import com.example.greeting.n_plus_1.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис заказов. Намеренно содержит <b>два</b> метода с идентичным
 * результатом, но кардинально разным числом SQL-запросов к БД.
 *
 * <h2>Сравнение методов</h2>
 * <table border="1">
 *   <tr><th>Метод</th><th>SQL-запросов при 50 заказах × 10 строк</th></tr>
 *   <tr><td>{@link #getOrdersWithN1Problem}</td><td>1 + 50 + 500 = <b>551</b></td></tr>
 *   <tr><td>{@link #getOrdersOptimized}</td><td><b>1</b></td></tr>
 * </table>
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * Возвращает заказы пользователя, <b>воспроизводя двухуровневый N+1</b>.
     *
     * <p>Алгоритм и число запросов при {@code N} заказах с {@code M} строками:</p>
     * <ol>
     *   <li><b>1 запрос</b> — {@code SELECT * FROM orders WHERE user_id = ?}</li>
     *   <li><b>N запросов</b> — {@code order.getItems()} для каждого заказа:
     *       {@code SELECT * FROM order_items WHERE order_id = ?}</li>
     *   <li><b>N*M запросов</b> — {@code item.getProduct()} для каждой строки:
     *       {@code SELECT * FROM products WHERE id = ?}</li>
     * </ol>
     *
     * <p>Итого: {@code 1 + N + N*M} запросов. При 50 заказах × 10 строк —
     * <b>551 запрос</b> к PostgreSQL за один HTTP-вызов.</p>
     *
     * <pre>{@code
     * // Именно эти две строки — триггеры N+1:
     * order.getItems()            // <- N SELECT на order_items
     * item.getProduct().getName() // <- N*M SELECT на products
     * }</pre>
     *
     * @param userId идентификатор пользователя
     * @return список DTO заказов
     * @see #getOrdersOptimized(Long)
     */
    @Transactional(readOnly = true)
    public List<OrderDto> getOrdersWithN1Problem(Long userId) {
        List<Order> orders = orderRepository.findByUserId(userId); // запрос 1

        return orders.stream()
                .map(order -> new OrderDto(
                                order.getId(),
                                order.getStatus(),
                                getItems(order) // запрос 2..N+1
                                        .stream()
                                        .map(item -> new ItemDto(
                                                        getItemProduct(item).getName(), // запрос N+2..N+1+N*M
                                                        item.getQty(),
                                                        getItemProduct(item).getPrice()
                                                )
                                        )
                                        .toList()
                        )
                )
                .toList();
    }

    private static Product getItemProduct(OrderItem item) {
        return item.getProduct();
    }

    private static List<OrderItem> getItems(Order order) {

        List<OrderItem> items = order.getItems();

        return items;
    }

    /**
     * Возвращает заказы пользователя <b>без N+1</b> — через {@code JOIN FETCH}.
     *
     * <p>Hibernate выполняет <b>один SQL-запрос</b> с двумя JOIN-ами:</p>
     * <pre>{@code
     * SELECT DISTINCT o.*, i.*, p.*
     * FROM iso_demo.orders o
     * JOIN iso_demo.order_items i ON i.order_id = o.id
     * JOIN iso_demo.products    p ON p.id = i.product_id
     * WHERE o.user_id = ?
     * }</pre>
     *
     * <p>Граф {@code Order -> items -> product} полностью материализован
     * в памяти до начала маппинга в DTO — никаких дополнительных запросов.</p>
     *
     * @param userId идентификатор пользователя
     * @return список DTO заказов (тот же формат, что у {@link #getOrdersWithN1Problem})
     * @see #getOrdersWithN1Problem(Long)
     */
    @Transactional(readOnly = true)
    public List<OrderDto> getOrdersOptimized(Long userId) {

        List<Order> orders = orderRepository.findByUserIdWithItemsAndProducts(userId); // 1 запрос

        return orders.stream()
                .map(order ->
                        new OrderDto(
                                order.getId(),
                                order.getStatus(),
                                getItems(order)
                                        .stream()            // прокси уже загружен, запросов нет
                                        .map(item -> new ItemDto(
                                                        getItemProduct(item).getName(), // прокси уже загружен
                                                        item.getQty(),
                                                        getItemProduct(item).getPrice()
                                                )
                                        )
                                        .toList()
                        )
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getOrdersOptimizedWithEntityGraph(Long userId) {

        List<Order> orders = orderRepository.findByUserIdWithEntityGraph(userId); // 1 запрос

        return orders.stream()
                .map(order ->
                        new OrderDto(
                                order.getId(),
                                order.getStatus(),
                                getItems(order)
                                        .stream()            // прокси уже загружен, запросов нет
                                        .map(item -> new ItemDto(
                                                        getItemProduct(item).getName(), // прокси уже загружен
                                                        item.getQty(),
                                                        getItemProduct(item).getPrice()
                                                )
                                        )
                                        .toList()
                        )
                )
                .toList();
    }
}