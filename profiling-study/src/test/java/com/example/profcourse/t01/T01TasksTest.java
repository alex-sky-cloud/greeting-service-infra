package com.example.profcourse.t01;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/** Проверка задач темы 01. Условия: {@code docs/profiling-course/01-before-profiler-tasks.md}. */
class T01TasksTest {

    @Nested
    @DisplayName("Задача 1. Куда смотреть дальше")
    class Task1 {

        static Stream<Arguments> incidents() {
            return Stream.of(
                    //          p99   base  jvm host  gc  act max wait  dep  depBase
                    arguments("ночь, всё как обычно",
                            new ServiceSnapshot(260, 200, 20, 25, 1, 3, 20, 0, 40, 40), Verdict.NO_DEGRADATION),
                    arguments("на машине антивирус, JVM почти не нагружена",
                            new ServiceSnapshot(900, 200, 15, 97, 1, 5, 20, 0, 40, 40), Verdict.OTHER_PROCESS),
                    arguments("полные сборки после релиза кэша",
                            new ServiceSnapshot(1500, 200, 85, 90, 34, 20, 20, 12, 40, 40), Verdict.GC_PRESSURE),
                    arguments("банк отвечает 3 с, пул занят из-за него",
                            new ServiceSnapshot(3200, 200, 10, 15, 1, 20, 20, 45, 3000, 150), Verdict.DEPENDENCY_SLOW),
                    arguments("HTTP внутри транзакции: БД быстрая, пул исчерпан",
                            new ServiceSnapshot(2500, 200, 12, 18, 1, 20, 20, 60, 45, 40), Verdict.POOL_SATURATED),
                    arguments("новая выгрузка съедает CPU",
                            new ServiceSnapshot(1200, 200, 92, 95, 3, 4, 20, 0, 40, 40), Verdict.CPU_IN_CODE),
                    arguments("CPU свободен, метрики чистые",
                            new ServiceSnapshot(1800, 200, 9, 12, 1, 6, 20, 0, 42, 40), Verdict.WAITING_UNKNOWN),
                    arguments("граница: ровно в полтора раза выше нормы — ещё норма",
                            new ServiceSnapshot(300, 200, 20, 25, 1, 3, 20, 0, 40, 40), Verdict.NO_DEGRADATION),
                    arguments("граница: пул полный, но очереди нет",
                            new ServiceSnapshot(700, 200, 30, 35, 2, 20, 20, 0, 50, 40), Verdict.WAITING_UNKNOWN));
        }

        @ParameterizedTest(name = "{0} → {2}")
        @MethodSource("incidents")
        void classify(String title, ServiceSnapshot snapshot, Verdict expected) {
            assertThat(new T01Triage().classify(snapshot)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Задача 2. Из чего складывается оформление заказа")
    class Task2 {

        @Test
        @DisplayName("шаги вызваны по одному разу в рабочем порядке, разбивка от долгого к короткому")
        void breakdown() {
            CheckoutPipeline pipeline = new CheckoutPipeline();

            List<StageTime> stages = new T01CheckoutTimer(pipeline).breakdown("ORD-1001");

            assertThat(pipeline.calls()).containsExactly("validate", "reserveStock", "saveOrder", "chargeCard");
            assertThat(stages).extracting(StageTime::stage)
                    .containsExactly("chargeCard", "reserveStock", "saveOrder", "validate");
            assertThat(stages.getFirst().took()).isBetween(Duration.ofMillis(118), Duration.ofMillis(200));
            assertThat(stages.getFirst().sharePercent()).isBetween(65, 80);
            assertThat(stages.getLast().sharePercent()).isBetween(2, 6);
            assertThat(stages.stream().mapToInt(StageTime::sharePercent).sum()).isBetween(98, 102);
        }
    }
}
