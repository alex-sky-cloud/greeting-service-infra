package com.example.profcourse.t06;

import com.example.profcourse.common.Measure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверка задач темы 06. Условия: {@code docs/profiling-course/06-cpu-tasks.md}. */
class T06TasksTest {

    @Nested
    @DisplayName("Задача 1. Выгрузка для бухгалтерии")
    class Task1 {

        @Test
        @DisplayName("текст совпадает со старой выгрузкой символ в символ")
        void sameText() {
            List<Order> orders = Order.sample(1_200);

            String expected = new LegacyReportBuilder().build(orders);

            assertThat(new T06ReportBuilder().build(orders)).isEqualTo(expected);
            assertThat(expected).startsWith("ORD-000001;01.10.2026 09:01;************7919;100.37\n");
            assertThat(new T06ReportBuilder().build(List.of())).isEmpty();
        }

        @Test
        @DisplayName("8 000 заказов: минимум в 5 раз быстрее старой выгрузки")
        void faster() throws Exception {
            List<Order> orders = Order.sample(8_000);
            LegacyReportBuilder legacy = new LegacyReportBuilder();
            T06ReportBuilder fixed = new T06ReportBuilder();

            Duration before = Measure.best(3, () -> legacy.build(orders));
            Duration after = Measure.best(5, () -> fixed.build(orders));

            System.out.printf("выгрузка 8 000 заказов: было %d мс, стало %d мс%n", before.toMillis(), after.toMillis());
            assertThat(after.multipliedBy(5)).isLessThan(before);
        }
    }

    @Nested
    @DisplayName("Задача 2. Ночная сверка с банком")
    class Task2 {

        @Test
        @DisplayName("расхождения те же, что у старой сверки, в порядке платежей")
        void sameResult() {
            List<Payment> payments = Reconciliation.payments(3_000);
            List<StatementLine> statement = Reconciliation.statement(payments);

            List<String> expected = new LegacyReconciler().unmatched(payments, statement);

            assertThat(new T06Reconciler().unmatched(payments, statement)).isEqualTo(expected);
            assertThat(expected).startsWith("P-000050", "P-000077", "P-000100").hasSize(98);
        }

        @Test
        @DisplayName("10 000 платежей: минимум в 20 раз быстрее старой сверки")
        void faster() throws Exception {
            List<Payment> payments = Reconciliation.payments(10_000);
            List<StatementLine> statement = Reconciliation.statement(payments);
            LegacyReconciler legacy = new LegacyReconciler();
            T06Reconciler fixed = new T06Reconciler();

            Duration before = Measure.best(1, () -> legacy.unmatched(payments, statement));
            Duration after = Measure.best(5, () -> fixed.unmatched(payments, statement));

            System.out.printf("сверка 10 000 платежей: было %d мс, стало %d мс%n", before.toMillis(), after.toMillis());
            assertThat(after.multipliedBy(20)).isLessThan(before);
        }
    }
}
