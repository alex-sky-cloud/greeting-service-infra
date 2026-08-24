package com.example.reactorworkshop.t01_map_flatmap.domain;

import java.util.List;

/**
 * DTO для {@code Mono.flatMap} / {@code Flux.flatMap}: пользователь плюс имена товаров.
 */
public record T01UserOrdersDto(Long userId, String email, List<String> products) {
}
