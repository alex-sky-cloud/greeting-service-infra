package com.example.reactorworkshop.t01_map_flatmap.domain;

/**
 * DTO ответа {@code GET /api/t01/map}: результат синхронного {@code map}, не Publisher.
 */
public record T01UserDto(Long id, String email, String displayName) {
}
