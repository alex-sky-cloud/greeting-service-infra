package com.example.greeting.serializable.processErrorSerialization.failFast.controller;

import com.example.greeting.serializable.processErrorSerialization.exception.WriteSkewFailFastSerializationException;
import com.example.greeting.serializable.processErrorSerialization.failFast.service.WriteSkewFailFastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер для варианта fail fast
 * в сценарии write skew.
 */
@RestController
@RequestMapping("/api/serializable/write-skew/fail-fast")
@RequiredArgsConstructor
@Slf4j
public class WriteSkewFailFastController {

    private final WriteSkewFailFastService failFastService;

    /**
     * Запускает сценарий снятия врача с дежурства
     * без автоматического повтора транзакции.
     *
     * @param doctorId идентификатор врача
     */
    @PostMapping("/off-call")
    public ResponseEntity<String> offCallFailFast(@RequestParam("doctorId") Long doctorId) {
        try {
            failFastService.offCallFailFast(doctorId);
            log.info("offCallFailFast - транзакция окончена успешно.");
            return ResponseEntity.ok("Врач снят с дежурства (или уже был снят ранее).");
        } catch (CannotAcquireLockException ex) {
            // Здесь уже понятно: параллельная транзакция изменила состав дежурных
            log.warn(
                    "offCallFailFast - конфликт параллельных изменений при снятии врача с дежурства. doctorId={}",
                    doctorId, ex
            );

            throw new WriteSkewFailFastSerializationException(
                    "Невозможно снять врача с дежурства из-за параллельных изменений. " +
                            "Список дежурных врачей успел измениться другой транзакцией.",
                    ex
            );
        }
    }
}