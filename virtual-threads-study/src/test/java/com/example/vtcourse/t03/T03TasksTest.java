package com.example.vtcourse.t03;

import com.example.vtcourse.common.PinnedEvents;
import com.example.vtcourse.common.Timing;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверка задач темы 03. Условия: {@code docs/virtual-threads-course/03-locks-pinning-tasks.md}. */
class T03TasksTest {

    @Nested
    @DisplayName("Задача 1. Резерв последних единиц товара")
    class Task1 {

        @Test
        @DisplayName("100 заказов на 30 единиц: 30 резервов, журнал по убыванию остатка, без закрепления носителей")
        void exactlyThirty() throws Exception {
            ReservationLog log = new ReservationLog();
            T03Warehouse warehouse = new T03Warehouse(30, log);
            List<Future<Boolean>> answers = new ArrayList<>();

            List<RecordedEvent> pinned = PinnedEvents.during(() -> {
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    IntStream.rangeClosed(1, 100)
                            .forEach(i -> answers.add(executor.submit(() -> warehouse.reserve("order-" + i))));
                }
            });

            int reserved = 0;
            for (Future<Boolean> answer : answers) {
                if (answer.get()) {
                    reserved++;
                }
            }
            assertThat(reserved).isEqualTo(30);
            assertThat(warehouse.remaining()).isZero();
            assertThat(log.lines()).hasSize(30);
            List<String> remainders = log.lines().stream().map(line -> line.substring(line.indexOf(": ") + 2)).toList();
            List<String> expected = IntStream.iterate(29, i -> i >= 0, i -> i - 1).mapToObj(i -> "осталось " + i).toList();
            assertThat(remainders).as("остаток в журнале строго по убыванию").containsExactlyElementsOf(expected);
            assertThat(log.stats().maxInFlight()).as("записей в журнал одновременно").isEqualTo(1);
            assertThat(pinned).as("событий jdk.VirtualThreadPinned").isEmpty();
        }
    }

    @Nested
    @DisplayName("Задача 2. Единственный принтер чеков")
    class Task2 {

        @Test
        @DisplayName("второй чек ждёт не дольше 100 мс и получает отказ, следующий печатается")
        void busyPrinter() throws Exception {
            ReceiptPrinter printer = new ReceiptPrinter();
            T03PrinterTasks tasks = new T03PrinterTasks(printer);

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<String> first = executor.submit(() -> tasks.printReceipt("A-1"));
                Thread.sleep(30);
                var second = Timing.measure(() -> tasks.printReceipt("A-2"));

                assertThat(second.value()).isEqualTo("принтер занят: A-2");
                assertThat(second.took()).isBetween(Duration.ofMillis(90), Duration.ofMillis(250));
                assertThat(first.get()).isEqualTo("напечатан: A-1");
            }

            assertThat(tasks.printReceipt("A-3")).isEqualTo("напечатан: A-3");
            assertThat(printer.printed()).containsExactly("A-1", "A-3");
            assertThat(printer.stats().maxInFlight()).as("печатей одновременно").isEqualTo(1);
        }

        @Test
        @DisplayName("пять касс сразу: печатает одна, остальные получают отказ, принтер не портит чеки")
        void fiveAtOnce() throws Exception {
            ReceiptPrinter printer = new ReceiptPrinter();
            T03PrinterTasks tasks = new T03PrinterTasks(printer);
            List<Future<String>> answers = new ArrayList<>();

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 1; i <= 5; i++) {
                    String id = "B-" + i;
                    answers.add(executor.submit(() -> tasks.printReceipt(id)));
                }
            }

            List<String> texts = new ArrayList<>();
            for (Future<String> answer : answers) {
                texts.add(answer.get());
            }
            assertThat(texts).filteredOn(text -> text.startsWith("напечатан")).hasSize(1);
            assertThat(texts).filteredOn(text -> text.startsWith("принтер занят")).hasSize(4);
            assertThat(printer.stats().maxInFlight()).isEqualTo(1);
        }
    }
}
