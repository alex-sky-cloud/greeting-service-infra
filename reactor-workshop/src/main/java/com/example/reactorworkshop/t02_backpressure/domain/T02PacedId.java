package com.example.reactorworkshop.t02_backpressure.domain;

/**
 * Элемент синтетического потока: значение и пояснение, что {@code limitRate} не drop.
 */
public record T02PacedId(int value, String note) {
}
