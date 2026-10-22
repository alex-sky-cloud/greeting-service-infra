package com.example.vtcourse.t02;

import com.example.vtcourse.common.Todo;

/**
 * Тема 02. Практика: задача 2.
 * Условия: {@code virtual-threads-study/docs/virtual-threads-course/02-when-and-how-tasks.md}.
 */
public class T02CardTasks {

    private final CustomerSystems systems;

    public T02CardTasks(CustomerSystems systems) {
        this.systems = systems;
    }

    /** Задача 2. Шапка карточки клиента из трёх систем. */
    public String cardHeader(int customerId) throws Exception {
        throw Todo.task("02.2");
    }
}
