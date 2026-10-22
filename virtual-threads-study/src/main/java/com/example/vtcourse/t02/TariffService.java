package com.example.vtcourse.t02;

import com.example.vtcourse.common.CallStats;
import com.example.vtcourse.common.Slow;

import java.util.Map;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Сервис тарифов партнёра. Один запрос блокирует поток на 100 мс.
 */
public class TariffService {

    public static final Map<String, Integer> PRICES = Map.ofEntries(
            Map.entry("Москва", 350), Map.entry("Казань", 420), Map.entry("Самара", 410),
            Map.entry("Пермь", 480), Map.entry("Уфа", 450), Map.entry("Омск", 560),
            Map.entry("Томск", 590), Map.entry("Тула", 300), Map.entry("Псков", 380),
            Map.entry("Сочи", 520), Map.entry("Курск", 340), Map.entry("Иркутск", 640));

    private final CallStats stats = new CallStats();

    /** Цена доставки в город, руб. */
    public int price(String city) throws InterruptedException {
        Slow.call(stats, 100);
        return PRICES.get(city);
    }

    public CallStats stats() {
        return stats;
    }
}
