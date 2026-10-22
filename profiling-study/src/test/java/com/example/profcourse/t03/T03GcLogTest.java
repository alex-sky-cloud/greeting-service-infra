package com.example.profcourse.t03;

import com.example.profcourse.common.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Проверка задачи 03.2. Условие: {@code docs/profiling-course/03-memory-tasks.md}. */
@DisplayName("Задача 2. Утечка или просто мало памяти: разбор GC-лога")
class T03GcLogTest {

    @Test
    @DisplayName("лаборатория leak: 23 полные сборки, после каждой занято всё больше — утечка")
    void leak() {
        GcLogReport report = new T03GcLog().analyze(Fixtures.lines("leak-gc.log"));

        assertThat(report.youngPauses()).isEqualTo(637);
        assertThat(report.fullPauses()).isEqualTo(23);
        assertThat(report.totalPauseMs()).isCloseTo(2677.466, within(0.01));
        assertThat(report.maxPauseMs()).isCloseTo(65.122, within(0.001));
        assertThat(report.heapAfterFullMb()).containsExactly(
                7L, 7L, 33L, 85L, 90L, 91L, 91L, 91L, 91L, 91L, 91L, 92L,
                92L, 92L, 92L, 92L, 92L, 92L, 92L, 92L, 92L, 92L, 93L);
        assertThat(report.leakSuspected()).isTrue();
    }

    @Test
    @DisplayName("лаборатория churn: только молодые сборки — утечки нет")
    void churn() {
        GcLogReport report = new T03GcLog().analyze(Fixtures.lines("churn-gc.log"));

        assertThat(report.youngPauses()).isEqualTo(2180);
        assertThat(report.fullPauses()).isZero();
        assertThat(report.totalPauseMs()).isCloseTo(1658.434, within(0.01));
        assertThat(report.maxPauseMs()).isCloseTo(16.539, within(0.001));
        assertThat(report.heapAfterFullMb()).isEmpty();
        assertThat(report.leakSuspected()).isFalse();
    }

    @Test
    @DisplayName("после полных сборок занято то больше, то меньше — не утечка")
    void notMonotonic() {
        List<String> lines = List.of(
                "[10.000s][info][gc] GC(1) Pause Full (G1 Compaction Pause) 95M->80M(96M) 40.000ms",
                "[20.000s][info][gc] GC(2) Pause Full (G1 Compaction Pause) 95M->72M(96M) 41.000ms",
                "[30.000s][info][gc] GC(3) Pause Full (G1 Compaction Pause) 95M->81M(96M) 42.000ms");

        GcLogReport report = new T03GcLog().analyze(lines);

        assertThat(report.heapAfterFullMb()).containsExactly(80L, 72L, 81L);
        assertThat(report.leakSuspected()).isFalse();
    }

    @Test
    @DisplayName("растёт, но занято меньше 70 % кучи — рано говорить об утечке")
    void growingButLow() {
        List<String> lines = List.of(
                "[10.000s][info][gc] GC(1) Pause Full (System.gc()) 300M->200M(1024M) 90.000ms",
                "[20.000s][info][gc] GC(2) Pause Full (System.gc()) 400M->250M(1024M) 95.000ms",
                "[30.000s][info][gc] GC(3) Pause Full (System.gc()) 500M->300M(1024M) 99.000ms",
                "[30.500s][info][gc] GC(4) Pause Young (Normal) (G1 Evacuation Pause) 350M->310M(1G) 5.500ms",
                "[31.000s][info][gc] GC(5) Concurrent Mark Cycle 120.000ms");

        GcLogReport report = new T03GcLog().analyze(lines);

        assertThat(report.youngPauses()).isEqualTo(1);
        assertThat(report.fullPauses()).isEqualTo(3);
        assertThat(report.totalPauseMs()).isCloseTo(289.5, within(0.001));
        assertThat(report.heapAfterFullMb()).containsExactly(200L, 250L, 300L);
        assertThat(report.leakSuspected()).isFalse();
    }
}
