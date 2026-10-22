package com.example.profcourse.t04;

import com.example.profcourse.common.Slow;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Переводы между счетами в том виде, в каком они ушли в продакшен. Лаборатория {@code deadlock} запускает именно их.
 */
public class LegacyTransfers {

    public void transfer(Account from, Account to, long amount) {
        synchronized (from) {
            Slow.sleep(10); // проверка лимитов в антифроде
            synchronized (to) {
                if (from.balance() < amount) {
                    throw new IllegalStateException("Недостаточно средств на " + from.id());
                }
                from.withdraw(amount);
                to.deposit(amount);
            }
        }
    }
}
