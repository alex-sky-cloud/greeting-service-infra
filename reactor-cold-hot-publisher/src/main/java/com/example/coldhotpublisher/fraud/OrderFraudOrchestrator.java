package com.example.coldhotpublisher.fraud;

import com.example.coldhotpublisher.dto.FraudDecision;
import com.example.coldhotpublisher.dto.FraudResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFraudOrchestrator {

    private final FraudClient fraudClient;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ResponseMapper responseMapper;

    public void processOrder(String orderId) {
        Mono<FraudDecision> sharedCheck =
            fraudClient.check(orderId)
                .share();

        sharedCheck.subscribe(d -> auditService.save(orderId, d));
        sharedCheck.subscribe(d -> metricsService.incrementFraudStatus(d.status()));
        sharedCheck.map(responseMapper::toDto)
            .subscribe(dto -> log.info("response <- {}", dto));
    }
}

@Slf4j
@Service
class AuditService {
    public void save(String orderId, FraudDecision decision) {
        log.info("audit <- orderId={}, status={}", orderId, decision.status());
    }
}

@Slf4j
@Service
class MetricsService {
    public void incrementFraudStatus(String status) {
        log.info("metrics <- fraud_status={}", status);
    }
}

@Component
class ResponseMapper {
    public FraudResponseDto toDto(FraudDecision decision) {
        return new FraudResponseDto(decision.orderId(), decision.status());
    }
}
