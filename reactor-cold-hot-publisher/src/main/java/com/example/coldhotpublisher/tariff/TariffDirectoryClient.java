package com.example.coldhotpublisher.tariff;

import com.example.coldhotpublisher.config.DemoProperties;
import com.example.coldhotpublisher.dto.TariffTable;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * <p>Справочник тарифов, который дорого или редко меняется.</p>
 * <p>{@code Mono.defer(...).cache()} превращает cold HTTP в hot-источник с памятью:
 * повторные читатели не бьют в сеть, пока жив TTL.</p>
 */
@Slf4j
@Service
public class TariffDirectoryClient {

    private final WebClient tariffWebClient;
    private final Mono<TariffTable> cachedTariffs;

    public TariffDirectoryClient(@Qualifier("tariffWebClient") WebClient tariffWebClient,
                                 DemoProperties demoProperties) {
        this.tariffWebClient = tariffWebClient;
        this.cachedTariffs = Mono.defer(this::loadTariffs)
            .cache(Duration.ofMinutes(demoProperties.getCache().getTariffTtlMinutes()));
    }

    /** Всегда один и тот же закэшированный publisher — точка входа для потребителей. */
    public Mono<TariffTable> getTariffs() {
        return cachedTariffs;
    }

    /** Реальный HTTP-вызов; срабатывает только при первом subscribe после истечения кэша. */
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
