package com.example.reactorworkshop.t01_map_flatmap.service;

import com.example.reactorworkshop.t01_map_flatmap.domain.T01MeterDto;
import com.example.reactorworkshop.t01_map_flatmap.domain.T01MeterEntity;
import com.example.reactorworkshop.t01_map_flatmap.domain.T01MeterReadingsDto;
import com.example.reactorworkshop.t01_map_flatmap.domain.T01ReadingEntity;
import com.example.reactorworkshop.t01_map_flatmap.repository.T01MeterRepository;
import com.example.reactorworkshop.t01_map_flatmap.repository.T01ReadingRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Глава 10: {@code map} / {@code Mono.flatMap} / {@code Flux.flatMap} на счётчиках.
 * <ul>
 *   <li>{@code map} — {@code T -> R} синхронно</li>
 *   <li>{@code Mono.flatMap} — {@code T -> Mono<R>}</li>
 *   <li>{@code Flux.flatMap} — inner Publisher сливаются (merge)</li>
 * </ul>
 */
@Service
public class T01MapFlatMapLabService {

    /** Сколько счётчиков берём в {@code flux-flatmap}, чтобы HTTP не выгрузил все 100k показаний. */
    static final int METERS_IN_FLATMAP_DEMO = 20;

    private final T01MeterRepository meterRepository;
    private final T01ReadingRepository readingRepository;

    public T01MapFlatMapLabService(T01MeterRepository meterRepository, T01ReadingRepository readingRepository) {
        this.meterRepository = meterRepository;
        this.readingRepository = readingRepository;
    }

    /**
     * Синхронное {@code map}: сущность счётчика в DTO, без подписки на другой Publisher.
     */
    public Flux<T01MeterDto> mapMetersToDto() {
        return meterRepository.findAll()
                .map(meter -> new T01MeterDto(meter.id(), meter.serialNo(), meter.city())); // T -> R, не Publisher
    }

    /**
     * {@code Mono.flatMap}: после счётчика нужен другой Publisher (показания).
     *
     * @param meterId {@code meters.id}
     */
    public Mono<T01MeterReadingsDto> loadMeterWithReadings(Long meterId) {
        return meterRepository.findById(meterId)
                .flatMap(this::toMeterReadings); // T -> Mono<R>: подписка на inner
    }

    /**
     * {@code Flux.flatMap}: на каждого счётчика — свои показания, порядок счётчиков не гарантирован.
     */
    public Flux<T01MeterReadingsDto> loadMetersWithReadingsInterleaved() {
        int metersInDemo = METERS_IN_FLATMAP_DEMO;

        return meterRepository.findAll()
                .take(metersInDemo) // обрезаем источник, не всю таблицу meters
                .flatMap(this::toMeterReadings); // inner Flux сливаются, не concat
    }

    /**
     * Собирает kWh одного счётчика в список для DTO.
     *
     * @param meter уже загруженный счётчик
     */
    private Mono<T01MeterReadingsDto> toMeterReadings(T01MeterEntity meter) {
        return readingRepository.findByMeterId(meter.id())
                .map(T01ReadingEntity::kwh) // из строки readings берём только kwh
                .collectList() // много kwh -> один List, чтобы отдать DTO целиком
                .map(kwhValues -> new T01MeterReadingsDto(meter.id(), meter.serialNo(), kwhValues)); // List -> DTO
    }
}
