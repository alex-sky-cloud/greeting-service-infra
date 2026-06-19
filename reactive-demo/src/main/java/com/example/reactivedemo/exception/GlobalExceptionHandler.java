package com.example.reactivedemo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

/**
 * <p>Глобальная обработка ошибок для WebFlux.</p>
 *
 * <p>Обработчик возвращает {@link Mono}{@code <ProblemDetail>} — так же реактивно,
 * как и контроллеры: Spring подписывается и сериализует ответ в JSON.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * <p>HTTP 404, если пользователь не найден.</p>
     *
     * @param ex исключение с id пользователя
     * @return {@link Mono} с {@link ProblemDetail} для клиента
     */
    @ExceptionHandler(UserNotFoundException.class)
    public Mono<ProblemDetail> handleUserNotFound(UserNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("User not found");
        return Mono.just(problem);
    }
}
