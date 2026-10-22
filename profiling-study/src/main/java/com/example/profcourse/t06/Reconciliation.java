package com.example.profcourse.t06;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Учебные данные сверки: {@code count} платежей и выписка банка за тот же день. В выписке:
 * <ul>
 *   <li>нет каждого 50-го платежа ({@code P-000050}, {@code P-000100} …);</li>
 *   <li>у каждого 77-го платежа сумма на 1 копейку меньше ({@code P-000077}, {@code P-000154} …);</li>
 *   <li>строки перемешаны: банк присылает их не в нашем порядке.</li>
 * </ul>
 */
public final class Reconciliation {

    private Reconciliation() {
    }

    public static List<Payment> payments(int count) {
        List<Payment> payments = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            payments.add(new Payment(String.format("P-%06d", i), 50_000L + i * 13L % 70_000));
        }
        return payments;
    }

    public static List<StatementLine> statement(List<Payment> payments) {
        List<StatementLine> lines = new ArrayList<>(payments.size());
        for (int i = 0; i < payments.size(); i++) {
            int number = i + 1;
            Payment p = payments.get(i);
            if (number % 50 == 0) {
                continue;
            }
            long amount = number % 77 == 0 ? p.amountKopecks() - 1 : p.amountKopecks();
            lines.add(new StatementLine(p.id(), amount));
        }
        Collections.shuffle(lines, new Random(42));
        return lines;
    }
}
