package com.example.vtcourse.t04;

import com.example.vtcourse.common.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверка задач темы 04. Условия: {@code docs/virtual-threads-course/04-scoped-values-tasks.md}. */
class T04RequestTasksTest {

    @Nested
    @DisplayName("Задача 1. Действия пользователя в журнале аудита")
    class Task1 {

        @Test
        @DisplayName("все действия — от имени anna; после запроса пользователь не привязан")
        void actionsAsUser() throws Exception {
            AuditLog audit = new AuditLog();
            OrderService orders = new OrderService(audit);

            new T04RequestTasks(orders).handle("anna", List.of("оплата", "отгрузка"));
            orders.perform("ночная сверка");

            assertThat(audit.lines()).containsExactly("anna: оплата", "anna: отгрузка", "anonymous: ночная сверка");
            assertThat(RequestContext.USER.isBound()).isFalse();
        }

        @Test
        @DisplayName("два запроса одновременно не путают пользователей")
        void parallelRequests() throws Exception {
            AuditLog audit = new AuditLog();
            T04RequestTasks tasks = new T04RequestTasks(new OrderService(audit));

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var anna = executor.submit(() -> { tasks.handle("anna", List.of("a1", "a2", "a3")); return null; });
                var boris = executor.submit(() -> { tasks.handle("boris", List.of("b1", "b2", "b3")); return null; });
                anna.get();
                boris.get();
            }

            assertThat(audit.lines()).containsExactlyInAnyOrder(
                    "anna: a1", "anna: a2", "anna: a3", "boris: b1", "boris: b2", "boris: b3");
        }
    }

    @Nested
    @DisplayName("Задача 2. Закрытие дня со служебным шагом")
    class Task2 {

        @Test
        @DisplayName("отчёт и выход — от пользователя, резервная копия — от system")
        void systemStep() throws Exception {
            AuditLog audit = new AuditLog();

            new T04RequestTasks(new OrderService(audit)).closeDay("vera");

            assertThat(audit.lines()).containsExactly("vera: отчёт за день", "system: резервная копия", "vera: выход");
            assertThat(RequestContext.USER.isBound()).isFalse();
        }
    }
}
