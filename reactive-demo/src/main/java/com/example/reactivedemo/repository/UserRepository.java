package com.example.reactivedemo.repository;

import com.example.reactivedemo.domain.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * <p>Реактивный репозиторий пользователей (R2DBC).</p>
 *
 * <p>Важно для Reactor:</p>
 * <ul>
 *   <li>{@link #findById(Long)} возвращает {@link Mono} — «один User или пусто»;</li>
 *   <li>запрос в БД <strong>не выполняется</strong>, пока на {@link Mono} не подпишутся
 *       (в WebFlux подписывается Spring, когда контроллер возвращает {@link Mono}).</li>
 * </ul>
 *
 * <p>Пример в сервисе — только с {@code flatMap}, если метод репозитория внутри цепочки по id:</p>
 * <pre>
 * {@code
 * Flux.fromIterable(ids)
 *     .flatMap(userRepository::findById)  // не map!
 *     .map(UserResponse::from);
 * }
 * </pre>
 */
public interface UserRepository extends ReactiveCrudRepository<User, Long> {

    /**
     * <p>Поиск пользователя по email.</p>
     *
     * @param email адрес электронной почты
     * @return {@link Mono} с пользователем или пустой {@link Mono}, если не найден
     */
    Mono<User> findByEmail(String email);
}
