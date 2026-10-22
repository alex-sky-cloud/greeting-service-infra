package com.example.profcourse.t07;

import com.example.profcourse.common.Todo;

/** Задача 07.1. Условие: {@code docs/profiling-course/07-allocations-tasks.md}. */
public class T07Catalog {

    private final PriceList priceList;

    public T07Catalog(PriceList priceList) {
        this.priceList = priceList;
    }

    public long priceFor(String sku, int discountPercent) {
        throw Todo.task("07.1");
    }
}
