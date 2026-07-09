package com.example.coldhotpublisher.infra.webclient;

import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * <p>Справочник исходящих каналов к внешним системам.</p>
 * <p>Сервис каталога, anti-fraud и др. запрашивают канал по типу ({@link ApiClientKind}),
 * не зная конкретной реализации и не привязываясь к имени бина.</p>
 */
public class ExternalApiClientRegistry {

    private final Map<ApiClientKind, ExternalApiClient> registry;

    public ExternalApiClientRegistry(Map<ApiClientKind, ExternalApiClient> registry) {
        this.registry = registry;
    }

    /** Канал связи с внешней системой указанного типа. */
    public ExternalApiClient get(ApiClientKind kind) {
        ExternalApiClient client = registry.get(kind);
        if (client == null) {
            throw new IllegalArgumentException("No ExternalApiClient registered for kind: " + kind);
        }
        return client;
    }

    /** HTTP-клиент для вызовов выбранной внешней системы. */
    public WebClient webClient(ApiClientKind kind) {
        return get(kind).webClient();
    }
}
