package com.example.profcourse.t05;

/**
 * Готовый класс, менять не нужно.
 *
 * @param method  {@code <полное имя класса>.<метод>}, например {@code java.util.HashMap.get}
 * @param samples сколько сэмплов исполнения пришлось на этот метод как на верхний кадр стека
 */
public record MethodSamples(String method, long samples) {
}
