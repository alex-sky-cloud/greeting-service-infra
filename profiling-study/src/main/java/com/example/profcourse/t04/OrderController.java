package com.example.profcourse.t04;

import com.example.profcourse.common.Slow;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Оформление заказа в том виде, в каком оно ушло в продакшен: транзакция БД открыта,
 * пока идёт вызов платёжного шлюза. Лаборатория {@code pool} запускает именно его.
 */
public class OrderController {

    private final ConnectionPool pool;

    public OrderController(ConnectionPool pool) {
        this.pool = pool;
    }

    public void placeOrder(String orderId) {
        pool.borrow();
        try {
            insertOrder(orderId);
            chargeCard(orderId);
            markPaid(orderId);
        } finally {
            pool.release();
        }
    }

    private void insertOrder(String orderId) {
        Slow.sleep(5);
    }

    /** Платёжный шлюз отвечает 2 секунды. */
    private void chargeCard(String orderId) {
        Slow.sleep(2_000);
    }

    private void markPaid(String orderId) {
        Slow.sleep(5);
    }
}
