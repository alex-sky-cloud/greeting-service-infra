package com.example.greeting.serializable.processErrorSerialization.springRetry.controller;

import com.example.greeting.serializable.processErrorSerialization.springRetry.service.WriteSkewSpringRetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер для варианта Spring Retry
 * в сценарии write skew.
 */
@RestController
@RequestMapping("/api/serializable/write-skew/spring-retry")
@RequiredArgsConstructor
public class WriteSkewSpringRetryController {

    private final WriteSkewSpringRetryService springRetryService;

    /**
     * Запускает сценарий снятия врача с дежурства
     * с использованием Spring Retry.
     *
     * @param doctorId идентификатор врача
     */
    @PostMapping("/off-call")
    public void offCallWithSpringRetry(@RequestParam Long doctorId) {
        springRetryService.offCallWithSpringRetry(doctorId);
    }
}
