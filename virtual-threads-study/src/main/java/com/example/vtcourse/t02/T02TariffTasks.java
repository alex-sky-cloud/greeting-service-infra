package com.example.vtcourse.t02;

import com.example.vtcourse.common.Todo;

import java.util.List;
import java.util.Map;

/**
 * Тема 02. Практика: задача 1.
 * Условия: {@code virtual-threads-study/docs/virtual-threads-course/02-when-and-how-tasks.md}.
 */
public class T02TariffTasks {

    private final TariffService tariffs;

    public T02TariffTasks(TariffService tariffs) {
        this.tariffs = tariffs;
    }

    /** Задача 1. Тарифы партнёра, не больше трёх запросов одновременно. */
    public Map<String, Integer> quotes(List<String> cities) throws Exception {
        throw Todo.task("02.1");
    }
}
