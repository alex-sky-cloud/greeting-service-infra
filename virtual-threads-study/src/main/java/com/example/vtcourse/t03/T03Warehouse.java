package com.example.vtcourse.t03;

import com.example.vtcourse.common.Todo;

/**
 * Тема 03. Практика: задача 1.
 * Условия: {@code virtual-threads-study/docs/virtual-threads-course/03-locks-pinning-tasks.md}.
 */
public class T03Warehouse {

    private final ReservationLog log;
    private int stock;

    public T03Warehouse(int stock, ReservationLog log) {
        this.stock = stock;
        this.log = log;
    }

    /** Задача 1. Резерв единицы товара под заказ. */
    public boolean reserve(String orderId) throws InterruptedException {
        throw Todo.task("03.1");
    }

    /** Остаток; тест вызывает его после того, как все резервы завершились. */
    public synchronized int remaining() {
        return stock;
    }
}
