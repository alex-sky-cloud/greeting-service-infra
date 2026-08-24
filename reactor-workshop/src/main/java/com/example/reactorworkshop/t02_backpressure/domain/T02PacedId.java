package com.example.reactorworkshop.t02_backpressure.domain;

/**
 * Элемент синтетического {@code Flux.range} без БД.
 * Нужен, чтобы увидеть: {@code limitRate} режет {@code request(n)}, но не drop — все id доходят.
 *
 * @param value очередной id из диапазона
 * @param note  пояснение студенту ({@code limitRate}, не drop)
 */
public record T02PacedId(int value, String note) {
}
