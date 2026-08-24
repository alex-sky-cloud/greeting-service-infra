package com.example.reactorworkshop.t01_map_flatmap.repository;

import com.example.reactorworkshop.t01_map_flatmap.domain.T01OrderEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * R2DBC-доступ к {@code orders} в теме t01.
 * <ul>
 *   <li>{@code findByUserId} — inner Publisher для {@code flatMap}</li>
 *   <li>не путать с {@code T02OrderRepository}</li>
 * </ul>
 * <pre>{@code
 * orderRepository.findByUserId(user.id()); // Flux<T01OrderEntity>
 * }</pre>
 */
public interface T01OrderRepository extends ReactiveCrudRepository<T01OrderEntity, Long> {

    /**
     * Заказы одного пользователя. Это {@code Flux}, поэтому склеивать с user нужно {@code flatMap}, не {@code map}.
     *
     * @param userId {@code users.id}
     * @return поток заказов, может быть пустым
     */
    Flux<T01OrderEntity> findByUserId(Long userId);
}
