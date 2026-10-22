package com.example.profcourse.t01;

import com.example.profcourse.common.Todo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Задача 01.2. Условие: {@code docs/profiling-course/01-before-profiler-tasks.md}. */
public class T01CheckoutTimer {

    private final CheckoutPipeline pipeline;

    public T01CheckoutTimer(CheckoutPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public List<StageTime> breakdown(String orderId) {
        throw Todo.task("01.2");
    }
}
