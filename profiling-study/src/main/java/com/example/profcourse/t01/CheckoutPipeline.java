package com.example.profcourse.t01;

import com.example.profcourse.common.Slow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Оформление заказа из четырёх шагов. Каждый шаг блокирует поток паузой и пишет своё имя в журнал вызовов.
 *
 * <table>
 *   <tr><th>Шаг</th><th>Что делает</th><th>Пауза</th></tr>
 *   <tr><td>{@code validate}</td><td>проверка корзины</td><td>5 мс</td></tr>
 *   <tr><td>{@code reserveStock}</td><td>резерв на складе</td><td>20 мс</td></tr>
 *   <tr><td>{@code saveOrder}</td><td>запись в БД</td><td>15 мс</td></tr>
 *   <tr><td>{@code chargeCard}</td><td>платёжный шлюз</td><td>120 мс</td></tr>
 * </table>
 */
public class CheckoutPipeline {

    private final List<String> calls = new CopyOnWriteArrayList<>();

    public void validate(String orderId) {
        step("validate", 5);
    }

    public void reserveStock(String orderId) {
        step("reserveStock", 20);
    }

    public void saveOrder(String orderId) {
        step("saveOrder", 15);
    }

    public void chargeCard(String orderId) {
        step("chargeCard", 120);
    }

    /** Имена вызванных шагов в порядке вызова. */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    private void step(String name, long millis) {
        calls.add(name);
        Slow.sleep(millis);
    }
}
