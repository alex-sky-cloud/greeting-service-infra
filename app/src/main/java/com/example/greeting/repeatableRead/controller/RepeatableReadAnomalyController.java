package com.example.greeting.repeatableRead.controller;


import java.util.List;
import com.example.greeting.entity.OrderLineEntity;
import com.example.greeting.repeatableRead.service.RepeatableReadAnomalyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер для демонстрации аномалии phantom read
 * на уровне изоляции REPEATABLE READ.
 */
@RestController
@RequestMapping("/api/repeatable-read/anomalies")
@RequiredArgsConstructor
public class RepeatableReadAnomalyController {

    private final RepeatableReadAnomalyService anomalyService;

    /**
     * Запускает демонстрацию phantom read на REPEATABLE READ.
     *
     * <p>Сценарий использования:</p>
     * <pre>
     * 1. Из приложения вызывается:
     *    POST /api/repeatable-read/anomalies/phantom-read/main?orderNo=1001
     *
     * 2. Метод выполняет первое чтение набора строк заказа
     *    и останавливается на breakpoint.
     *
     * 3. В консоли psql выполняется INSERT или DELETE строки
     *    того же заказа и фиксируется commit.
     *
     * 4. После продолжения метод выполняет второе чтение
     *    и в логах видна разница между первым и вторым набором строк.
     * </pre>
     *
     * @param orderNo номер заказа
     * @return список строк заказа после второго чтения
     */
    @PostMapping("/phantom-read/main")
    public List<OrderLineEntity> phantomReadMain(@RequestParam ("orderNo") Long orderNo) {
        return anomalyService.phantomReadMain(orderNo);
    }
}
