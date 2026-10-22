package com.example.profcourse.t07;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Прайс магазина: 50 000 товаров {@code SKU-00001} … {@code SKU-50000}, цена в копейках.
 * Прайс не меняется после создания.
 */
public class PriceList {

    public static final int SIZE = 50_000;

    private final Map<String, Long> prices = new HashMap<>();

    public PriceList() {
        for (int i = 1; i <= SIZE; i++) {
            prices.put(String.format("SKU-%05d", i), 1_000L + i * 113L % 500_000);
        }
    }

    /** Цена в копейках или {@code -1}, если товара нет. */
    public long price(String sku) {
        Long price = prices.get(sku);
        return price == null ? -1 : price;
    }

    /** Весь прайс только для чтения. */
    public Map<String, Long> asMap() {
        return Collections.unmodifiableMap(prices);
    }
}
