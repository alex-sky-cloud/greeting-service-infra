package com.example.coldhotpublisher.fraud;

import com.example.coldhotpublisher.dto.FraudCheckRequest;
import com.example.coldhotpublisher.dto.FraudDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class FraudClient {

    private final WebClient fraudWebClient;

    public FraudClient(@Qualifier("fraudWebClient") WebClient fraudWebClient) {
        this.fraudWebClient = fraudWebClient;
    }

    public Mono<FraudDecision> check(String orderId) {
        return fraudWebClient.post()
            .uri("/fraud/check")
            .bodyValue(new FraudCheckRequest(orderId))
            .retrieve()
            .bodyToMono(FraudDecision.class)
            .doOnSubscribe(s -> log.info("fraud -> POST /fraud/check orderId={}", orderId))
            .doOnNext(d -> log.info("fraud <- orderId={}, status={}", d.orderId(), d.status()))
            .doOnError(e -> log.error("fraud !! failed orderId={}", orderId, e));
    }
}
