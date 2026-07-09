package com.example.coldhotpublisher.service.fraud.checker;

import com.example.coldhotpublisher.model.FraudDecision;
import com.example.coldhotpublisher.infra.webclient.ApiClientKind;
import com.example.coldhotpublisher.infra.webclient.ExternalApiClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * <p>Обращается к внешней службе anti-fraud за вердиктом по заказу.</p>
 * <p>Возвращает решение (разрешить / отклонить) — его дальше использует {@link com.example.coldhotpublisher.service.fraud.OrderFraudOrchestrator}.</p>
 */
@Slf4j
@Service
public class WebClientFraudChecker implements FraudChecker {

    private final WebClient fraudWebClient;

    public WebClientFraudChecker(ExternalApiClientRegistry externalApiClients) {
        this.fraudWebClient = externalApiClients.webClient(ApiClientKind.FRAUD);
    }

    @Override
    public Mono<FraudDecision> check(String orderId) {
        return fraudWebClient.post()
            .uri("/fraud/check/{orderId}", orderId)
            .retrieve()
            .bodyToMono(FraudDecision.class)
            .doOnSubscribe(s -> log.info("fraud -> POST /fraud/check/{}", orderId))
            .doOnNext(d -> log.info("fraud <- orderId={}, status={}", d.orderId(), d.status()))
            .doOnError(e -> log.error("fraud !! failed orderId={}", orderId, e));
    }
}
