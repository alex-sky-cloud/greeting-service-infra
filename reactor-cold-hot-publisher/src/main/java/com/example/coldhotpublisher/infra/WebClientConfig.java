package com.example.coldhotpublisher.infra;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/**
 * <p>Общие настройки исходящих HTTP-вызовов ко всем внешним системам.</p>
 * <p>Добавляет идентификатор запроса в заголовок — по нему в логах связывают цепочку вызовов.</p>
 */
@Configuration
public class WebClientConfig {

    @Bean
    public ExchangeFilterFunction correlationIdFilter() {
        return (request, next) -> next.exchange(
            ClientRequest.from(request)
                .header("X-Correlation-Id", UUID.randomUUID().toString())
                .build()
        );
    }
}
