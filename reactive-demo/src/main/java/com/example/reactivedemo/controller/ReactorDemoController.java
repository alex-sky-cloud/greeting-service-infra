package com.example.reactivedemo.controller;

import com.example.reactivedemo.dto.MapVsFlatMapComparisonResponse;
import com.example.reactivedemo.dto.UserResponse;
import com.example.reactivedemo.service.ReactorDemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * <p>Учебные endpoint'ы Project Reactor ({@code map} / {@code flatMap}).</p>
 *
 * <p>Боевое API пользователей — {@link UserController}.</p>
 *
 * <p>Базовый путь: {@code /api/demo/reactor}</p>
 */
@RestController
@RequestMapping("/api/demo/reactor")
@RequiredArgsConstructor
public class ReactorDemoController {

    private final ReactorDemoService reactorDemoService;

    /**
     * <p>Сравнение {@code map} и {@code flatMap} на одних и тех же id.</p>
     *
     * <p>Ответ JSON содержит:</p>
     * <ul>
     *   <li>{@code mapWrong} — тип элементов потока при ошибочном {@code map};</li>
     *   <li>{@code flatMapCorrect} — реальные пользователи при {@code flatMap}.</li>
     * </ul>
     *
     * <p>Пример:</p>
     * <pre>
     * {@code GET /api/demo/reactor/compare?ids=1,2,3}
     * </pre>
     *
     * @param ids список идентификаторов пользователей
     * @return {@link Mono} с результатом сравнения
     */
    @GetMapping("/compare")
    public Mono<MapVsFlatMapComparisonResponse> compareMapVsFlatMap(@RequestParam List<Long> ids) {
        return reactorDemoService.compareMapVsFlatMap(ids);
    }

    /**
     * <p>Загрузка пользователей по списку id — правильный вариант с {@code flatMap}.</p>
     *
     * <p>Пример:</p>
     * <pre>
     * {@code GET /api/demo/reactor/users?ids=1,2}
     * </pre>
     *
     * @param ids идентификаторы пользователей
     * @return {@link Flux} DTO пользователей
     */
    @GetMapping("/users")
    public Flux<UserResponse> loadUsersCorrect(@RequestParam List<Long> ids) {
        return reactorDemoService.loadUsersWithFlatMapCorrect(ids);
    }

    /**
     * <p>Загрузка пользователей через {@code concatMap} — порядок id на выходе совпадает с входом.</p>
     *
     * <p>Пример:</p>
     * <pre>
     * {@code GET /api/demo/reactor/users-concat?ids=1,2,3}
     * </pre>
     *
     * @param ids идентификаторы пользователей
     * @return {@link Flux} DTO пользователей
     */
    @GetMapping("/users-concat")
    public Flux<UserResponse> loadUsersConcat(@RequestParam List<Long> ids) {
        return reactorDemoService.loadUsersWithConcatMap(ids);
    }
}
