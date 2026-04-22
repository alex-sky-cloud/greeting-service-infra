package com.example.greeting.readcommited.controller;

import com.example.greeting.entity.OrderLineEntity;
import com.example.greeting.readcommited.repository.OrderLineRepository;
import com.example.greeting.readcommited.service.AnomalyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/anomalies")
@Slf4j
public class AnomalyController {

    private final AnomalyService anomalyService;
    private final OrderLineRepository orderLineRepository;

    public AnomalyController(AnomalyService anomalyService, OrderLineRepository orderLineRepository) {
        this.anomalyService = anomalyService;
        this.orderLineRepository = orderLineRepository;
    }

    /**
     * Запускает основную транзакцию из приложения для демонстрации non-repeatable read.
     *
     * <p>Сценарий использования:</p>
     * <pre>
     * 1. Вызывается этот endpoint.
     * 2. Внутри сервиса выполняется первое чтение balance.
     * 3. Поток приложения останавливается на breakpoint.
     * 4. Пока поток остановлен, из консоли psql запускается вторая транзакция.
     * 5. После продолжения выполняется второе чтение balance.
     * </pre>
     *
     * @param accountId идентификатор счёта
     * @return значение balance, прочитанное второй раз
     */
    @PostMapping("/non-repeatable-read/main")
    public BigDecimal nonRepeatableReadMain(@RequestParam Long accountId) {

        BigDecimal secondBalance = anomalyService.nonRepeatableReadMain(accountId);
        log.info("+++++");
        log.info("Баланс клиента - 2-я выборка в этой же транзакции : " + secondBalance.toString());
        log.info("+++++");
        return secondBalance;
    }

    /**
     * Запускает вторую транзакцию для сценария non-repeatable read.
     *
     * <p>Эта транзакция вызывается отдельно, пока основная транзакция приложения
     * остановлена на breakpoint.</p>
     *
     * @param accountId идентификатор счёта
     * @param newBalance новое значение balance
     */
    @PostMapping("/non-repeatable-read/concurrent")
    public void nonRepeatableReadConcurrent(@RequestParam Long accountId,
                                            @RequestParam BigDecimal newBalance) {
        anomalyService.nonRepeatableReadConcurrent(accountId, newBalance);
    }

    /**
     * Запускает основную транзакцию из приложения для демонстрации lost update.
     *
     * <p>Сценарий использования:</p>
     * <pre>
     * 1. Вызывается этот endpoint.
     * 2. Внутри сервиса читается текущее значение balance.
     * 3. Вычисляется новое значение balance.
     * 4. Поток приложения останавливается на breakpoint перед update.
     * 5. Пока поток остановлен, из консоли psql запускается вторая транзакция.
     * 6. После продолжения выполняется update по ранее вычисленному значению.
     * </pre>
     *
     * @param accountId идентификатор счёта
     * @param delta величина изменения баланса
     * @return итоговое значение balance после update этой транзакции
     */
    @PostMapping("/lost-update/main")
    public BigDecimal lostUpdateMain(@RequestParam Long accountId,
                                     @RequestParam BigDecimal delta) {
        return anomalyService.lostUpdateMain(accountId, delta);
    }

    /**
     * Запускает вторую транзакцию для сценария lost update.
     *
     * <p>Эта транзакция вызывается отдельно, пока основная транзакция приложения
     * остановлена на breakpoint перед update.</p>
     *
     * @param accountId идентификатор счёта
     * @param calculated заранее вычисленное значение balance
     */
    @PostMapping("/lost-update/concurrent")
    public void lostUpdateConcurrent(@RequestParam Long accountId,
                                     @RequestParam BigDecimal calculated) {
        anomalyService.lostUpdateConcurrent(accountId, calculated);
    }

    /**
     * Запускает основную транзакцию из приложения для демонстрации phantom read.
     *
     * <p>Сценарий использования:</p>
     * <pre>
     * 1. Вызывается этот endpoint.
     * 2. Внутри сервиса первый раз читаются строки заказа по orderNo.
     * 3. Поток приложения останавливается на breakpoint.
     * 4. Пока поток остановлен, из консоли psql запускается вторая транзакция.
     * 5. После продолжения строки заказа читаются повторно.
     * </pre>
     *
     * @param orderNo номер заказа
     * @return список строк заказа после второго чтения
     */
    @PostMapping("/phantom-read/main")
    public List<OrderLineEntity> phantomReadMain(@RequestParam ("orderNo") Long orderNo) {
        List<OrderLineEntity> orderLineEntities = anomalyService.phantomReadMain(orderNo);

        log.info("+++++");
        log.info("Общее количество заказов - 2-я выборка в этой же транзакции : " + orderLineEntities.size());
        log.info("+++++");


        long firstCount = orderLineRepository.countByOrderNoNative(orderNo);

        log.info("+++++");
        log.info("Количество позиций в заказе (по номеру заказа) - 1-я выборка в этой же транзакции : " + firstCount);
        log.info("+++++");

        long firstQtySum = orderLineRepository.sumQtyByOrderNoNative(orderNo);
        log.info("+++++");
        log.info("Общее количество штук товара, по каждой позиции заказа (по номеру заказа) - 1-я выборка в этой же транзакции : " + firstQtySum);
        log.info("+++++");
        return orderLineEntities;
    }

    /**
     * Запускает вторую транзакцию для сценария phantom read через вставку новой строки.
     *
     * @param orderNo номер заказа
     * @param productName название товара
     * @param qty количество
     * @param state состояние строки заказа
     */
    @PostMapping("/phantom-read/concurrent/insert")
    public void phantomReadConcurrentInsert(@RequestParam Long orderNo,
                                            @RequestParam String productName,
                                            @RequestParam Integer qty,
                                            @RequestParam String state) {
        anomalyService.phantomReadConcurrentInsert(orderNo, productName, qty, state);
    }

    /**
     * Запускает вторую транзакцию для сценария phantom read через удаление строки.
     *
     * @param id идентификатор строки заказа
     */
    @PostMapping("/phantom-read/concurrent/delete")
    public void phantomReadConcurrentDelete(@RequestParam Long id) {
        anomalyService.phantomReadConcurrentDelete(id);
    }
}
