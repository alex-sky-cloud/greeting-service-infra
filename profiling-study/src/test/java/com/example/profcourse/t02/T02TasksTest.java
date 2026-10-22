package com.example.profcourse.t02;

import com.example.profcourse.common.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Проверка задач темы 02. Условия: {@code docs/profiling-course/02-jdk-tools-tasks.md}. */
class T02TasksTest {

    @Nested
    @DisplayName("Задача 1. Сводка по jstat")
    class Task1 {

        @Test
        @DisplayName("лаборатория leak, 205 секунд: 575 молодых и 44 полных сборки, старое поколение 83 → 99 %")
        void leakWholeWindow() {
            GcUtilReport report = new T02GcUtil().analyze(Fixtures.lines("leak-jstat.txt"));

            assertThat(report.seconds()).isCloseTo(205.3, within(0.01));
            assertThat(report.youngGcs()).isEqualTo(575);
            assertThat(report.fullGcs()).isEqualTo(44);
            assertThat(report.gcTimePercent()).isCloseTo(1.887, within(0.01));
            assertThat(report.oldFirstPercent()).isCloseTo(83.06, within(0.001));
            assertThat(report.oldLastPercent()).isCloseTo(99.37, within(0.001));
        }

        @Test
        @DisplayName("последние 10 секунд той же записи: сборки заняли больше 20 % времени")
        void leakLastSeconds() {
            List<String> all = Fixtures.lines("leak-jstat.txt");
            List<String> tail = new ArrayList<>();
            tail.add(all.getFirst());
            tail.addAll(all.subList(all.size() - 3, all.size()));

            GcUtilReport report = new T02GcUtil().analyze(tail);

            assertThat(report.seconds()).isCloseTo(10.0, within(0.01));
            assertThat(report.youngGcs()).isEqualTo(39);
            assertThat(report.fullGcs()).isEqualTo(39);
            assertThat(report.gcTimePercent()).isCloseTo(21.58, within(0.01));
        }

        @Test
        @DisplayName("лаборатория churn: сборок много, полных нет, старое поколение стоит на месте")
        void churn() {
            GcUtilReport report = new T02GcUtil().analyze(Fixtures.lines("churn-jstat.txt"));

            assertThat(report.seconds()).isCloseTo(28.0, within(0.01));
            assertThat(report.youngGcs()).isEqualTo(1789);
            assertThat(report.fullGcs()).isZero();
            assertThat(report.gcTimePercent()).isCloseTo(4.921, within(0.01));
            assertThat(report.oldFirstPercent()).isCloseTo(4.53, within(0.001));
            assertThat(report.oldLastPercent()).isCloseTo(4.80, within(0.001));
        }

        @Test
        @DisplayName("десятичная точка вместо запятой и повтор заголовка тоже читаются")
        void dotsAndRepeatedHeader() {
            List<String> lines = List.of(
                    "Timestamp         S0     S1     E      O      M     CCS    YGC     YGCT     FGC    FGCT     CGC    CGCT       GCT   ",
                    "           10.0      -   1.00  10.00  20.00  90.00  80.00     10     0.100     0     0.000     0     0.000     0.100",
                    "Timestamp         S0     S1     E      O      M     CCS    YGC     YGCT     FGC    FGCT     CGC    CGCT       GCT   ",
                    "           20.0      -   1.00  10.00  25.00  90.00  80.00     30     0.400     1     0.100     0     0.000     0.600");

            GcUtilReport report = new T02GcUtil().analyze(lines);

            assertThat(report.seconds()).isCloseTo(10.0, within(0.001));
            assertThat(report.youngGcs()).isEqualTo(20);
            assertThat(report.fullGcs()).isEqualTo(1);
            assertThat(report.gcTimePercent()).isCloseTo(5.0, within(0.001));
            assertThat(report.oldFirstPercent()).isCloseTo(20.0, within(0.001));
            assertThat(report.oldLastPercent()).isCloseTo(25.0, within(0.001));
        }
    }

    @Nested
    @DisplayName("Задача 2. Что выросло между двумя гистограммами")
    class Task2 {

        @Test
        @DisplayName("лаборатория leak, 14 → 82 секунды: сессия, её Instant и узел карты растут одинаково")
        void leakGrowers() {
            List<Growth> top = new T02Histogram().topGrowers(
                    Fixtures.text("leak-histogram-14s.txt"), Fixtures.text("leak-histogram-82s.txt"), 5);

            assertThat(top).containsExactly(
                    new Growth("[B", 112_408, 22_485_592),
                    new Growth("java.lang.String", 74_931, 1_798_344),
                    new Growth("com.example.profcourse.t03.UserSession", 37_477, 899_448),
                    new Growth("java.time.Instant", 37_477, 899_448),
                    new Growth("java.util.concurrent.ConcurrentHashMap$Node", 37_475, 899_400));
        }

        @Test
        @DisplayName("класс, которого не было в первой гистограмме, — рост от нуля; уменьшение не попадает")
        void newAndShrinking() {
            String before = """
                     num     #instances         #bytes  class name (module)
                    -------------------------------------------------------
                       1:           100          10000  [B (java.base@27)
                       2:            50           1200  java.lang.String (java.base@27)
                    Total           150          11200
                    """;
            String after = """
                     num     #instances         #bytes  class name (module)
                    -------------------------------------------------------
                       1:            10           1000  [B (java.base@27)
                       2:            70           1680  java.lang.String (java.base@27)
                       3:             5            120  com.example.shop.Basket
                    Total            85           2800
                    """;

            assertThat(new T02Histogram().topGrowers(before, after, 10)).containsExactly(
                    new Growth("java.lang.String", 20, 480),
                    new Growth("com.example.shop.Basket", 5, 120));
        }
    }
}
