package com.example.profcourse.t06;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Готовый класс, менять не нужно.
 *
 * @param id            номер заказа, {@code ORD-000001}
 * @param cardNumber    номер карты, 16 цифр
 * @param amountKopecks сумма в копейках
 * @param createdAt     когда оформлен
 */
public record Order(String id, String cardNumber, long amountKopecks, LocalDateTime createdAt) {

    /** {@code count} заказов; одинаковый {@code count} — одинаковые данные. */
    public static List<Order> sample(int count) {
        List<Order> orders = new ArrayList<>(count);
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 9, 0);
        for (int i = 1; i <= count; i++) {
            String card = "4276" + String.format("%012d", i * 7919L % 1_000_000_000_000L);
            orders.add(new Order(String.format("ORD-%06d", i), card, 10_000L + i * 37L % 900_000, start.plusMinutes(i)));
        }
        return orders;
    }
}
