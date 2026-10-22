package com.example.profcourse.t05;

import com.example.profcourse.common.Todo;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.util.Optional;

/** Задача 05.2. Условие: {@code docs/profiling-course/05-jfr-jmc-tasks.md}. */
public class T05IncidentRecorder implements AutoCloseable {

    public static final Duration SLOW = Duration.ofMillis(500);

    private final Path directory;
    private final Duration minGap;

    public T05IncidentRecorder(Path directory, Duration minGap) {
        this.directory = directory;
        this.minGap = minGap;
    }

    public synchronized void start() {
        throw Todo.task("05.2");
    }

    public synchronized Optional<Path> onRequest(String requestId, Duration latency) {
        throw Todo.task("05.2");
    }

    @Override
    public synchronized void close() {
        throw Todo.task("05.2");
    }
}
