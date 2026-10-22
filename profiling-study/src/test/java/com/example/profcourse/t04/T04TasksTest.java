package com.example.profcourse.t04;

import com.example.profcourse.common.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/** Проверка задач темы 04. Условия: {@code docs/profiling-course/04-threads-tasks.md}. */
class T04TasksTest {

    @Nested
    @DisplayName("Задача 1. Встречные переводы не зависают")
    class Task1 {

        @Test
        @DisplayName("4 потока, 20 000 встречных переводов: всё завершилось, деньги сошлись")
        void oppositeTransfers() throws Exception {
            Account a = new Account("40817-001", 1_000_000);
            Account b = new Account("40817-002", 1_000_000);
            T04Transfers transfers = new T04Transfers();
            ExecutorService pool = Executors.newFixedThreadPool(4, Thread.ofPlatform().daemon().name("transfer-", 1).factory());
            List<Future<?>> jobs = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                boolean forward = t % 2 == 0;
                jobs.add(pool.submit(() -> {
                    for (int i = 0; i < 5_000; i++) {
                        if (forward) {
                            transfers.transfer(a, b, 3);
                        } else {
                            transfers.transfer(b, a, 3);
                        }
                    }
                }));
            }
            try {
                for (Future<?> job : jobs) {
                    job.get(20, TimeUnit.SECONDS);
                }
            } catch (TimeoutException e) {
                long[] deadlocked = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
                fail("Переводы не завершились за 20 с; потоков во взаимной блокировке: "
                        + (deadlocked == null ? 0 : deadlocked.length));
            } finally {
                pool.shutdownNow();
            }

            assertThat(ManagementFactory.getThreadMXBean().findDeadlockedThreads()).isNull();
            assertThat(a.balance()).isEqualTo(1_000_000);
            assertThat(b.balance()).isEqualTo(1_000_000);
        }

        @Test
        @DisplayName("денег не хватает: отказ, балансы не изменились")
        void insufficientFunds() {
            Account a = new Account("40817-001", 100);
            Account b = new Account("40817-002", 0);

            assertThatThrownBy(() -> new T04Transfers().transfer(a, b, 101)).isInstanceOf(IllegalStateException.class);

            assertThat(a.balance()).isEqualTo(100);
            assertThat(b.balance()).isZero();
        }
    }

    @Nested
    @DisplayName("Задача 2. Где стоят обработчики запросов")
    class Task2 {

        @Test
        @DisplayName("дамп лаборатории pool: 16 ждут соединение, 4 ждут платёжный шлюз")
        void poolDump() {
            List<Hotspot> hotspots = new T04ThreadDump().hotspots(Fixtures.text("pool-thread-print.txt"), "http-nio-");

            assertThat(hotspots).containsExactly(
                    new Hotspot(Thread.State.WAITING, "com.example.profcourse.t04.ConnectionPool.borrow", 16),
                    new Hotspot(Thread.State.TIMED_WAITING, "com.example.profcourse.t04.OrderController.chargeCard", 4));
        }

        @Test
        @DisplayName("дамп лаборатории deadlock: оба потока переводов BLOCKED в одном месте")
        void deadlockDump() {
            List<Hotspot> hotspots = new T04ThreadDump().hotspots(Fixtures.text("deadlock-thread-print.txt"), "transfer-");

            assertThat(hotspots).containsExactly(
                    new Hotspot(Thread.State.BLOCKED, "com.example.profcourse.t04.LegacyTransfers.transfer", 2));
        }

        @Test
        @DisplayName("потоки с другим префиксом не считаются")
        void otherPrefix() {
            assertThat(new T04ThreadDump().hotspots(Fixtures.text("pool-thread-print.txt"), "kafka-")).isEmpty();
        }
    }
}
