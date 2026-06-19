package com.example.reactivedemo.controller;

import com.example.reactivedemo.dto.OrderResponse;
import com.example.reactivedemo.dto.UserResponse;
import com.example.reactivedemo.dto.UserSummaryResponse;
import com.example.reactivedemo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>REST API пользователей в стиле <strong>Spring WebFlux</strong>.</p>
 *
 * <p>Контроллер <strong>не вызывает</strong> {@code subscribe()} — он возвращает {@link Mono} /
 * {@link Flux}, а Spring подписывается на цепочку и пишет HTTP-ответ через Netty.</p>
 *
 * <p>Базовый путь: {@code /api/users}</p>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * <p>Список всех пользователей.</p>
     *
     * <p>Пример запроса:</p>
     * <pre>
     * {@code GET /api/users}
     * </pre>
     *
     * @return {@link Flux} пользователей
     */
    @GetMapping
    public Flux<UserResponse> listUsers() {
        return userService.findAll();
    }

    /**
     * <p>Один пользователь по id.</p>
     *
     * <p>Пример:</p>
     * <pre>
     * {@code GET /api/users/1}
     * </pre>
     *
     * @param id идентификатор пользователя
     * @return {@link Mono} с пользователем или HTTP 404
     */
    @GetMapping("/{id}")
    public Mono<UserResponse> getUser(@PathVariable Long id) {
        return userService.findById(id);
    }

    /**
     * <p>Пользователь и его заказы (цепочка с {@code flatMap} в {@link UserService#getUserSummary}).</p>
     *
     * <p>Пример:</p>
     * <pre>
     * {@code GET /api/users/1/summary}
     * </pre>
     *
     * @param id идентификатор пользователя
     * @return {@link Mono} со сводкой
     */
    @GetMapping("/{id}/summary")
    public Mono<UserSummaryResponse> getUserSummary(@PathVariable Long id) {
        return userService.getUserSummary(id);
    }

    /**
     * <p>Заказы пользователя.</p>
     *
     * <p>Пример:</p>
     * <pre>
     * {@code GET /api/users/1/orders}
     * </pre>
     *
     * @param id идентификатор пользователя
     * @return {@link Flux} заказов
     */
    @GetMapping("/{id}/orders")
    public Flux<OrderResponse> getUserOrders(@PathVariable Long id) {
        return userService.getOrdersForUser(id);
    }

    /**
     * <p>Email пользователя в верхнем регистре — пример {@code map} в сервисе.</p>
     *
     * <p>Пример:</p>
     * <pre>
     * {@code GET /api/users/1/email-upper}
     * </pre>
     *
     * @param id идентификатор пользователя
     * @return {@link Mono} со строкой email
     */
    @GetMapping("/{id}/email-upper")
    public Mono<String> getUserEmailUpperCase(@PathVariable Long id) {
        return userService.getUserEmailUpperCase(id);
    }
}
