package com.example.vtcourse.t05;

import com.example.vtcourse.common.Todo;

import java.util.List;

/**
 * Тема 05. Практика: задачи 1–3.
 * Условия: {@code virtual-threads-study/docs/virtual-threads-course/05-structured-concurrency-tasks.md}.
 */
public class T05StructuredTasks {

    private final CustomerClients clients;

    public T05StructuredTasks(CustomerClients clients) {
        this.clients = clients;
    }

    /** Задача 1. Карточка клиента: всё или ничего. */
    public String card(int customerId) throws Exception {
        throw Todo.task("05.1");
    }

    /** Задача 2. Курс валюты с самого быстрого зеркала. */
    public String fastestRate(String currency, List<RateMirror> mirrors) throws Exception {
        throw Todo.task("05.2");
    }

    /** Задача 3. Панель оператора за 300 мс. */
    public List<String> dashboard(List<Widget> widgets) throws Exception {
        throw Todo.task("05.3");
    }
}
