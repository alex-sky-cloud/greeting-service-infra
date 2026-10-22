package com.example.vtcourse.t01;

import com.example.vtcourse.common.Timing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверка задач темы 01. Условия: {@code docs/virtual-threads-course/01-model-tasks.md}. */
class T01DispatchTasksTest {

    @Nested
    @DisplayName("Задача 1. Рассылка SMS")
    class Task1 {

        @Test
        @DisplayName("1000 номеров: 990 принято, все отправки одновременно, каждая на своём виртуальном потоке")
        void sendsAllConcurrently() throws Exception {
            SmsGateway gateway = new SmsGateway();
            List<String> phones = new ArrayList<>();
            IntStream.range(0, 990).forEach(i -> phones.add("+7900" + String.format("%07d", i)));
            IntStream.range(0, 10).forEach(i -> phones.add("8900" + String.format("%07d", i)));

            var result = Timing.measure(() -> new T01DispatchTasks(gateway).sendAll(phones));

            assertThat(result.value()).isEqualTo(990);
            assertThat(gateway.stats().calls()).as("вызовов шлюза к моменту возврата").isEqualTo(1000);
            assertThat(gateway.stats().inFlight()).as("отправок ещё в работе после возврата").isZero();
            assertThat(result.took()).isLessThan(Duration.ofMillis(1500));
            assertThat(gateway.stats().threads()).allMatch(Thread::isVirtual);
            assertThat(Set.copyOf(gateway.stats().threads())).as("разных потоков").hasSize(1000);
        }
    }

    @Nested
    @DisplayName("Задача 2. Курьеры с именами")
    class Task2 {

        @Test
        @DisplayName("пять курьеров: courier-1 … courier-5, виртуальные, запущены, маршрут пройден пять раз")
        void namedVirtualCouriers() throws Exception {
            Set<String> seen = ConcurrentHashMap.newKeySet();
            Runnable route = () -> seen.add(Thread.currentThread().getName());

            List<Thread> couriers = new T01DispatchTasks(new SmsGateway()).startCouriers(5, route);
            for (Thread courier : couriers) {
                assertThat(courier.getState()).as("курьер запущен").isNotEqualTo(Thread.State.NEW);
                courier.join(Duration.ofSeconds(1));
            }

            assertThat(couriers).extracting(Thread::getName)
                    .containsExactly("courier-1", "courier-2", "courier-3", "courier-4", "courier-5");
            assertThat(couriers).allMatch(Thread::isVirtual).allMatch(Thread::isDaemon);
            assertThat(seen).containsExactlyInAnyOrder("courier-1", "courier-2", "courier-3", "courier-4", "courier-5");
        }
    }
}
