package com.example.vtcourse.t03;

import com.example.vtcourse.common.CallStats;
import com.example.vtcourse.common.Slow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Журнал резервов на диске. Запись строки блокирует поток на 20 мс.
 * Строки хранятся в порядке записи.
 */
public class ReservationLog {

    private final CallStats stats = new CallStats();
    private final List<String> lines = new CopyOnWriteArrayList<>();

    public void write(String line) throws InterruptedException {
        Slow.call(stats, 20);
        lines.add(line);
    }

    public List<String> lines() {
        return List.copyOf(lines);
    }

    public CallStats stats() {
        return stats;
    }
}
