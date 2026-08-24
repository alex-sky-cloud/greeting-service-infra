package com.example.reactorworkshop.t02_backpressure.repository;

import com.example.reactorworkshop.t02_backpressure.domain.T02ReadingEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * Поток показаний для лабы 2.1.
 * <ul>
 *   <li>{@code findAll} + {@code limitRate} — один SELECT, demand в JVM</li>
 *   <li>{@code findPage} — {@code LIMIT}/{@code OFFSET} видно в QUERY-логе</li>
 * </ul>
 */
public interface T02ReadingRepository extends ReactiveCrudRepository<T02ReadingEntity, Long> {

    @Query("""
            SELECT id, meter_id, kwh, recorded_at
            FROM reactor_workshop.readings
            ORDER BY id
            LIMIT :limit OFFSET :offset
            """)
    Flux<T02ReadingEntity> findPage(@Param("limit") int limit, @Param("offset") long offset);
}
