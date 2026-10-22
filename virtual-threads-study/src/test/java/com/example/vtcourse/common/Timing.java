package com.example.vtcourse.common;

import java.time.Duration;
import java.util.concurrent.Callable;

/** Замер времени вызова в тестах. */
public final class Timing {

    public record Timed<T>(T value, Duration took) {
    }

    private Timing() {
    }

    public static <T> Timed<T> measure(Callable<T> call) throws Exception {
        long start = System.nanoTime();
        T value = call.call();
        return new Timed<>(value, Duration.ofNanos(System.nanoTime() - start));
    }

    /** Время до выброса исключения; исключение возвращается тесту для проверки. */
    public static Timed<Throwable> measureFailure(Callable<?> call) {
        long start = System.nanoTime();
        try {
            call.call();
        } catch (Throwable e) {
            return new Timed<>(e, Duration.ofNanos(System.nanoTime() - start));
        }
        throw new AssertionError("Ожидалось исключение, но вызов завершился успешно");
    }

    /** Есть ли в цепочке причин исключение типа {@code type}. */
    public static boolean causedBy(Throwable error, Class<? extends Throwable> type) {
        for (Throwable e = error; e != null; e = e.getCause()) {
            if (type.isInstance(e)) {
                return true;
            }
        }
        return false;
    }
}
