package com.example.greeting.n_plus_1.controller;

import com.example.greeting.n_plus_1.dto.OrderDto;
import com.example.greeting.n_plus_1.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-контроллер для демонстрации N+1 на схеме {@code iso_demo}.
 *
 * <p>Два endpoint'а возвращают <b>одинаковый JSON</b>, но с кардинально
 * разным числом SQL-запросов под капотом. Разница видна в:</p>
 * <ul>
 *   <li>логах Hibernate ({@code spring.jpa.show-sql=true});</li>
 *   <li>{@code pg_stat_activity} в PostgreSQL;</li>
 *   <li>времени ответа при реальном объёме данных.</li>
 * </ul>
 *
 * <h2>Как наблюдать N+1 в pg_stat_activity</h2>
 * <pre>{@code
 * -- Выполни в psql ВО ВРЕМЯ запроса к /api/orders/n1:
 * SELECT pid, query, state
 * FROM pg_stat_activity
 * WHERE datname = current_database()
 *   AND query NOT LIKE '%pg_stat_activity%'
 * ORDER BY query_start DESC;
 * }</pre>
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Возвращает заказы пользователя, <b>воспроизводя N+1</b>.
     *
     * <p>При 50 заказах × 10 строк выполнит <b>551 SQL-запрос</b>.
     * Используется только для демонстрации антипаттерна.</p>
     *
     * <p>Пример вызова:</p>
     * <pre>{@code
     * GET /api/orders/n1?userId=1
     * }</pre>
     *
     * @param userId идентификатор пользователя
     * @return список заказов с вложенными строками
     */
    @GetMapping("/n1")
    public List<OrderDto> getOrdersN1(@RequestParam ("userId") Long userId) {
        return orderService.getOrdersWithN1Problem(userId);
    }

    /**
     * Возвращает заказы пользователя через {@code JOIN FETCH} — <b>без N+1</b>.
     *
     * <p>Независимо от числа заказов и строк выполнит ровно <b>1 SQL-запрос</b>.</p>
     *
     * <p>Пример вызова:</p>
     * <pre>{@code
     * GET /api/orders/optimized?userId=1
     * }</pre>
     *
     * @param userId идентификатор пользователя
     * @return список заказов с вложенными строками (тот же формат, что у {@link #getOrdersN1})
     */
    @GetMapping("/optimized")
    public List<OrderDto> getOrdersOptimized(@RequestParam ("userId") Long userId) {
        return orderService.getOrdersOptimized(userId);
    }

    @GetMapping("/optimized-entity-graph")
    public List<OrderDto> getOrdersOptimizedWithEntityGraph(@RequestParam ("userId") Long userId) {
        return orderService.getOrdersOptimizedWithEntityGraph(userId);
    }
}
