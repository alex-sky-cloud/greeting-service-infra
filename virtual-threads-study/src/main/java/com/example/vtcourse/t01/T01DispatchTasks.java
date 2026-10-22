package com.example.vtcourse.t01;

import com.example.vtcourse.common.Todo;

import java.util.List;

/**
 * Тема 01. Практика: задачи 1–2.
 * Условия: {@code virtual-threads-study/docs/virtual-threads-course/01-model-tasks.md}.
 */
public class T01DispatchTasks {

    private final SmsGateway gateway;

    public T01DispatchTasks(SmsGateway gateway) {
        this.gateway = gateway;
    }

    /** Задача 1. Рассылка SMS. */
    public int sendAll(List<String> phones) throws Exception {
        throw Todo.task("01.1");
    }

    /** Задача 2. Курьеры с именами. */
    public List<Thread> startCouriers(int count, Runnable route) {
        throw Todo.task("01.2");
    }
}
