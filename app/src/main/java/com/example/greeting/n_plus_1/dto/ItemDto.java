package com.example.greeting.n_plus_1.dto;


import java.math.BigDecimal;

/**
 * DTO строки заказа для передачи через API.
 *
 * @param productName название товара
 * @param qty         количество единиц
 * @param price       цена товара на момент запроса
 */
public record ItemDto(
        String productName,
        Integer qty,
        BigDecimal price
) {}
