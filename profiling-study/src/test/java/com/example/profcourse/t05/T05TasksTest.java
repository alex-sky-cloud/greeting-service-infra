package com.example.profcourse.t05;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверка задач темы 05. Условия: {@code docs/profiling-course/05-jfr-jmc-tasks.md}. */
class T05TasksTest {

    @Nested
    @DisplayName("Задача 1. Горячие методы по записи JFR")
    class Task1 {

        @Test
        @DisplayName("скоринг 2 секунды: самый горячий метод — моделирование дефолта")
        void hottest(@TempDir Path dir) throws Exception {
            Path file = dir.resolve("scoring.jfr");
            try (Recording recording = new Recording()) {
                recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
                recording.start();
                RiskScoring scoring = new RiskScoring();
                long until = System.nanoTime() + 2_000_000_000L;
                long id = 1;
                int sink = 0;
                while (System.nanoTime() < until) {
                    sink += scoring.score(id++, 120_000, 30_000);
                }
                assertThat(sink).isNotZero();
                recording.stop();
                recording.dump(file);
            }

            List<MethodSamples> top = new T05HotMethods().top(file, 3);

            assertThat(top).isNotEmpty().hasSizeLessThanOrEqualTo(3);
            assertThat(top.getFirst().method()).isEqualTo("com.example.profcourse.t05.RiskScoring.simulate");
            long all = RecordingFile.readAllEvents(file).stream()
                    .filter(e -> e.getEventType().getName().equals("jdk.ExecutionSample")).count();
            assertThat(top.getFirst().samples()).as("доля сэмплов simulate").isGreaterThan(all / 2);
            assertThat(top).isSortedAccordingTo((x, y) -> Long.compare(y.samples(), x.samples()));
        }
    }

    @Nested
    @DisplayName("Задача 2. Запись после медленного запроса")
    class Task2 {

        @Test
        @DisplayName("постоянная запись 5 минут / 50 МБ; дамп только по медленному запросу и не чаще раза в интервал")
        void dumpsOnSlowRequest(@TempDir Path dir) throws Exception {
            try (T05IncidentRecorder recorder = new T05IncidentRecorder(dir, Duration.ofMillis(800))) {
                recorder.start();

                Recording running = FlightRecorder.getFlightRecorder().getRecordings().stream()
                        .filter(r -> r.getState() == RecordingState.RUNNING)
                        .findFirst().orElseThrow();
                assertThat(running.getMaxAge()).isEqualTo(Duration.ofMinutes(5));
                assertThat(running.getMaxSize()).isEqualTo(50L * 1024 * 1024);
                assertThat(running.isToDisk()).isTrue();
                assertThat(running.getSettings()).containsEntry("jdk.GarbageCollection#enabled", "true");

                busy(300);
                assertThat(recorder.onRequest("r-1", Duration.ofMillis(120))).as("быстрый запрос").isEmpty();

                Optional<Path> first = recorder.onRequest("r-2", Duration.ofMillis(1_700));
                assertThat(first).contains(dir.resolve("incident-r-2.jfr"));
                assertThat(Files.size(first.orElseThrow())).isPositive();
                Set<String> types = RecordingFile.readAllEvents(first.get()).stream()
                        .map(RecordedEvent::getEventType).map(t -> t.getName()).collect(Collectors.toSet());
                assertThat(types).contains("jdk.ExecutionSample", "jdk.GCHeapConfiguration");

                assertThat(recorder.onRequest("r-3", Duration.ofSeconds(3))).as("сразу после дампа").isEmpty();

                Thread.sleep(900);
                assertThat(recorder.onRequest("r-4", Duration.ofMillis(500))).as("ровно 500 мс — медленный")
                        .contains(dir.resolve("incident-r-4.jfr"));
            }

            assertThat(FlightRecorder.getFlightRecorder().getRecordings())
                    .as("после close запись закрыта").noneMatch(r -> r.getState() == RecordingState.RUNNING);
        }

        private static void busy(long millis) {
            long until = System.nanoTime() + millis * 1_000_000;
            double x = 0;
            while (System.nanoTime() < until) {
                x += Math.sqrt(x + 1);
            }
            assertThat(x).isPositive();
        }
    }
}
