package com.example.vtcourse.t04;

import com.example.vtcourse.common.Todo;

import java.util.List;

/**
 * Тема 04. Практика: задачи 1–2.
 * Условия: {@code virtual-threads-study/docs/virtual-threads-course/04-scoped-values-tasks.md}.
 */
public class T04RequestTasks {

    private final OrderService orders;

    public T04RequestTasks(OrderService orders) {
        this.orders = orders;
    }

    /** Задача 1. Действия пользователя в журнале аудита. */
    public void handle(String user, List<String> actions) throws Exception {
        throw Todo.task("04.1");
    }

    /** Задача 2. Закрытие дня со служебным шагом. */
    public void closeDay(String user) throws Exception {
        throw Todo.task("04.2");
    }
}
