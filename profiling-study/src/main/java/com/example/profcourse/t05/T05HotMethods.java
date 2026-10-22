package com.example.profcourse.t05;

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

/** Задача 05.1. Условие: {@code docs/profiling-course/05-jfr-jmc-tasks.md}. */
public class T05HotMethods {

    public List<MethodSamples> top(Path recording, int n) throws IOException {
        throw Todo.task("05.1");
    }
}
