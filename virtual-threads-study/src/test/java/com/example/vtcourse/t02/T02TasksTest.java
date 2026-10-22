package com.example.vtcourse.t02;

import com.example.vtcourse.common.Timing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверка задач темы 02. Условия: {@code docs/virtual-threads-course/02-when-and-how-tasks.md}. */
class T02TasksTest {

    private static final List<String> CITIES = List.of(
            "Москва", "Казань", "Самара", "Пермь", "Уфа", "Омск",
            "Томск", "Тула", "Псков", "Сочи", "Курск", "Иркутск");

    @Nested
    @DisplayName("Задача 1. Тарифы партнёра, не больше трёх запросов одновременно")
    class Task1 {

        @Test
        @DisplayName("12 городов: все цены, одновременно ровно 3 запроса, каждый запрос — свой виртуальный поток")
        void limitedByThree() throws Exception {
            TariffService service = new TariffService();

            var result = Timing.measure(() -> new T02TariffTasks(service).quotes(CITIES));

            assertThat(result.value()).containsExactlyInAnyOrderEntriesOf(TariffService.PRICES);
            assertThat(service.stats().calls()).isEqualTo(12);
            assertThat(service.stats().maxInFlight()).as("запросов одновременно").isEqualTo(3);
            assertThat(result.took()).isBetween(Duration.ofMillis(380), Duration.ofMillis(800));
            assertThat(service.stats().threads()).allMatch(Thread::isVirtual);
            assertThat(Set.copyOf(service.stats().threads())).as("разных потоков").hasSize(12);
        }
    }

    @Nested
    @DisplayName("Задача 2. Шапка карточки клиента из трёх систем")
    class Task2 {

        @Test
        @DisplayName("клиент 1: строка из трёх систем быстрее 450 мс, вызовы на виртуальных потоках")
        void threeSystemsAtOnce() throws Exception {
            CustomerSystems systems = new CustomerSystems();

            var result = Timing.measure(() -> new T02CardTasks(systems).cardHeader(1));

            assertThat(result.value()).isEqualTo("Анна Смирнова: заказов 4, бонусов 500");
            assertThat(result.took()).isLessThan(Duration.ofMillis(450));
            assertThat(systems.stats().calls()).isEqualTo(3);
            assertThat(systems.stats().maxInFlight()).isEqualTo(3);
            assertThat(systems.stats().threads()).allMatch(Thread::isVirtual);
        }

        @Test
        @DisplayName("клиент 2: нулевой баланс бонусов")
        void zeroBonuses() throws Exception {
            assertThat(new T02CardTasks(new CustomerSystems()).cardHeader(2))
                    .isEqualTo("Борис Котов: заказов 3, бонусов 0");
        }
    }
}
