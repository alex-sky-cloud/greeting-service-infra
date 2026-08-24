package com.example.reactorworkshop.t01_map_flatmap.repository;

import com.example.reactorworkshop.t01_map_flatmap.domain.T01MeterEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * R2DBC {@code meters} для t01.
 * <pre>{@code
 * meterRepository.findById(meterId);
 * }</pre>
 */
public interface T01MeterRepository extends ReactiveCrudRepository<T01MeterEntity, Long> {
}
