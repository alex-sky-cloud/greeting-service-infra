package com.example.coldhotpublisher.service.fraud.checker;

import com.example.coldhotpublisher.model.FraudDecision;
import reactor.core.publisher.Mono;

/** Проверка заказа на признаки мошенничества перед принятием в работу. */
public interface FraudChecker {

    Mono<FraudDecision> check(String orderId);
}
