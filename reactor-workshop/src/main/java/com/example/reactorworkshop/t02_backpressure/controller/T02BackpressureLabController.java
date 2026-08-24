package com.example.reactorworkshop.t02_backpressure.controller;

import com.example.reactorworkshop.t02_backpressure.domain.T02PacedId;
import com.example.reactorworkshop.t02_backpressure.domain.T02ReadingEntity;
import com.example.reactorworkshop.t02_backpressure.service.T02BackpressureLabService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * HTTP лабы 2.1 (backpressure / {@code limitRate}).
 * <ul>
 *   <li>{@code /readings-limit-rate} — один SELECT без LIMIT</li>
 *   <li>{@code /readings-sql-page} — один SELECT с LIMIT, видно в QUERY</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/t02")
public class T02BackpressureLabController {

    private static final int DEFAULT_ID_COUNT = 20;
    private static final int DEFAULT_IDS_PER_REQUEST = 5;

    private final T02BackpressureLabService service;

    public T02BackpressureLabController(T02BackpressureLabService service) {
        this.service = service;
    }

    /**
     * JVM {@code limitRate} на ~100k строк.
     */
    @GetMapping("/readings-limit-rate")
    public Flux<T02ReadingEntity> readingsLimitRate() {
        return service.exportReadingsLimited();
    }

    /**
     * Один SQL {@code LIMIT 5 OFFSET 0} в логе {@code io.r2dbc.postgresql.QUERY}.
     */
    @GetMapping("/readings-sql-page")
    public Flux<T02ReadingEntity> readingsSqlPage() {
        return service.exportReadingsSqlPage();
    }

    /**
     * Синтетический поток (БД не трогает).
     *
     * @param count сколько id
     * @param rate  пачка {@code limitRate}
     */
    @GetMapping("/paced-ids")
    public Flux<T02PacedId> pacedIds(
            @RequestParam(defaultValue = "" + DEFAULT_ID_COUNT) int count,
            @RequestParam(defaultValue = "" + DEFAULT_IDS_PER_REQUEST) int rate
    ) {
        return service.pacedIds(count, rate);
    }
}
