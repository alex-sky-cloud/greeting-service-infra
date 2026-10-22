package com.example.profcourse.t03;

import com.example.profcourse.common.Todo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Задача 03.1. Условие: {@code docs/profiling-course/03-memory-tasks.md}. */
public class T03SessionRegistry {

    public static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final Clock clock;

    public T03SessionRegistry(Clock clock) {
        this.clock = clock;
    }

    /** Новая сессия пользователя; возвращает её номер. */
    public synchronized String login(String userId) {
        throw Todo.task("03.1");
    }

    /** Отметить активность. {@code false}, если сессии нет или она просрочена. */
    public synchronized boolean touch(String sessionId) {
        throw Todo.task("03.1");
    }

    public synchronized void logout(String sessionId) {
        throw Todo.task("03.1");
    }

    /** Сколько действующих сессий. */
    public synchronized int activeCount() {
        throw Todo.task("03.1");
    }
}
