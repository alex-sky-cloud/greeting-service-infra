package com.example.vtcourse.t05;

import com.example.vtcourse.common.RequestContext;
import com.example.vtcourse.common.Timing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверка задач темы 05. Условия: {@code docs/virtual-threads-course/05-structured-concurrency-tasks.md}. */
class T05StructuredTasksTest {

    @Nested
    @DisplayName("Задача 1. Карточка клиента: всё или ничего")
    class Task1 {

        @Test
        @DisplayName("клиент 1: строка быстрее 450 мс, оба вызова от имени anna на виртуальных потоках")
        void success() throws Exception {
            CustomerClients clients = new CustomerClients();
            T05StructuredTasks tasks = new T05StructuredTasks(clients);

            var result = Timing.measure(() -> ScopedValue.where(RequestContext.USER, "anna").call(() -> tasks.card(1)));

            assertThat(result.value()).isEqualTo("Анна Смирнова: заказов 4");
            assertThat(result.took()).isLessThan(Duration.ofMillis(450));
            assertThat(clients.seenUsers()).containsExactly("anna", "anna");
            assertThat(clients.stats().threads()).hasSize(2).allMatch(Thread::isVirtual);
        }

        @Test
        @DisplayName("клиент 13: ошибка заказов за 200 мс, профиль прерван, после ответа ничего не работает")
        void failureCancelsSibling() {
            CustomerClients clients = new CustomerClients();
            T05StructuredTasks tasks = new T05StructuredTasks(clients);

            var failure = Timing.measureFailure(() -> tasks.card(CustomerClients.BROKEN_CUSTOMER));

            assertThat(Timing.causedBy(failure.value(), OrdersUnavailableException.class))
                    .as("в причинах — OrdersUnavailableException, а не %s", failure.value()).isTrue();
            assertThat(failure.took()).isLessThan(Duration.ofMillis(200));
            assertThat(clients.stats().interruptedCount()).as("прерванных вызовов профиля").isEqualTo(1);
            assertThat(clients.stats().inFlight()).as("вызовов в работе после ответа").isZero();
        }
    }

    @Nested
    @DisplayName("Задача 2. Курс валюты с самого быстрого зеркала")
    class Task2 {

        @Test
        @DisplayName("ответ быстрого зеркала, упавшее не мешает, медленное прервано")
        void fastestWins() throws Exception {
            RateMirror slow = new RateMirror("mirror-A", 600, "92.40");
            RateMirror fast = new RateMirror("mirror-B", 100, "92.35");
            RateMirror broken = new RateMirror("mirror-C", 10, null);

            var result = Timing.measure(() -> new T05StructuredTasks(new CustomerClients())
                    .fastestRate("USD", List.of(slow, fast, broken)));

            assertThat(result.value()).isEqualTo("92.35");
            assertThat(result.took()).isLessThan(Duration.ofMillis(300));
            assertThat(slow.stats().interruptedCount()).as("медленное зеркало прервано").isEqualTo(1);
            assertThat(slow.stats().inFlight()).isZero();
        }

        @Test
        @DisplayName("все зеркала упали — ошибка с причиной MirrorUnavailableException")
        void allFailed() {
            List<RateMirror> mirrors = List.of(new RateMirror("mirror-A", 20, null), new RateMirror("mirror-B", 40, null));

            var failure = Timing.measureFailure(() -> new T05StructuredTasks(new CustomerClients()).fastestRate("USD", mirrors));

            assertThat(Timing.causedBy(failure.value(), MirrorUnavailableException.class))
                    .as("в причинах — MirrorUnavailableException, а не %s", failure.value()).isTrue();
        }
    }

    @Nested
    @DisplayName("Задача 3. Панель оператора за 300 мс")
    class Task3 {

        @Test
        @DisplayName("медленный виджет — прочерк и прерван, порядок как в списке, ответ быстрее 450 мс")
        void slowWidgetDashed() throws Exception {
            Widget orders = new Widget("заказы", 100, "12");
            Widget weather = new Widget("погода", 2000, "+5");
            Widget bonuses = new Widget("бонусы", 200, "500");

            var result = Timing.measure(() -> new T05StructuredTasks(new CustomerClients())
                    .dashboard(List.of(orders, weather, bonuses)));

            assertThat(result.value()).containsExactly("заказы: 12", "погода: —", "бонусы: 500");
            assertThat(result.took()).isBetween(Duration.ofMillis(280), Duration.ofMillis(450));
            assertThat(weather.stats().interruptedCount()).as("медленный виджет прерван").isEqualTo(1);
            assertThat(weather.stats().inFlight()).isZero();
        }

        @Test
        @DisplayName("все виджеты успели — все значения, ответ без ожидания срока")
        void allInTime() throws Exception {
            List<Widget> widgets = List.of(new Widget("заказы", 150, "12"), new Widget("бонусы", 50, "500"));

            var result = Timing.measure(() -> new T05StructuredTasks(new CustomerClients()).dashboard(widgets));

            assertThat(result.value()).containsExactly("заказы: 12", "бонусы: 500");
            assertThat(result.took()).isLessThan(Duration.ofMillis(270));
        }
    }
}
