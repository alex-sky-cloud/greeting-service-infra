package com.example.profcourse.t06;

import java.util.ArrayList;
import java.util.List;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Ночная сверка платежей с выпиской банка в том виде, в каком она ушла в продакшен.
 * Возвращает номера платежей, которых нет в выписке или которые зачислены на другую сумму, — в порядке платежей.
 * Лаборатория {@code reconcile} запускает именно её.
 */
public class LegacyReconciler {

    public List<String> unmatched(List<Payment> payments, List<StatementLine> statement) {
        List<String> result = new ArrayList<>();
        for (Payment payment : payments) {
            boolean found = false;
            for (StatementLine line : statement) {
                if (line.paymentId().equals(payment.id()) && line.amountKopecks() == payment.amountKopecks()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                result.add(payment.id());
            }
        }
        return result;
    }
}
