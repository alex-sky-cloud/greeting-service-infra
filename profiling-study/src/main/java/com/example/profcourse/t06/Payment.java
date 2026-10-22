package com.example.profcourse.t06;

/**
 * Готовый класс, менять не нужно.
 *
 * @param id            номер платежа в нашей системе
 * @param amountKopecks сумма в копейках
 */
public record Payment(String id, long amountKopecks) {
}
