package com.example.reactivedemo.repository;

import com.example.reactivedemo.domain.Order;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * <p>Реактивный репозиторий заказов (R2DBC).</p>
 *
 * <p>{@link #findByUserId(Long)} возвращает {@link Flux} — поток из нуля или нескольких заказов.
 * В сервисе его обычно собирают через {@code collectList()} или отдают из контроллера как
 * {@link Flux} для стримингового ответа.</p>
 */
public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {

    /**
     * <p>Все заказы указанного пользователя.</p>
     *
     * @param userId идентификатор пользователя
     * @return {@link Flux} заказов; пустой поток, если заказов нет
     */
    Flux<Order> findByUserId(Long userId);
}
