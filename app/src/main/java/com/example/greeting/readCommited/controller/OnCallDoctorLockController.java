package com.example.greeting.readCommited.controller;

import com.example.greeting.readCommited.service.OnCallDoctorLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер для демонстрации защиты от write skew
 * через SELECT ... FOR UPDATE.
 */
@RestController
@RequestMapping("/api/mitigation/on-call")
@RequiredArgsConstructor
public class OnCallDoctorLockController {

    private final OnCallDoctorLockService lockService;

    /**
     * Запускает безопасное снятие врача с дежурства.
     *
     * <p>Сценарий использования:</p>
     * <pre>
     * 1. Из приложения вызывается:
     *    POST /api/mitigation/on-call/safe-off?doctorId=2
     *
     * 2. Внутри сервиса выполняется SELECT ... FOR UPDATE
     *    по всем строкам on_call = true.
     *
     * 3. Пока поток стоит на breakpoint, в консоли psql можно выполнить
     *    конкурентную транзакцию, которая попытается работать
     *    с тем же набором строк.
     *
     * 4. После продолжения выполнения сервис либо снимет врача
     *    с дежурства, либо не даст это сделать, если останется один дежурный.
     * </pre>
     *
     * @param doctorId идентификатор врача
     */
    @PostMapping("/safe-off")
    public void safeOff(@RequestParam ("doctorId") Long doctorId) {
        lockService.safeOffCall(doctorId);
    }
}