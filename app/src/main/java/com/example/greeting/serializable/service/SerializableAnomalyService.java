package com.example.greeting.serializable.service;

import java.util.List;

import com.example.greeting.entity.OrderLineEntity;
import com.example.greeting.serializable.repository.SerializableOrderLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для демонстрации поведения phantom read
 * на уровне изоляции SERIALIZABLE.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SerializableAnomalyService {

    private final SerializableOrderLineRepository orderLineRepository;

    /**
     * <p>Дважды читает строки заказа в одной транзакции <code>SERIALIZABLE</code>.</p>
     *
     * <p><b>Сценарий выполнения:</b></p>
     * <ol>
     *   <li>Выполняется первое чтение строк заказа.</li>
     *   <li>Поток останавливается на breakpoint.</li>
     *   <li>Пока поток остановлен, из <code>psql</code> выполняется <code>INSERT</code>
     *       или <code>DELETE</code> с тем же <code>order_no</code> в отдельной
     *       транзакции <code>SERIALIZABLE</code>.</li>
     *   <li>После продолжения выполняется второе чтение.</li>
     *   <li>PostgreSQL либо вернёт тот же набор строк, либо откатит одну из
     *       транзакций с ошибкой сериализации.</li>
     * </ol>
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public List<OrderLineEntity> phantomReadSerializableMain(Long orderNo) {
        long firstCount = orderLineRepository.countByOrderNoNative(orderNo);
        long firstQtySum = orderLineRepository.sumQtyByOrderNoNative(orderNo);
        List<OrderLineEntity> firstRows = orderLineRepository.findAllByOrderNoNative(orderNo);

        log.info("Первое чтение: count={}, qtySum={}, rows={}",
                firstCount, firstQtySum, firstRows.size());

        // breakpoint здесь

        long secondCount = orderLineRepository.countByOrderNoNative(orderNo);
        long secondQtySum = orderLineRepository.sumQtyByOrderNoNative(orderNo);
        List<OrderLineEntity> secondRows = orderLineRepository.findAllByOrderNoNative(orderNo);

        log.info("Второе чтение: count={}, qtySum={}, rows={}",
                secondCount, secondQtySum, secondRows.size());

        return secondRows;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void phantomReadSerializableConcurrentInsert(Long orderNo,
                                                        String productName,
                                                        Integer qty,
                                                        String state) {
        orderLineRepository.insertNative(orderNo, productName, qty, state);

        log.info("Вставка новой записи");
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void phantomReadSerializableConcurrentDelete(Long orderNo,
                                                        String productName) {
        orderLineRepository.deleteByOrderNoAndProductNameNative(orderNo, productName);
    }
}
