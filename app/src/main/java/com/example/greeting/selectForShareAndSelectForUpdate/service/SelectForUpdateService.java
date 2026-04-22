package com.example.greeting.selectForShareAndSelectForUpdate.service;

import com.example.greeting.selectForShareAndSelectForUpdate.repository.SelectForUpdateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class SelectForUpdateService {

    private final SelectForUpdateRepository selectForUpdateRepository;

    /**
     * Дебетует счёт на указанную сумму с использованием SELECT ... FOR UPDATE.
     *
     * <p>Шаги:</p>
     * <ol>
     *   <li>Выполняет SELECT ... FOR UPDATE — если строка уже заблокирована
     *       другой транзакцией (например, Tx1 в psql), ждёт её завершения.</li>
     *   <li>После снятия блокировки читает <strong>актуальный</strong> баланс
     *       (уже после COMMIT Tx1).</li>
     * ```
     *   <li>Рассчитывает новый баланс: {@code newBalance = balance - amount}.</li>
     * ```
     *   <li>Обновляет строку счёта.</li>
     * </ol>
     *
     * <p>Ожидаемое поведение в сценарии:</p>
     * <ul>
     * ```
     *   <li>Tx1 (psql): списала 200.00, зафиксировала баланс, сделала COMMIT.</li>
     * ```
     * ```
     *   <li>Tx2 (app):  прочитала уже обновлённый баланс, списала 100.00 поверх него.</li>
     * ```
     * ```
     *   <li>Итог: оба списания применены корректно, lost update отсутствует.</li>
     * ```
     * </ul>
     *
     * @param accountId идентификатор счёта
     * @param amount    сумма списания
     * @return новый баланс после дебетования
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BigDecimal debitAccount(Long accountId, BigDecimal amount) {

        BigDecimal balance = selectForUpdateRepository.findBalanceByIdForUpdate(accountId);

        log.info("Обычный Select");
        log.info("Первая транзакция все еще держит для блокировки строку для обновления = {}", balance);
        log.info("++++");

        // breakpoint здесь:
        // пока Tx1 (psql) открыта — мы сюда не попадём, метод ждёт на SELECT FOR UPDATE.
        // после COMMIT Tx1 — balance содержит уже обновлённое значение.
        BigDecimal newBalance = balance.subtract(amount);
        selectForUpdateRepository.updateBalance(accountId, newBalance);
        return newBalance;
    }
}