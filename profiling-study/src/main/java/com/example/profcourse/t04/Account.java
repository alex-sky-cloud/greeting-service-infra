package com.example.profcourse.t04;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Счёт клиента. Сам себя от одновременного изменения не защищает.
 */
public final class Account {

    private final String id;
    private long balance;

    public Account(String id, long balance) {
        this.id = id;
        this.balance = balance;
    }

    public String id() {
        return id;
    }

    public long balance() {
        return balance;
    }

    void add(long delta) {
        balance += delta;
    }

    /** Списать: баланс уменьшается на {@code amount}. */
    public void withdraw(long amount) {
        add(-amount);
    }

    /** Зачислить: баланс увеличивается на {@code amount}. */
    public void deposit(long amount) {
        add(amount);
    }
}
