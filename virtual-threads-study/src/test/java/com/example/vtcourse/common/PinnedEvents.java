package com.example.vtcourse.common;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Записывает JFR-события {@code jdk.VirtualThreadPinned}, пока выполняется действие:
 * каждое — случай, когда виртуальный поток заблокировался, не освободив поток-носитель.
 */
public final class PinnedEvents {

    public interface Action {
        void run() throws Exception;
    }

    private PinnedEvents() {
    }

    public static List<RecordedEvent> during(Action action) throws Exception {
        Path file = Files.createTempFile("pinned", ".jfr");
        try {
            try (Recording recording = new Recording()) {
                recording.enable("jdk.VirtualThreadPinned").withThreshold(Duration.ZERO).withStackTrace();
                recording.start();
                action.run();
                recording.stop();
                recording.dump(file);
            }
            return RecordingFile.readAllEvents(file);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
