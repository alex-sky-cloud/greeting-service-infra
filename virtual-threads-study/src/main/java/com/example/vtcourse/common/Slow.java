package com.example.vtcourse.common;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Блокирующее ожидание, которым готовые классы изображают сеть или диск. Ведёт {@link CallStats}.
 */
public final class Slow {

    private Slow() {
    }

    /** Вызов внешней системы длительностью {@code millis}: пока он идёт, поток заблокирован. */
    public static void call(CallStats stats, long millis) throws InterruptedException {
        stats.enter();
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            stats.interrupted();
            throw e;
        } finally {
            stats.exit();
        }
    }
}
