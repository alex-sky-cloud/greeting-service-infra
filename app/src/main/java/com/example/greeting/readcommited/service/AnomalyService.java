package com.example.greeting.readcommited.service;

import java.math.BigDecimal;
import java.util.List;

import com.example.greeting.entity.OrderLineEntity;
import com.example.greeting.readcommited.repository.AccountRepository;
import com.example.greeting.readcommited.repository.OnCallDoctorRepository;
import com.example.greeting.readcommited.repository.OrderLineRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AnomalyService {

    private final AccountRepository accountRepository;
    private final OrderLineRepository orderLineRepository;
    private final OnCallDoctorRepository onCallDoctorRepository;

    public AnomalyService(AccountRepository accountRepository,
                          OrderLineRepository orderLineRepository,
                          OnCallDoctorRepository onCallDoctorRepository
    ) {
        this.accountRepository = accountRepository;
        this.orderLineRepository = orderLineRepository;
        this.onCallDoctorRepository = onCallDoctorRepository;
    }


    /**
     * Дважды читает баланс счёта в одной транзакции с уровнем изоляции
     * {@link Isolation#READ_COMMITTED}.
     *
     * <p>Метод нужен для демонстрации аномалии non-repeatable read.</p>
     *
     * <p>Сценарий выполнения:</p>
     * <pre>
     * 1. Метод запускается из приложения.
     * 2. Выполняется первое чтение balance.
     * 3. На следующей строке ставится breakpoint.
     * 4. Пока поток приложения остановлен в debugger, параллельная транзакция
     *    вручную запускается из консоли psql.
     * 5. В psql параллельная транзакция изменяет balance этого же счёта и делает commit.
     * 6. После продолжения выполнения в IDE метод делает второе чтение balance.
     * 7. Если второе значение отличается от первого, аномалия подтверждена.
     * </pre>
     *
     * <p>На уровне {@code READ_COMMITTED} это допустимо:
     * каждый следующий {@code SELECT} может увидеть изменения,
     * зафиксированные другой транзакцией между двумя чтениями.</p>
     *
     * @param accountId идентификатор счёта
     * @return второе прочитанное значение баланса
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BigDecimal nonRepeatableReadMain(Long accountId) {

        BigDecimal firstBalance = accountRepository.findBalanceByIdNative(accountId);

        // breakpoint здесь

        log.info("+++++");
        log.info("Баланс клиента : " + firstBalance.toString());
        log.info("+++++");
        BigDecimal secondBalance = accountRepository.findBalanceByIdNative(accountId);

        return secondBalance;
    }



    /**
     * Обновляет баланс счёта в отдельной транзакции с уровнем изоляции
     * {@link Isolation#READ_COMMITTED} для демонстрации non-repeatable read.
     *
     * <p>Сценарий использования:</p>
     * <pre>
     * 1. Из приложения запускается метод nonRepeatableReadMain(accountId),
     *    который читает баланс и останавливается на breakpoint.
     * 2. Пока основной поток остановлен в debugger, из консоли psql
     *    выполняется вызов этого метода (второй транзакции).
     * 3. Метод записывает новое значение balance и фиксирует транзакцию (commit).
     * 4. Затем продолжается выполнение nonRepeatableReadMain(accountId),
     *    который делает второе чтение balance.
     * </pre>
     *
     * @param accountId  идентификатор счёта, баланс которого нужно изменить
     * @param newBalance новое значение баланса, которое записывается и фиксируется
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void nonRepeatableReadConcurrent(Long accountId, BigDecimal newBalance) {
        accountRepository.updateBalanceByIdNative(accountId, newBalance);
    }



    /**
     * Выполняет сценарий lost update в одной транзакции с уровнем изоляции
     * {@link Isolation#READ_COMMITTED}.
     *
     * <p>Сценарий использования:</p>
     * <pre>
     * 1. Из приложения запускается этот метод.
     * 2. Метод читает текущее значение balance.
     * 3. На основе прочитанного значения вычисляет новое значение balance.
     * 4. Перед update ставится breakpoint.
     * 5. Пока поток остановлен в debugger, из консоли psql запускается
     *    вторая транзакция, которая тоже изменяет balance и делает commit.
     * 6. После продолжения этот метод выполняет update по ранее вычисленному значению.
     * 7. Если update второй транзакции был затёрт, фиксируется lost update.
     * </pre>
     *
     * @param accountId идентификатор счёта
     * @param delta величина изменения баланса, которая прибавляется к уже прочитанному значению
     * @return итоговое значение balance после update, выполненного этой транзакцией
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BigDecimal lostUpdateMain(Long accountId, BigDecimal delta) {
        BigDecimal before = accountRepository.findBalanceByIdNative(accountId);
        BigDecimal calculated = before.add(delta);

        // breakpoint здесь

        accountRepository.updateBalanceByIdNative(accountId, calculated);

        return accountRepository.findBalanceByIdNative(accountId);
    }


    /**
     * Выполняет вторую транзакцию для сценария lost update
     * с уровнем изоляции {@link Isolation#READ_COMMITTED}.
     *
     * <p>Сценарий использования:</p>
     * <pre>
     * 1. Из приложения запускается метод lostUpdateMain(accountId, delta).
     * 2. Он читает balance, вычисляет новое значение и останавливается на breakpoint
     *    перед выполнением update.
     * 3. Пока поток приложения остановлен в debugger, из консоли psql
     *    запускается эта вторая транзакция.
     * 4. Эта транзакция записывает своё заранее вычисленное значение balance
     *    и фиксирует его commit.
     * 5. Затем выполнение lostUpdateMain(accountId, delta) продолжается,
     *    и его update может затереть уже зафиксированное значение.
     * </pre>
     *
     * @param accountId идентификатор счёта
     * @param calculated заранее вычисленное значение balance,
     *                   которое записывается этой транзакцией
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void lostUpdateConcurrent(Long accountId, BigDecimal calculated) {
        accountRepository.updateBalanceByIdNative(accountId, calculated);
    }


    /**
     * Выполняет сценарий phantom read в одной транзакции с уровнем изоляции
     * {@link Isolation#READ_COMMITTED}.
     *
     * <p>Сценарий использования:</p>
     * <pre>
     * 1. Из приложения запускается этот метод.
     * 2. Метод первый раз читает строки заказа по order_no:
     *    count(*), sum(qty) и полный список строк.
     * 3. После первого чтения ставится breakpoint.
     * 4. Пока поток приложения остановлен в debugger, из консоли psql
     *    запускается вторая транзакция, которая вставляет новую строку
     *    этого же заказа или удаляет существующую, затем делает commit.
     * 5. После продолжения этот метод повторно читает тот же набор строк.
     * 6. Если count, sum(qty) или список строк изменились,
     *    фиксируется phantom read.
     * </pre>
     *
     * @param orderNo номер заказа, строки которого читаются дважды
     * @return список строк заказа после второго чтения
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<OrderLineEntity> phantomReadMain(Long orderNo) {
        long firstCount = orderLineRepository.countByOrderNoNative(orderNo);

        log.info("+++++");
        log.info("Количество позиций в заказе (по номеру заказа) - 1-я выборка в этой же транзакции : " + firstCount);
        log.info("+++++");

        long firstQtySum = orderLineRepository.sumQtyByOrderNoNative(orderNo);
        log.info("+++++");
        log.info("Общее количество штук товара, по каждой позиции заказа (по номеру заказа) - 1-я выборка в этой же транзакции : " + firstQtySum);
        log.info("+++++");
        List<OrderLineEntity> firstRows = orderLineRepository.findAllByOrderNoNative(orderNo);

        log.info("+++++");
        log.info("Общее количество заказов - 1-я выборка в этой же транзакции : " + firstRows.size());
        log.info("+++++");

        // breakpoint здесь

        List<OrderLineEntity> secondRows = orderLineRepository.findAllByOrderNoNative(orderNo);

        return secondRows;
    }

    /**
     * Выполняет вторую транзакцию для сценария phantom read
     * с уровнем изоляции {@link Isolation#READ_COMMITTED}.
     *
     * <p>Сценарий использования:</p>
     * <pre>
     * 1. Из приложения запускается метод phantomReadMain(orderNo).
     * 2. Он первый раз читает строки заказа и останавливается на breakpoint.
     * 3. Пока поток приложения остановлен в debugger, из консоли psql
     *    запускается эта вторая транзакция.
     * 4. Эта транзакция вставляет новую строку с тем же order_no
     *    и фиксирует изменения commit.
     * 5. После этого выполнение phantomReadMain(orderNo) продолжается,
     *    и повторное чтение может увидеть новую строку.
     * </pre>
     *
     * @param orderNo номер заказа, в который добавляется новая строка
     * @param productName наименование товара в новой строке
     * @param qty количество товара в новой строке
     * @param state состояние новой строки заказа
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void phantomReadConcurrentInsert(Long orderNo,
                                            String productName,
                                            Integer qty,
                                            String state) {
        orderLineRepository.insertNative(orderNo, productName, qty, state);
    }


    /**
     * Выполняет вторую транзакцию для сценария phantom read
     * с уровнем изоляции {@link Isolation#READ_COMMITTED}.
     *
     * <p>Сценарий использования:</p>
     * <pre>
     * 1. Из приложения запускается метод phantomReadMain(orderNo).
     * 2. Он первый раз читает строки заказа и останавливается на breakpoint.
     * 3. Пока поток приложения остановлен в debugger, из консоли psql
     *    запускается эта вторая транзакция.
     * 4. Эта транзакция удаляет одну из строк заказа и фиксирует изменения commit.
     * 5. После этого выполнение phantomReadMain(orderNo) продолжается,
     *    и повторное чтение может увидеть уже другой набор строк.
     * </pre>
     *
     * @param id идентификатор строки заказа, которую нужно удалить
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void phantomReadConcurrentDelete(Long id) {
        orderLineRepository.deleteByIdNative(id);
    }
}
