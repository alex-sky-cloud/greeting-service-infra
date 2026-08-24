package com.example.reactorworkshop.t02_backpressure.service;

import com.example.reactorworkshop.t02_backpressure.domain.T02PacedId;
import com.example.reactorworkshop.t02_backpressure.domain.T02ReadingEntity;
import com.example.reactorworkshop.t02_backpressure.repository.T02ReadingRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Лаба 2.1: backpressure на таблице {@code readings} (~100 000 строк).
 * <ul>
 *   <li>{@code limitRate} режет {@code request(n)} в JVM, SQL {@code LIMIT} сам не появляется</li>
 *   <li>{@code findPage} — один запрос с {@code LIMIT}/{@code OFFSET}, его видно в логе QUERY</li>
 * </ul>
 */
@Service
public class T02BackpressureLabService {

    /** Сколько строк WebFlux запрашивает у {@code findAll} за один {@code request}. */
    static final int READINGS_PER_REQUEST = 50;

    /** Сколько строк просим у Postgres в демо SQL-{@code LIMIT}. */
    static final int SQL_LIMIT_ROWS = 5;

    /** С какой строки начинаем SQL-выборку (первая страница). */
    static final long SQL_FIRST_OFFSET = 0L;

    /** Нижняя граница: поток не должен эмитить отрицательное число элементов. */
    private static final int EMPTY_COUNT = 0;

    /** Минимальный размер пачки {@code limitRate}, если клиент прислал 0. */
    private static final int MIN_IDS_PER_REQUEST = 1;

    /** Первое число в синтетическом {@code Flux.range}. */
    private static final int FIRST_SYNTHETIC_ID = 1;

    private final T02ReadingRepository readingRepository;

    public T02BackpressureLabService(T02ReadingRepository readingRepository) {
        this.readingRepository = readingRepository;
    }

    /**
     * Все показания с backpressure в JVM.
     * В логе QUERY будет <b>один</b> {@code SELECT} без {@code LIMIT}.
     * <pre>{@code
     * readingRepository.findAll().limitRate(readingsPerRequest);
     * }</pre>
     *
     * @return поток всех строк {@code readings}
     */
    public Flux<T02ReadingEntity> exportReadingsLimited() {
        int readingsPerRequest = READINGS_PER_REQUEST;

        return readingRepository.findAll()
                .limitRate(readingsPerRequest); // режет demand к ResultSet, не добавляет SQL LIMIT
    }

    /**
     * Один SQL с {@code LIMIT}, чтобы в логе QUERY было видно пачку на стороне БД.
     * Без цепочки {@code collectList}/{@code flatMapMany} — это операторы следующих тем.
     * <pre>{@code
     * readingRepository.findPage(sqlLimitRows, firstOffset);
     * }</pre>
     *
     * @return {@link #SQL_LIMIT_ROWS} первых показаний по {@code id}
     */
    public Flux<T02ReadingEntity> exportReadingsSqlPage() {
        int sqlLimitRows = SQL_LIMIT_ROWS;
        long firstOffset = SQL_FIRST_OFFSET;

        return readingRepository.findPage(sqlLimitRows, firstOffset); // один SELECT ... LIMIT ? OFFSET 0
    }

    /**
     * Синтетика без БД: все id доходят, {@code limitRate} только задаёт размер {@code request}.
     *
     * @param count сколько чисел эмитить
     * @param rate  размер пачки {@code limitRate}
     */
    public Flux<T02PacedId> pacedIds(int count, int rate) {
        int emptyCount = EMPTY_COUNT;
        int minIdsPerRequest = MIN_IDS_PER_REQUEST;
        int firstSyntheticId = FIRST_SYNTHETIC_ID;
        int totalIds = Math.max(count, emptyCount);
        int idsPerRequest = Math.max(rate, minIdsPerRequest);

        return Flux.range(firstSyntheticId, totalIds)
                .limitRate(idsPerRequest) // пачки спроса; элементы не drop
                .map(id -> new T02PacedId(id, "limitRate, не drop")); // T -> DTO, без Publisher
    }
}
