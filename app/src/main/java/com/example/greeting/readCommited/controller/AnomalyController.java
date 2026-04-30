package com.example.greeting.readCommited.controller;

import com.example.greeting.entity.OnCallDoctorEntity;
import com.example.greeting.entity.OrderLineEntity;
import com.example.greeting.readCommited.repository.OrderLineRepository;
import com.example.greeting.readCommited.service.AnomalyService;
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


        BigDecimal lostUpdateMain = anomalyService.lostUpdateMain(accountId, delta);

        log.info("+++++");
        log.info("Баланс клиента - 2 транзакция выполнила update : " + lostUpdateMain.toString());
        log.info("+++++");

        return lostUpdateMain;
    }

    /**
     * Можно использовать атомарный SQL-апдейт вместо схемы
     * «прочитать баланс в приложение → посчитать → записать».
     *
     * <p>Пример: </p>
     * <pre>
     * {@code
     * update iso_demo.accounts
     *    set balance = balance + :delta,
     *        updated_at = now()
     *  where id = :id
     * }</pre>
     */
    @PostMapping("/lost-update/main/correct")
    public BigDecimal correctLostUpdateMain(@RequestParam Long accountId,
                                     @RequestParam BigDecimal delta) {


        BigDecimal lostUpdateMain = anomalyService.correctLostUpdateMainS(accountId, delta);

        log.info("+++++");
        log.info("Баланс клиента - 2 транзакция выполнила update : " + lostUpdateMain.toString());
        log.info("+++++");

        return lostUpdateMain;
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

    /**
     * Запускает из приложения Транзакцию 2 для демонстрации write skew.
     *
     * <p>Сценарий использования:</p>
     * <pre>
     * 1. В psql (Транзакция 1) выполнить:
     *    BEGIN;
     *    SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
     *    SELECT count(*) FROM iso_demo.on_call_doctors WHERE on_call = true;
     *    UPDATE iso_demo.on_call_doctors
     *    SET on_call = false, updated_at = now()
     *    WHERE id = 1;
     *    COMMIT;
     *
     * 2. Параллельно из приложения вызвать этот endpoint:
     *    POST /api/anomalies/write-skew/main?doctorId=2
     *
     * 3. В writeSkewMain(2) поставить breakpoint сразу после
     *    чтения количества дежурных. Пока поток остановлен,
     *    выполнить шаги Транзакции 1 в psql.
     *
     * 4. После продолжения выполнения writeSkewMain(2) врач
     *    с id = 2 также снимается с дежурства.
     * </pre>
     *
     * @param doctorId идентификатор врача, которого снимаем с дежурства
     */
    @PostMapping("/write-skew/main")
    public void writeSkewMain(@RequestParam ("doctorId") Long doctorId) {
        anomalyService.writeSkewMain(doctorId);
    }

    /**
     * Возвращает текущий график дежурств после выполнения сценария
     * write skew, чтобы можно было убедиться в результате.
     *
     * @return список врачей с их on_call-флагами
     */
    @PostMapping("/write-skew/schedule")
    public List<OnCallDoctorEntity> onCallSchedule() {
        return anomalyService.getOnCallSchedule();
    }
}
