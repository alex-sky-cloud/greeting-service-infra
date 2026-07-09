package com.example.coldhotpublisher.service.fraud.response;

import com.example.coldhotpublisher.model.FraudDecision;
import com.example.coldhotpublisher.model.FraudResponseDto;

/** Формирование ответа клиенту API по результату проверки anti-fraud. */
public interface FraudResponseMapper {

    FraudResponseDto toDto(FraudDecision decision);
}
