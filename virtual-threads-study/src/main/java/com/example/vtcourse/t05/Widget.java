package com.example.vtcourse.t05;

import com.example.vtcourse.common.CallStats;
import com.example.vtcourse.common.Slow;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Виджет панели оператора. Загружает своё значение за {@code delayMillis}.
 */
public class Widget {

    private final String title;
    private final long delayMillis;
    private final String value;
    private final CallStats stats = new CallStats();

    public Widget(String title, long delayMillis, String value) {
        this.title = title;
        this.delayMillis = delayMillis;
        this.value = value;
    }

    public String load() throws InterruptedException {
        Slow.call(stats, delayMillis);
        return value;
    }

    public String title() {
        return title;
    }

    public CallStats stats() {
        return stats;
    }
}
