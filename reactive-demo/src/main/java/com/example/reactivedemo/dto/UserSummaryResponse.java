package com.example.reactivedemo.dto;

import com.example.reactivedemo.domain.Order;
import com.example.reactivedemo.domain.User;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>Агрегированный ответ: пользователь + список заказов + итоговая сумма.</p>
 *
 * <p>Собирается в {@link com.example.reactivedemo.service.UserService#getUserSummary}
 * после цепочки {@code flatMap} → {@code collectList} → {@code map}.</p>
 *
 * @param id          идентификатор пользователя
 * @param email       email
 * @param fullName    имя
 * @param orderCount  количество заказов
 * @param totalAmount сумма всех заказов
 * @param orders      список заказов
 */
public record UserSummaryResponse(
        Long id,
        String email,
        String fullName,
        int orderCount,
        BigDecimal totalAmount,
        List<OrderResponse> orders
) {

    /**
     * <p>Сборка DTO из загруженных сущностей.</p>
     *
     * @param user   пользователь (уже получен из БД)
     * @param orders список заказов (уже собран в {@link java.util.List})
     * @return сводка для JSON
     */
    public static UserSummaryResponse of(User user, List<Order> orders) {
        BigDecimal total = orders.stream()
                .map(Order::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<OrderResponse> orderResponses = orders.stream().map(OrderResponse::from).toList();
        return new UserSummaryResponse(
                user.id(),
                user.email(),
                user.fullName(),
                orders.size(),
                total,
                orderResponses
        );
    }
}
