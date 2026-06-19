package com.example.reactivedemo.dto;

import com.example.reactivedemo.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * <p>DTO заказа для REST-ответа.</p>
 *
 * @param id          идентификатор заказа
 * @param userId      владелец
 * @param productName товар
 * @param amount      сумма
 * @param createdAt   дата создания
 */
public record OrderResponse(
        Long id,
        Long userId,
        String productName,
        BigDecimal amount,
        Instant createdAt
) {

    /**
     * <p>Маппинг из доменной сущности {@link Order}.</p>
     *
     * @param order сущность из R2DBC
     * @return DTO для JSON
     */
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.userId(),
                order.productName(),
                order.amount(),
                order.createdAt()
        );
    }
}
