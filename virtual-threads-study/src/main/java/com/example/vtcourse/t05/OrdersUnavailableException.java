package com.example.vtcourse.t05;

/** Готовый класс, менять не нужно. Система заказов не ответила. */
public class OrdersUnavailableException extends RuntimeException {

    public OrdersUnavailableException(int customerId) {
        super("Система заказов недоступна для клиента " + customerId);
    }
}
