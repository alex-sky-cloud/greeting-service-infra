package com.example.profcourse.t06;

/**
 * Готовый класс, менять не нужно. Строка банковской выписки.
 *
 * @param paymentId     номер нашего платежа, который банк указал в назначении
 * @param amountKopecks сумма, которую зачислил банк
 */
public record StatementLine(String paymentId, long amountKopecks) {
}
