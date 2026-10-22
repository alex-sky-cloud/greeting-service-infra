package com.example.profcourse.common;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.concurrent.Callable;

/** Замеры для тестов: время и байты, выделенные текущим потоком. */
public final class Measure {

    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private Measure() {
    }

    public record Timed<T>(T value, Duration took) {
    }

    public static <T> Timed<T> time(Callable<T> action) throws Exception {
        long start = System.nanoTime();
        T value = action.call();
        return new Timed<>(value, Duration.ofNanos(System.nanoTime() - start));
    }

    /** Лучшее время из {@code runs} запусков: первые запуски медленнее из-за JIT. */
    public static Duration best(int runs, Callable<?> action) throws Exception {
        Duration best = null;
        for (int i = 0; i < runs; i++) {
            Duration took = time(action).took();
            if (best == null || took.compareTo(best) < 0) {
                best = took;
            }
        }
        return best;
    }

    public interface Action {
        void run() throws Exception;
    }

    /** Сколько байт в куче выделил текущий поток, пока выполнялось действие. */
    public static long allocatedBytes(Action action) throws Exception {
        long before = THREADS.getCurrentThreadAllocatedBytes();
        action.run();
        return THREADS.getCurrentThreadAllocatedBytes() - before;
    }
}
