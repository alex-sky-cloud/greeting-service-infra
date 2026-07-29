package com.example.reactivestudy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Точка входа модуля <strong>reactive-study</strong> — лабораторные работы по Project Reactor.
 *
 * <p>Теория и задания: {@code docs/interview/reactive/}.</p>
 *
 * <p>Перед первым запуском: {@code src/main/resources/README.md}
 * (создать БД {@code reactive_study}, profile {@code local}).</p>
 */
@SpringBootApplication
@EnableR2dbcRepositories
public class ReactiveStudyApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactiveStudyApplication.class, args);
    }
}
