package com.example.vtcourse.t04;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Сервис заказов. Пользователя в параметрах не получает: каждое действие записывает в аудит
 * от имени того, кто выполняет текущий запрос.
 */
public class OrderService {

    private final AuditLog audit;

    public OrderService(AuditLog audit) {
        this.audit = audit;
    }

    public void perform(String action) throws InterruptedException {
        Thread.sleep(10);
        audit.record(action);
    }
}
