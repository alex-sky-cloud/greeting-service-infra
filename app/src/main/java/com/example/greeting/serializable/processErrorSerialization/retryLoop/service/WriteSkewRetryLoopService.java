package com.example.greeting.serializable.processErrorSerialization.retryLoop.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;

/**
 * Сервис с ручным retry-циклом
 * для полного повтора транзакции.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WriteSkewRetryLoopService {

    private static final int MAX_ATTEMPTS = 3;

    private final WriteSkewRetryLoopTxService txService;

    /**
     * Повторяет полную транзакцию при serialization failure.
     *
     * @param doctorId идентификатор врача
     */
    public void offCallWithRetryLoop(Long doctorId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                txService.offCallOnce(doctorId);
                log.info("Транзакция завершена успешно: doctorId={}, attempt={}", doctorId, attempt);
                return;
            } catch (ConcurrencyFailureException | JpaSystemException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    throw ex;
                }

                log.warn("Serialization failure, повтор транзакции: doctorId={}, attempt={}",
                        doctorId, attempt, ex);

                sleepBeforeRetry(attempt);
            }
        }
    }

    /**
     * Делает короткую паузу перед повторной попыткой.
     *
     * @param attempt номер попытки
     */
    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(100L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Поток был прерван во время retry backoff", ex);
        }
    }
}
