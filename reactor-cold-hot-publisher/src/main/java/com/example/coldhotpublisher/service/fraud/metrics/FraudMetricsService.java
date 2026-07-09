package com.example.coldhotpublisher.service.fraud.metrics;

/** Учёт исходов проверок anti-fraud для мониторинга (сколько заказов разрешено / отклонено). */
public interface FraudMetricsService {

    void incrementFraudStatus(String status);
}
