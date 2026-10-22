package com.example.profcourse.common;

/**
 * Заглушка нерешённой задачи курса.
 * <p>
 * Метод задачи бросает это исключение, пока в нём нет решения, поэтому тест задачи падает
 * с понятным сообщением, а не с {@code NullPointerException}.
 */
public final class Todo {

    private Todo() {
    }

    /** @param id номер вида {@code "03.2"}: тема 03, задача 2. */
    public static UnsupportedOperationException task(String id) {
        String topic = id.substring(0, 2);
        return new UnsupportedOperationException(
                "Задача " + id + " не решена. Условие: profiling-study/docs/profiling-course/" + topic + "-*-tasks.md");
    }
}
