package com.example.vtcourse.t05;

/** Готовый класс, менять не нужно. Зеркало курсов не ответило. */
public class MirrorUnavailableException extends RuntimeException {

    public MirrorUnavailableException(String mirror) {
        super("Зеркало " + mirror + " недоступно");
    }
}
