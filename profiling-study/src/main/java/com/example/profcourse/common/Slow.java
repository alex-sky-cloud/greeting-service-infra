package com.example.profcourse.common;

/** Пауза, которой готовые классы изображают сеть, диск или чужую систему. */
public final class Slow {

    private Slow() {
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание прервано", e);
        }
    }
}
