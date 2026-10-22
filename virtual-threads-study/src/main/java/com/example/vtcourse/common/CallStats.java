package com.example.vtcourse.common;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Счётчики вызовов внешней системы: сколько вызовов, сколько шло одновременно, на каких потоках,
 * сколько прервано. По ним тесты проверяют поведение решения.
 */
public final class CallStats {

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();
    private final AtomicInteger interrupted = new AtomicInteger();
    private final ConcurrentLinkedQueue<Thread> threads = new ConcurrentLinkedQueue<>();

    /** Вызов начался на текущем потоке. */
    public void enter() {
        calls.incrementAndGet();
        threads.add(Thread.currentThread());
        maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
    }

    /** Вызов закончился: успешно, с ошибкой или прерыванием. */
    public void exit() {
        inFlight.decrementAndGet();
    }

    /** Ожидание внутри вызова прервано. */
    public void interrupted() {
        interrupted.incrementAndGet();
    }

    public int calls() {
        return calls.get();
    }

    public int inFlight() {
        return inFlight.get();
    }

    public int maxInFlight() {
        return maxInFlight.get();
    }

    public int interruptedCount() {
        return interrupted.get();
    }

    public List<Thread> threads() {
        return List.copyOf(threads);
    }
}
