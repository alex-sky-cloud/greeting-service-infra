package com.example.profcourse.t07;

/**
 * Готовый класс, менять не нужно.
 *
 * @param method {@code <полное имя класса>.<метод>} — первый кадр стека из кода приложения ({@code com.example.…})
 * @param bytes  сколько байт выделено в этом месте по оценке JFR (сумма поля {@code weight})
 */
public record AllocationSite(String method, long bytes) {
}
