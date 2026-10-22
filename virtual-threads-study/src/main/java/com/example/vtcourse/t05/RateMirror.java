package com.example.vtcourse.t05;

import com.example.vtcourse.common.CallStats;
import com.example.vtcourse.common.Slow;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Зеркало сервиса курсов валют. Отвечает через {@code delayMillis}: курсом {@code rate}
 * или, если {@code rate == null}, ошибкой {@link MirrorUnavailableException}.
 */
public class RateMirror {

    private final String name;
    private final long delayMillis;
    private final String rate;
    private final CallStats stats = new CallStats();

    public RateMirror(String name, long delayMillis, String rate) {
        this.name = name;
        this.delayMillis = delayMillis;
        this.rate = rate;
    }

    /** Курс валюты, например {@code "92.35"}. */
    public String rate(String currency) throws InterruptedException {
        Slow.call(stats, delayMillis);
        if (rate == null) {
            throw new MirrorUnavailableException(name);
        }
        return rate;
    }

    public String name() {
        return name;
    }

    public CallStats stats() {
        return stats;
    }
}
