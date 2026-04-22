package com.example.greeting.selectForShareAndSelectForUpdate.controller;

import com.example.greeting.selectForShareAndSelectForUpdate.service.SelectForUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/locks/for-update")
@RequiredArgsConstructor
@Slf4j
public class SelectForUpdateController {

    private final SelectForUpdateService selectForUpdateService;

    /**
     * HTTP‑endpoint для демонстрации работы SELECT ... FOR UPDATE.
     *
     * <p>Сценарий:</p>
     * <pre>
     * 1. В psql (Tx1):
     *    BEGIN;
     *    SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
     *    SELECT id, balance FROM iso_demo.accounts WHERE id = 1 FOR UPDATE;
     *    UPDATE iso_demo.accounts SET balance = balance - 200.00 WHERE id = 1;
     *    -- не делаем COMMIT
     *
     * 2. Вызвать этот endpoint:
     *    POST /api/locks/for-update/debit?accountId=1&amount=100.00
     *
     * 3. UPDATE внутри сервиса будет ждать освобождения блокировки из Tx1.
     * 4. После COMMIT Tx1 — UPDATE сервиса продолжает выполнение.
     * </pre>
     *
     * @param accountId идентификатор счёта
     * @param amount    сумма списания
     * @return новый баланс после дебетования
     */
    @PostMapping("/debit")
    public BigDecimal debit(@RequestParam Long accountId,
                            @RequestParam BigDecimal amount) {

        BigDecimal balance = selectForUpdateService.debitAccount(accountId, amount);
        log.info("++++");
        log.info("balance после первой transaction, во 2-й транзакции = {}", balance);
        log.info("++++");

        return balance;
    }
}
