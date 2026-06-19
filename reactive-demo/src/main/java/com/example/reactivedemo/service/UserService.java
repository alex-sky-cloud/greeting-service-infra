package com.example.reactivedemo.service;

import com.example.reactivedemo.domain.User;
import com.example.reactivedemo.dto.OrderResponse;
import com.example.reactivedemo.dto.UserResponse;
import com.example.reactivedemo.dto.UserSummaryResponse;
import com.example.reactivedemo.exception.UserNotFoundException;
import com.example.reactivedemo.repository.OrderRepository;
import com.example.reactivedemo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>Бизнес-логика пользователей в <strong>реактивном</strong> стиле Spring WebFlux.</p>
 *
 * <p>Общие правила этого сервиса:</p>
 * <ul>
 *   <li>методы возвращают {@link Mono} или {@link Flux} — без {@code subscribe()} и без {@code block()};</li>
 *   <li>{@code map} — когда значение уже загружено и его нужно преобразовать;</li>
 *   <li>{@code flatMap} / {@code flatMapMany} — когда следующий шаг — вызов репозитория
 *       (метод возвращает {@link Mono} или {@link Flux}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    /**
     * <p>Список всех пользователей.</p>
     *
     * @return {@link Flux} DTO пользователей
     */
    public Flux<UserResponse> findAll() {
        return userRepository.findAll().map(UserResponse::from);
    }

    /**
     * <p>Один пользователь по id.</p>
     *
     * <p>Если записи нет — {@link UserNotFoundException} (HTTP 404 через
     * {@link com.example.reactivedemo.exception.GlobalExceptionHandler}).</p>
     *
     * @param id идентификатор пользователя
     * @return {@link Mono} с {@link UserResponse}
     */
    public Mono<UserResponse> findById(Long id) {

        return userRepository.findById(id)
                .map(UserResponse::from)
                .switchIfEmpty(Mono.error(new UserNotFoundException(id)));
    }

    /**
     * <p>Сводка: пользователь и его заказы.</p>
     *
     * <p>Цепочка (типичный WebFlux + R2DBC):</p>
     * <ul>
     *   <li>{@code findById} → {@link Mono}{@code <User>}</li>
     *   <li>{@code flatMap} → для каждого User запрос заказов</li>
     *   <li>{@code collectList} → {@link Mono}{@code <List<Order>>}</li>
     *   <li>{@code map} → сборка {@link UserSummaryResponse}</li>
     * </ul>
     *
     * <p><strong>Нельзя</strong> заменить внешний {@code flatMap} на {@code map}, если лямбда возвращает
     * {@link reactor.core.publisher.Flux}:</p>
     * <pre>
     * {@code
     * // ❌ Mono<Flux<Order>> — map не подписывается на Flux
     * .map(user -> orderRepository.findByUserId(user.id()))
     * }
     * </pre>
     *
     * <p>Пример (правильно):</p>
     * <pre>
     * {@code
     * return userRepository.findById(id)
     *     .flatMap(user -> orderRepository.findByUserId(user.id())
     *         .collectList()
     *         .map(orders -> UserSummaryResponse.of(user, orders)));
     * }
     * </pre>
     *
     * @param id идентификатор пользователя
     * @return {@link Mono} со сводкой; ошибка, если пользователь не найден
     */
    public Mono<UserSummaryResponse> getUserSummary(Long id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException(id)))
                .flatMap(user -> orderRepository.findByUserId(user.id())
                        .collectList()
                        .map(orders -> UserSummaryResponse.of(user, orders)));
    }

    /**
     * <p>Заказы пользователя.</p>
     *
     * <p>Сначала проверяем, что пользователь существует ({@link Mono}), затем через
     * {@code flatMapMany} разворачиваем {@link Flux} заказов.</p>
     *
     * @param userId идентификатор пользователя
     * @return {@link Flux} заказов в виде {@link OrderResponse}
     */
    public Flux<OrderResponse> getOrdersForUser(Long userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException(userId)))
                .flatMapMany(user -> orderRepository.findByUserId(user.id()))
                .map(OrderResponse::from);
    }

    /**
     * <p>Пример оператора {@code map}: email уже есть в объекте {@link User},
     * запрос в БД один раз, дальше — синхронное преобразование строки.</p>
     *
     * <p>Пример:</p>
     * <pre>
     * {@code
     * return userRepository.findById(id)
     *     .map(User::email)
     *     .map(String::toUpperCase);
     * }
     * </pre>
     *
     * @param id идентификатор пользователя
     * @return {@link Mono} с email в верхнем регистре
     */
    public Mono<String> getUserEmailUpperCase(Long id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException(id)))
                .map(User::email)
                .map(String::toUpperCase);
    }
}
