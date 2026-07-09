package com.example.coldhotpublisher.service.fraud.audit;

import com.example.coldhotpublisher.model.FraudDecision;

/** Фиксация результата проверки заказа в журнале аудита (юридическая прослеживаемость). */
public interface FraudAuditService {

    void save(String orderId, FraudDecision decision);
}
