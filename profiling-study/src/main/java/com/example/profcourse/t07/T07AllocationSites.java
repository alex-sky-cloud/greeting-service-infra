package com.example.profcourse.t07;

import com.example.profcourse.common.Todo;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Задача 07.2. Условие: {@code docs/profiling-course/07-allocations-tasks.md}. */
public class T07AllocationSites {

    public List<AllocationSite> top(Path recording, int n) throws IOException {
        throw Todo.task("07.2");
    }
}
