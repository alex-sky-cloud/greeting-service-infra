package com.example.greeting.serializable.controller;

import java.util.List;

import com.example.greeting.entity.OrderLineEntity;
import com.example.greeting.serializable.service.SerializableAnomalyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер для демонстрации phantom read
 * на уровне изоляции SERIALIZABLE.
 */
@RestController
@RequestMapping("/api/serializable/anomalies")
@RequiredArgsConstructor
public class SerializableAnomalyController {

    private final SerializableAnomalyService anomalyService;

    /**
     * <p>Запускает основную транзакцию <code>SERIALIZABLE</code>
     * для сценария <code>phantom read</code>.</p>
     *
     * <p><b>Сценарий использования:</b></p>
     * <ol>
     *   <li>Вызывается этот endpoint из приложения.</li>
     *   <li>Внутри сервиса первый раз читаются строки заказа.</li>
     *   <li>Поток останавливается на breakpoint.</li>
     *   <li>Пока поток остановлен, из <code>psql</code> выполняется
     *       конкурентная транзакция.</li>
     *   <li>После продолжения строки заказа читаются повторно.</li>
     * </ol>
     */
    @PostMapping("/phantom-read/main")
    public List<OrderLineEntity> phantomReadSerializableMain(@RequestParam ("orderNo") Long orderNo) {
        return anomalyService.phantomReadSerializableMain(orderNo);
    }

    /**
     * <p>Запускает конкурентную транзакцию <code>SERIALIZABLE</code> для вставки новой строки заказа.</p>
     *
     * <p><b>Пример запроса:</b></p>
     * <pre>
     * POST /api/serializable/anomalies/phantom-read/concurrent-insert?orderNo=1001&productName=Phantom-item&qty=3&state=NEW
     * </pre>
     *
     * @param orderNo номер заказа
     * @param productName название товара
     * @param qty количество
     * @param state состояние строки заказа
     */
    @PostMapping("/phantom-read/concurrent-insert")
    public void phantomReadSerializableConcurrentInsert(@RequestParam("orderNo") Long orderNo,
                                                        @RequestParam String productName,
                                                        @RequestParam Integer qty,
                                                        @RequestParam String state) {
        anomalyService.phantomReadSerializableConcurrentInsert(orderNo, productName, qty, state);
    }

    /**
     * <p>Запускает конкурентную транзакцию <code>SERIALIZABLE</code> для удаления строки заказа.</p>
     *
     * <p><b>Пример запроса:</b></p>
     * <pre>
     * POST /api/serializable/anomalies/phantom-read/concurrent-delete?orderNo=1001&productName=Keyboard
     * </pre>
     *
     * @param orderNo номер заказа
     * @param productName название товара удаляемой строки
     */
    @PostMapping("/phantom-read/concurrent-delete")
    public void phantomReadSerializableConcurrentDelete(@RequestParam("orderNo") Long orderNo,
                                                        @RequestParam String productName) {
        anomalyService.phantomReadSerializableConcurrentDelete(orderNo, productName);
    }
}
