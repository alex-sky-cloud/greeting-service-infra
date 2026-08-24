package com.example.reactorworkshop.t01_map_flatmap;

import com.example.reactorworkshop.t01_map_flatmap.domain.T01MeterEntity;
import com.example.reactorworkshop.t01_map_flatmap.domain.T01ReadingEntity;
import com.example.reactorworkshop.t01_map_flatmap.repository.T01MeterRepository;
import com.example.reactorworkshop.t01_map_flatmap.repository.T01ReadingRepository;
import com.example.reactorworkshop.t01_map_flatmap.service.T01MapFlatMapLabService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code map} и {@code Mono.flatMap} на счётчиках. */
class T01MapFlatMapLabServiceTest {

    @Test
    void mapTransformsSynchronously() {
        T01MeterRepository meters = Mockito.mock(T01MeterRepository.class);
        T01ReadingRepository readings = Mockito.mock(T01ReadingRepository.class);
        Mockito.when(meters.findAll()).thenReturn(Flux.just(
                new T01MeterEntity(1L, "M-00001", "Minsk", Instant.now())
        ));
        T01MapFlatMapLabService service = new T01MapFlatMapLabService(meters, readings);
        StepVerifier.create(service.mapMetersToDto())
                .expectNextMatches(dto -> dto.serialNo().equals("M-00001"))
                .verifyComplete();
    }

    @Test
    void monoFlatMapLoadsInnerPublisher() {
        T01MeterRepository meters = Mockito.mock(T01MeterRepository.class);
        T01ReadingRepository readings = Mockito.mock(T01ReadingRepository.class);
        Instant now = Instant.now();
        Mockito.when(meters.findById(1L)).thenReturn(Mono.just(
                new T01MeterEntity(1L, "M-00001", "Minsk", now)
        ));
        Mockito.when(readings.findByMeterId(1L)).thenReturn(Flux.just(
                new T01ReadingEntity(10L, 1L, new BigDecimal("1.250"), now)
        ));
        T01MapFlatMapLabService service = new T01MapFlatMapLabService(meters, readings);
        StepVerifier.create(service.loadMeterWithReadings(1L))
                .expectNextMatches(dto -> dto.kwhValues().size() == 1)
                .verifyComplete();
    }
}
