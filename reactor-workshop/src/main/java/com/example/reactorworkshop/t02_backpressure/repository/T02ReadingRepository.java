package com.example.reactorworkshop.t02_backpressure.repository;

import com.example.reactorworkshop.t02_backpressure.domain.T02ReadingEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * R2DBC-доступ к {@code reactor_workshop.readings} для лабы 2.1.
 * Два разных механизма, которые студент обязан различать по логу QUERY:
 * <ul>
 *   <li>{@code findAll} + {@code limitRate} — один {@code SELECT} <b>без</b> {@code LIMIT}.
 *       Backpressure в JVM: {@code request(n)} / prefetch. Драйвер Fetch-ит из открытого cursor.
 *       Все строки в итоге доходят (если HTTP не отменили).</li>
 *   <li>{@link #findPage} — native {@code LIMIT}/{@code OFFSET}. Backpressure на стороне БД:
 *       Postgres возвращает только эту страницу и STOP. JVM не видит остаток ~100 000.</li>
 * </ul>
 */
public interface T02ReadingRepository extends ReactiveCrudRepository<T02ReadingEntity, Long> {

    /**
     * Одна страница показаний классической offset-пагинацией (не Reactive Streams prefetch).
     *
     * <p>Что делает запрос по строкам:</p>
     * <pre>{@code
     * SELECT id, meter_id, kwh, recorded_at
     * FROM reactor_workshop.readings
     * ORDER BY id
     * LIMIT :limit OFFSET :offset
     * }</pre>
     * <ul>
     *   <li>{@code ORDER BY id} — страницы стабильны. Без {@code ORDER BY} {@code OFFSET} бессмысленен:
     *       Postgres может отдать любые N строк.</li>
     *   <li>{@code LIMIT :limit} — сервер вернёт не больше N строк и <b>остановится</b>.
     *       JVM никогда не увидит хвост таблицы. В демо {@code limit = 5}, чтобы лог был коротким.</li>
     *   <li>{@code OFFSET :offset} — сколько строк пропустить, прежде чем взять {@code LIMIT}.
     *       {@code OFFSET 0} + {@code LIMIT 5} = первая страница (ids 1..5, если id с 1).
     *       {@code OFFSET 5} + {@code LIMIT 5} = вторая (ids 6..10). Демо берёт только первую:
     *       {@code offset = 0}. Обход всех страниц — {@code collectList}/{@code flatMapMany}/{@code concatWith}, позже.</li>
     * </ul>
     * <p>Зачем оба: {@code LIMIT} один — это «первые N». {@code OFFSET} именует страницу
     * ({@code pageIndex * pageSize}). Вместе — классическая offset-пагинация.
     * Минус: большой {@code OFFSET} дорогой (Postgres всё равно проходит пропущенные строки).
     * Оставляем, потому что именно этот SQL виден в {@code io.r2dbc.postgresql.QUERY}
     * и это стандартный ответ на интервью «как постраничить в SQL».</p>
     *
     * <p>Результат вызова демо: ровно {@code limit} строк, затем {@code onComplete}.
     * Не 100k. Не prefetch. Это БД режет ResultSet.</p>
     *
     * @param limit  максимум строк от Postgres ({@code LIMIT})
     * @param offset сколько строк пропустить ({@code OFFSET})
     * @return {@code Flux} только этой страницы
     */
    @Query("""
            SELECT id, meter_id, kwh, recorded_at
            FROM reactor_workshop.readings
            ORDER BY id
            LIMIT :limit OFFSET :offset
            """)
    Flux<T02ReadingEntity> findPage(@Param("limit") int limit, @Param("offset") long offset);
}
