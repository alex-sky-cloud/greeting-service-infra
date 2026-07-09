package com.example.coldhotpublisher.service.fraud.audit;

import com.example.coldhotpublisher.model.FraudDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Учебная реализация аудита: пишет решение по заказу в лог вместо реального хранилища. */
@Slf4j
@Service
public class LoggingFraudAuditService implements FraudAuditService {

    @Override
    public void save(String orderId, FraudDecision decision) {
        log.info("audit <- orderId={}, status={}", orderId, decision.status());
    }
}
