package com.example.coldhotpublisher.service.fraud;

import com.example.coldhotpublisher.model.FraudDecision;
import com.example.coldhotpublisher.model.FraudResponseDto;
import com.example.coldhotpublisher.service.fraud.audit.FraudAuditService;
import com.example.coldhotpublisher.service.fraud.checker.FraudChecker;
import com.example.coldhotpublisher.service.fraud.metrics.FraudMetricsService;
import com.example.coldhotpublisher.service.fraud.response.FraudResponseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * <p>Обработка принятого заказа: одна проверка на мошенничество, несколько последствий.</p>
 * <p>Результат уходит в журнал аудита, в метрики и в ответ клиенту — службу anti-fraud
 * нельзя дергать трижды за один и тот же заказ.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFraudOrchestrator {

    private final FraudChecker fraudChecker;
    private final FraudAuditService fraudAuditService;
    private final FraudMetricsService fraudMetricsService;
    private final FraudResponseMapper fraudResponseMapper;

    /**
     * <p>Принимает заказ: проверка anti-fraud выполняется один раз, итог расходится
     * по аудиту, метрикам и ответу HTTP-клиенту магазина.</p>
     */
    public Mono<FraudResponseDto> processOrder(String orderId) {
        Mono<FraudDecision> sharedCheck = fraudChecker.check(orderId).share();

        sharedCheck.subscribe(d -> fraudAuditService.save(orderId, d));
        sharedCheck.subscribe(d -> fraudMetricsService.incrementFraudStatus(d.status()));

        return sharedCheck
            .map(fraudResponseMapper::toDto)
            .doOnNext(dto -> log.info("response <- {}", dto));
    }
}
