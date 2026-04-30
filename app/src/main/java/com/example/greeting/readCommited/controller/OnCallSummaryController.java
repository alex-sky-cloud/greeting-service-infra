package com.example.greeting.readCommited.controller;

import com.example.greeting.readCommited.service.OnCallSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер для демонстрации защиты от write skew
 * через отдельную агрегирующую строку.
 */
@RestController
@RequestMapping("/api/mitigation/on-call-summary")
@RequiredArgsConstructor
public class OnCallSummaryController {

    private final OnCallSummaryService summaryService;

    /**
     * Запускает безопасное снятие врача с дежурства
     * через агрегирующую строку on_call_summary.
     *
     * <p>Сценарий использования:</p>
     * <pre>
     * 1. Из приложения вызывается:
     *    POST /api/mitigation/on-call-summary/safe-off?doctorId=2
     *
     * 2. Внутри сервиса выполняется SELECT ... FOR UPDATE
     *    по строке iso_demo.on_call_summary(id = 1).
     *
     * 3. Пока поток стоит на breakpoint, в консоли psql можно выполнить
     *    конкурентную транзакцию, которая попытается изменить того же врача
     *    и тот же агрегирующий счётчик.
     *
     * 4. После продолжения выполнения сервис либо снимет врача
     *    с дежурства и уменьшит счётчик, либо не даст это сделать,
     *    если счётчик уже стал равен 1.
     * </pre>
     *
     * @param doctorId идентификатор врача
     */
    @PostMapping("/safe-off")
    public void safeOff(@RequestParam Long doctorId) {
        summaryService.safeOffWithSummary(doctorId);
    }
}