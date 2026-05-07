package com.example.greeting.serializable.processErrorSerialization.retryLoop.controller;

import com.example.greeting.serializable.processErrorSerialization.retryLoop.service.WriteSkewRetryLoopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер для варианта retry loop
 * в сценарии write skew.
 */
@RestController
@RequestMapping("/api/serializable/write-skew/retry-loop")
@RequiredArgsConstructor
@Slf4j
public class WriteSkewRetryLoopController {

    private final WriteSkewRetryLoopService retryLoopService;

    /**
     * Запускает сценарий снятия врача с дежурства
     * с повтором полной транзакции.
     *
     * @param doctorId идентификатор врача
     */
    @PostMapping("/off-call")
    public void offCallWithRetryLoop(@RequestParam ("doctorId") Long doctorId) {
        retryLoopService.offCallWithRetryLoop(doctorId);
    }
}
