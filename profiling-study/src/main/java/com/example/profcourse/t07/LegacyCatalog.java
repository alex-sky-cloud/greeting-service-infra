package com.example.profcourse.t07;

import java.util.HashMap;
import java.util.Map;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Цена товара со скидкой в том виде, в каком она ушла в продакшен. Лаборатория {@code catalog} запускает именно её.
 */
public class LegacyCatalog {

    private final PriceList priceList;

    public LegacyCatalog(PriceList priceList) {
        this.priceList = priceList;
    }

    /** Цена в копейках со скидкой {@code discountPercent}, округление вниз; {@code -1}, если товара нет. */
    public long priceFor(String sku, int discountPercent) {
        Map<String, Long> snapshot = new HashMap<>(priceList.asMap()); // «чтобы прайс не поменялся посреди расчёта»
        Long base = snapshot.get(sku.trim().toUpperCase());
        if (base == null) {
            return -1;
        }
        return base * (100 - discountPercent) / 100;
    }
}
