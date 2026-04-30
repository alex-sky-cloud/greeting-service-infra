package com.example.greeting.repeatableRead.service;

import java.util.List;
import com.example.greeting.entity.OrderLineEntity;
import com.example.greeting.repeatableRead.repository.RepeatableReadOrderLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для демонстрации аномалии phantom read
 * на уровне изоляции REPEATABLE READ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RepeatableReadAnomalyService {

    private final RepeatableReadOrderLineRepository orderLineRepository;

    /**
     * Демонстрирует phantom read на уровне REPEATABLE READ.
     *
     * <p>Метод выполняет два идентичных чтения набора строк заказа
     * в рамках одной транзакции. Между этими чтениями конкурентная
     * транзакция вставляет или удаляет строку того же заказа.</p>
     *
     * <p>На уровне REPEATABLE READ:</p>
     * <pre>
     * - повторное чтение одной и той же строки даёт тот же результат;
     * - но INSERT или DELETE в другой транзакции меняет набор строк,
     *   поэтому второй SELECT возвращает другое количество строк.
     * </pre>
     *
     * <p>Это и есть phantom read — строки-«призраки», которые
     * появляются или исчезают между двумя чтениями.</p>
     *
     * @param orderNo номер заказа для демонстрации аномалии
     * @return список строк заказа после второго чтения
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<OrderLineEntity> phantomReadMain(Long orderNo) {
        long firstCount = orderLineRepository.countByOrderNoNative(orderNo);
        long firstQtySum = orderLineRepository.sumQtyByOrderNoNative(orderNo);
        List<OrderLineEntity> firstRows = orderLineRepository.findAllByOrderNoNative(orderNo);

        log.info("Первое чтение: count (считаем количество заказов с одним номером) ={}", firstCount);
        log.info("Первое чтение: qtySum (считаем общее количество товара по всем заказам) ={}", firstQtySum);
        log.info("Первое чтение:  rows (получаем все строки с указанным номером заказа и вычисляем их количество ) ={}", firstRows.size());

        // breakpoint здесь

        long secondCount = orderLineRepository.countByOrderNoNative(orderNo);
        long secondQtySum = orderLineRepository.sumQtyByOrderNoNative(orderNo);
        List<OrderLineEntity> secondRows = orderLineRepository.findAllByOrderNoNative(orderNo);

        log.info("Второе чтение: count (считаем количество заказов с одним номером) ={}", firstCount);
        log.info("Второе чтение: qtySum (считаем общее количество товара по всем заказам) ={}", firstQtySum);
        log.info("Второе чтение:  rows (получаем все строки с указанным номером заказа и вычисляем их количество ) ={}", firstRows.size());

        return secondRows;
    }
}
