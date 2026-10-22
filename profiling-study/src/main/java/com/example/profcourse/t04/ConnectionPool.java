package com.example.profcourse.t04;

import java.util.concurrent.Semaphore;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Пул соединений с БД: не больше {@code size} соединений одновременно, остальные потоки ждут в очереди.
 */
public class ConnectionPool {

    private final Semaphore connections;

    public ConnectionPool(int size) {
        this.connections = new Semaphore(size, true);
    }

    public void borrow() {
        connections.acquireUninterruptibly();
    }

    public void release() {
        connections.release();
    }
}
