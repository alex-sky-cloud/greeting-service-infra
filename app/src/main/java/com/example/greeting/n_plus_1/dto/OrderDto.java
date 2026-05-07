package com.example.greeting.n_plus_1.dto;


import java.util.List;


import java.util.List;

/**
 * DTO заказа с вложенными строками.
 *
 * @param orderId идентификатор заказа
 * @param status статус заказа
 * @param items список строк заказа
 */
public record OrderDto(
        Long orderId,
        String status,
        List<ItemDto> items
) {}