package com.example.reactorworkshop.t01_map_flatmap.repository;

import com.example.reactorworkshop.t01_map_flatmap.domain.T01ReadingEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * R2DBC {@code readings} для t01.
 */
public interface T01ReadingRepository extends ReactiveCrudRepository<T01ReadingEntity, Long> {

    /**
     * Показания одного счётчика — inner {@code Flux} для {@code flatMap}.
     */
    Flux<T01ReadingEntity> findByMeterId(Long meterId);
}
