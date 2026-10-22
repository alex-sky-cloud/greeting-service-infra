package com.example.vtcourse.t05;

import com.example.vtcourse.common.CallStats;
import com.example.vtcourse.common.RequestContext;
import com.example.vtcourse.common.Slow;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Два клиента внешних систем. Каждый вызов запоминает пользователя текущего запроса.
 *
 * <table>
 *   <tr><th>Метод</th><th>Задержка</th><th>Особенность</th></tr>
 *   <tr><td>{@link #profileName(int)}</td><td>300 мс</td><td></td></tr>
 *   <tr><td>{@link #orderCount(int)}</td><td>200 мс</td><td>клиент 13: через 50 мс {@link OrdersUnavailableException}</td></tr>
 * </table>
 */
public class CustomerClients {

    public static final int BROKEN_CUSTOMER = 13;

    private static final Map<Integer, String> NAMES = Map.of(1, "Анна Смирнова", 2, "Борис Котов", 13, "Нина Осипова");
    private static final Map<Integer, Integer> ORDERS = Map.of(1, 4, 2, 3);

    private final CallStats stats = new CallStats();
    private final List<String> seenUsers = new CopyOnWriteArrayList<>();

    public String profileName(int customerId) throws InterruptedException {
        seenUsers.add(RequestContext.currentUser());
        Slow.call(stats, 300);
        return NAMES.get(customerId);
    }

    public int orderCount(int customerId) throws InterruptedException {
        seenUsers.add(RequestContext.currentUser());
        if (customerId == BROKEN_CUSTOMER) {
            Slow.call(stats, 50);
            throw new OrdersUnavailableException(customerId);
        }
        Slow.call(stats, 200);
        return ORDERS.get(customerId);
    }

    /** Пользователи, от имени которых пришли вызовы, в порядке вызовов. */
    public List<String> seenUsers() {
        return List.copyOf(seenUsers);
    }

    public CallStats stats() {
        return stats;
    }
}
