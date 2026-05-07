package com.example.greeting.serializable.processErrorSerialization.springRetry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Сервис для варианта Spring Resilience Retry в сценарии write skew.
 *
 * <p>Выполняет повтор полной транзакции при ошибке сериализации PostgreSQL
 * ({@code SQLSTATE 40001}). Использует встроенный механизм повтора
 * Spring Framework 7 ({@code @Retryable}) из пакета
 * {@code org.springframework.resilience.annotation}.
 *
 * <p>Архитектурное правило: {@code @Retryable} и {@code @Transactional}
 * разнесены по <strong>разным бинам</strong>. Это гарантирует, что каждая
 * повторная попытка создаёт <strong>новую транзакцию</strong>, а не
 * продолжает старую завершившуюся с ошибкой.
 *
 * <p>Схема вызовов:
 * <pre>
 *   Controller
 *       └─▶ WriteSkewSpringRetryService.offCallWithSpringRetry()   // @Retryable
 *                   └─▶ WriteSkewSpringRetryTxService.offCallOnce() // @Transactional SERIALIZABLE
 * </pre>
 *
 * @see WriteSkewSpringRetryTxService
 * @see org.springframework.resilience.annotation.Retryable
 * @see org.springframework.dao.ConcurrencyFailureException
 * @see org.springframework.orm.jpa.JpaSystemException
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WriteSkewSpringRetryService {

    private final WriteSkewSpringRetryTxService txService;

    /**
     * Снимает врача с дежурства с автоматическим повтором транзакции
     * при ошибке сериализации PostgreSQL.
     *
     * <p>При возникновении {@link ConcurrencyFailureException} или
     * {@link JpaSystemException} (оба варианта трансляции {@code SQLSTATE 40001}
     * Spring-ом) метод будет повторён до {@code maxRetries} раз с экспоненциальной
     * задержкой.
     *
     * <p><strong>Важно:</strong> повторяется вся операция целиком, включая
     * повторное чтение списка дежурных врачей и повторную проверку бизнес-правила.
     * Повтор только последнего SQL недопустим с точки зрения уровня изоляции
     * {@code SERIALIZABLE}.
     *
     * <p>Поведение при исчерпании попыток: исключение пробрасывается выше
     * без обёртки.
     *
     * @param doctorId идентификатор врача, которого необходимо снять с дежурства;
     *                 не должен быть {@code null}
     * @throws ConcurrencyFailureException если все попытки исчерпаны
     *         и последняя завершилась ошибкой сериализации на уровне SQL
     * @throws JpaSystemException если все попытки исчерпаны
     *         и последняя завершилась ошибкой сериализации на этапе commit
     */
    @Retryable(
            includes = {ConcurrencyFailureException.class, JpaSystemException.class},
            maxRetries = 3,
            delay = 100,
            multiplier = 2.0
    )
    public void offCallWithSpringRetry(Long doctorId) {
        log.info("Запуск offCallWithSpringRetry, doctorId={}", doctorId);
        txService.offCallOnce(doctorId);
    }
}