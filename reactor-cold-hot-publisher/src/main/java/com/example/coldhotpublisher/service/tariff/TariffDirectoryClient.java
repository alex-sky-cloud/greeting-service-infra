package com.example.coldhotpublisher.service.tariff;

import com.example.coldhotpublisher.config.DemoProperties;
import com.example.coldhotpublisher.model.TariffTable;
import com.example.coldhotpublisher.infra.webclient.ApiClientKind;
import com.example.coldhotpublisher.infra.webclient.ExternalApiClientRegistry;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * <p>Загружает справочник тарифов из внешней службы доставки.</p>
 * <p>Таблица меняется редко: после первой загрузки повторные запросы корзины и виджетов
 * не должны снова тянуть тяжёлый справочник с источника.</p>
 */
@Slf4j
@Service
public class TariffDirectoryClient implements TariffDirectory {

    private final WebClient tariffWebClient;
    private final Mono<TariffTable> cachedTariffs;

    public TariffDirectoryClient(ExternalApiClientRegistry externalApiClients,
                                 DemoProperties demoProperties) {
        this.tariffWebClient = externalApiClients.webClient(ApiClientKind.TARIFF);
        this.cachedTariffs = Mono.defer(this::loadTariffs)
            .cache(Duration.ofMinutes(demoProperties.getCache().getTariffTtlMinutes()));
    }

    @Override
    public Mono<TariffTable> getTariffs() {
        return cachedTariffs;
    }

    private Mono<TariffTable> loadTariffs() {
        return tariffWebClient.get()
            .uri("/tariffs")
            .retrieve()
            .bodyToMono(TariffTable.class)
            .doOnSubscribe(s -> log.info("tariff -> GET /tariffs"))
            .doOnNext(t -> log.info("tariff <- version={}", t.version()))
            .doOnError(e -> log.error("tariff !! failed", e));
    }
}
