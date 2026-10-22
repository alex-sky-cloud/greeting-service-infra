package com.example.profcourse.t02;

/**
 * Готовый класс, менять не нужно.
 *
 * @param className      имя класса, как в гистограмме, без модуля в скобках: {@code [B}, {@code java.lang.String}
 * @param instancesDelta на сколько выросло число объектов
 * @param bytesDelta     на сколько выросло число байт
 */
public record Growth(String className, long instancesDelta, long bytesDelta) {
}
