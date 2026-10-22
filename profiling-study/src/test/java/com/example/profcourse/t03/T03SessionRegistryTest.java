package com.example.profcourse.t03;

import com.example.profcourse.common.HeapHistogram;
import com.example.profcourse.common.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверка задачи 03.1. Условие: {@code docs/profiling-course/03-memory-tasks.md}. */
@DisplayName("Задача 1. Реестр сессий не держит просроченные сессии")
class T03SessionRegistryTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-20T09:00:00Z"));
    private final T03SessionRegistry registry = new T03SessionRegistry(clock);

    @Test
    @DisplayName("вход, активность, выход и истечение через 30 минут без активности")
    void rules() {
        String anna = registry.login("anna");
        String boris = registry.login("boris");

        clock.advance(Duration.ofMinutes(20));
        assertThat(registry.touch(anna)).as("anna активна через 20 минут").isTrue();

        clock.advance(Duration.ofMinutes(15));
        assertThat(registry.touch(boris)).as("boris молчал 35 минут").isFalse();
        assertThat(registry.touch(anna)).as("anna молчала 15 минут").isTrue();
        assertThat(registry.activeCount()).isEqualTo(1);

        registry.logout(anna);
        assertThat(registry.touch(anna)).isFalse();
        assertThat(registry.touch("нет-такой")).isFalse();
        assertThat(registry.activeCount()).isZero();
    }

    @Test
    @DisplayName("20 000 входов за 2 часа: в куче живут только сессии последних 30 минут")
    void expiredSessionsAreCollected() {
        for (int i = 0; i < 20_000; i++) {
            registry.login("user-" + i);
            clock.advance(Duration.ofMillis(360));
        }

        assertThat(registry.activeCount()).as("действующих сессий: вход каждые 0,36 с за 30 минут").isBetween(4_999, 5_001);
        long live = HeapHistogram.liveInstances(UserSession.class);
        assertThat(live).as("живых объектов UserSession в куче после полной сборки").isLessThanOrEqualTo(5_100);
    }
}
