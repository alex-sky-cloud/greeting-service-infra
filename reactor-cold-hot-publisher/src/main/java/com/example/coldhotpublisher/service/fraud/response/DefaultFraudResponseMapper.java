package com.example.coldhotpublisher.service.fraud.response;

import com.example.coldhotpublisher.model.FraudDecision;
import com.example.coldhotpublisher.model.FraudResponseDto;
import org.springframework.stereotype.Component;

/** Преобразует вердикт службы anti-fraud в краткий ответ для фронта или мобильного клиента. */
@Component
public class DefaultFraudResponseMapper implements FraudResponseMapper {

    @Override
    public FraudResponseDto toDto(FraudDecision decision) {
        return new FraudResponseDto(decision.orderId(), decision.status());
    }
}
