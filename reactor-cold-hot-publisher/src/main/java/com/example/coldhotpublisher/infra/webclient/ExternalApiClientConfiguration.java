package com.example.coldhotpublisher.infra.webclient;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>При старте приложения регистрирует все исходящие каналы к внешним системам в едином реестре.</p>
 * <p>Каталог, anti-fraud, тарифы и др. затем обращаются к реестру по типу системы, а не по имени бина.</p>
 */
@Configuration
public class ExternalApiClientConfiguration {

    @Bean
    public Map<ApiClientKind, ExternalApiClient> externalApiClientMap(List<ExternalApiClient> clients) {
        return clients.stream()
            .collect(Collectors.toUnmodifiableMap(ExternalApiClient::getKind, Function.identity()));
    }

    @Bean
    public ExternalApiClientRegistry externalApiClientRegistry(Map<ApiClientKind, ExternalApiClient> externalApiClientMap) {
        return new ExternalApiClientRegistry(externalApiClientMap);
    }
}
