package com.example.greeting.selectForShareAndSelectForUpdate.controller;

import com.example.greeting.selectForShareAndSelectForUpdate.service.SelectForShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/locks/for-share")
@RequiredArgsConstructor
@Slf4j
public class SelectForShareController {

    private final SelectForShareService selectForShareService;

    /**
     * HTTP‑endpoint для демонстрации работы SELECT ... FOR SHARE.
     *
     * <p>Сценарий:</p>
     * <pre>
     * 1. В psql (Tx1):
     *    BEGIN;
     *    SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
     *    SELECT id, order_no, product_code, qty
     *    FROM iso_demo.order_lines
     *    WHERE order_no = 1001 FOR SHARE;
     *    -- не делаем COMMIT
     *
     * 2. В другой консоли psql (Tx3) пытаемся обновить те же строки:
     *    BEGIN;
     *    UPDATE iso_demo.order_lines SET qty = qty + 5 WHERE order_no = 1001;
     *    -- встаёт в ожидание
     *
     * 3. Вызвать этот endpoint:
     *    GET /api/locks/for-share/order-qty?orderNo=1001
     *    -- Tx2 (app) тоже берёт FOR SHARE — успешно,
     *    -- потому что shared‑lock совместим с shared‑lock Tx1.
     *
     * 4. После COMMIT Tx1 и завершения Tx2 (app):
     *    UPDATE из Tx3 продолжает выполнение.
     * </pre>
     *
     * @param orderNo номер заказа
     * @return суммарное количество единиц по заказу
     */
    @GetMapping("/order-qty")
    public BigDecimal orderQty(@RequestParam Long orderNo) {
        BigDecimal bigDecimal = selectForShareService.calculateOrderQty(orderNo);
        log.info("++++++++");
        log.info("Количество заказов, после транзакции из приложения = {}", bigDecimal.toString());
        return bigDecimal;
    }
}