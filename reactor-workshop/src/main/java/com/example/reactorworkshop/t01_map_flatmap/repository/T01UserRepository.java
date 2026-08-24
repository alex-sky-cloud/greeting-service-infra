package com.example.reactorworkshop.t01_map_flatmap.repository;

import com.example.reactorworkshop.t01_map_flatmap.domain.T01UserEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * R2DBC-доступ к {@code users} в теме t01.
 * <p>
 * Имя интерфейса {@code T01UserRepository} даёт бин {@code t01UserRepository}, не {@code userRepository}.
 * <p>
 * Типичный вызов:
 * <pre>{@code
 * userRepository.findById(userId); // Mono<T01UserEntity>
 * userRepository.findAll();        // Flux<T01UserEntity>
 * }</pre>
 */
public interface T01UserRepository extends ReactiveCrudRepository<T01UserEntity, Long> {
}
