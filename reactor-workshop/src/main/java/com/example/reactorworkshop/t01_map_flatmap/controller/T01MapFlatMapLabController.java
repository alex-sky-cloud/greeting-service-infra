package com.example.reactorworkshop.t01_map_flatmap.controller;

import com.example.reactorworkshop.t01_map_flatmap.domain.T01MeterDto;
import com.example.reactorworkshop.t01_map_flatmap.domain.T01MeterReadingsDto;
import com.example.reactorworkshop.t01_map_flatmap.service.T01MapFlatMapLabService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * HTTP t01: счётчики и показания.
 */
@RestController
@RequestMapping("/api/t01")
public class T01MapFlatMapLabController {

    private final T01MapFlatMapLabService service;

    public T01MapFlatMapLabController(T01MapFlatMapLabService service) {
        this.service = service;
    }

    @GetMapping("/map")
    public Flux<T01MeterDto> mapMeters() {
        return service.mapMetersToDto();
    }

    @GetMapping("/mono-flatmap/{meterId}")
    public Mono<T01MeterReadingsDto> monoFlatMap(@PathVariable Long meterId) {
        return service.loadMeterWithReadings(meterId);
    }

    @GetMapping("/flux-flatmap")
    public Flux<T01MeterReadingsDto> fluxFlatMap() {
        return service.loadMetersWithReadingsInterleaved();
    }
}
