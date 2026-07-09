package com.example.coldhotpublisher.service.fraud.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Учебная реализация метрик: пишет счётчик в лог вместо реальной системы мониторинга. */
@Slf4j
@Service
public class LoggingFraudMetricsService implements FraudMetricsService {

    @Override
    public void incrementFraudStatus(String status) {
        log.info("metrics <- fraud_status={}", status);
    }
}
