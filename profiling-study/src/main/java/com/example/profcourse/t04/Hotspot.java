package com.example.profcourse.t04;

/**
 * Готовый класс, менять не нужно.
 *
 * @param state   состояние потоков, как в дампе: {@code RUNNABLE}, {@code WAITING} …
 * @param frame   первый кадр стека из кода приложения ({@code com.example.…}, кроме служебного
 *                {@code com.example.profcourse.common}), без номера строки
 * @param threads сколько потоков стоят в этом месте в этом состоянии
 */
public record Hotspot(Thread.State state, String frame, int threads) {
}
