package com.example.vtcourse.t02;

import com.example.vtcourse.common.CallStats;
import com.example.vtcourse.common.Slow;

import java.util.Map;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Три независимые системы с данными клиента. Каждая отвечает со своей задержкой.
 *
 * <table>
 *   <tr><th>Метод</th><th>Задержка</th></tr>
 *   <tr><td>{@link #name(int)}</td><td>300 мс</td></tr>
 *   <tr><td>{@link #orderCount(int)}</td><td>200 мс</td></tr>
 *   <tr><td>{@link #bonusBalance(int)}</td><td>250 мс</td></tr>
 * </table>
 */
public class CustomerSystems {

    private static final Map<Integer, String> NAMES = Map.of(1, "Анна Смирнова", 2, "Борис Котов");
    private static final Map<Integer, Integer> ORDERS = Map.of(1, 4, 2, 3);
    private static final Map<Integer, Integer> BONUSES = Map.of(1, 500, 2, 0);

    private final CallStats stats = new CallStats();

    public String name(int customerId) throws InterruptedException {
        Slow.call(stats, 300);
        return NAMES.get(customerId);
    }

    public int orderCount(int customerId) throws InterruptedException {
        Slow.call(stats, 200);
        return ORDERS.get(customerId);
    }

    public int bonusBalance(int customerId) throws InterruptedException {
        Slow.call(stats, 250);
        return BONUSES.get(customerId);
    }

    public CallStats stats() {
        return stats;
    }
}
