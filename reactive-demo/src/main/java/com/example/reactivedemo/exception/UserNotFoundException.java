package com.example.reactivedemo.exception;

/**
 * <p>Пользователь не найден в БД.</p>
 *
 * <p>Обрабатывается в {@link GlobalExceptionHandler} → HTTP 404 и тело {@code ProblemDetail}.</p>
 */
public class UserNotFoundException extends RuntimeException {

    /**
     * @param id идентификатор, по которому запись не найдена
     */
    public UserNotFoundException(Long id) {
        super("User not found: id=" + id);
    }
}
