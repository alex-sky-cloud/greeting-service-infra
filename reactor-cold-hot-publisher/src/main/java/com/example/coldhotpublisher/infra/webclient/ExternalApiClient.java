package com.example.coldhotpublisher.infra.webclient;

import org.springframework.web.reactive.function.client.WebClient;

/**
 * <p>Исходящий канал к внешней системе определённого типа (каталог, anti-fraud, тарифы…).</p>
 * <p>Каждая реализация знает свой {@link ApiClientKind}; при старте все каналы собираются в реестр
 * ({@link ExternalApiClientConfiguration}).</p>
 */
public interface ExternalApiClient {

    /** К какой внешней системе относится этот канал. */
    ApiClientKind getKind();

    /** Клиент для вызовов этой системы. */
    WebClient webClient();
}
