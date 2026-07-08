package com.example.coldhotpublisher.catalog;

import com.example.coldhotpublisher.dto.ProductDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * <p>Тонкая обёртка над HTTP каталога.</p>
 * <p>Намеренно <em>не</em> делает {@code share()}/{@code cache()} — чтобы показать,
 * что обычный WebClient {@code Mono} ведёт себя как cold-источник.</p>
 */
@Slf4j
@Service
public class ProductCatalogClient {

    private final WebClient catalogWebClient;

    public ProductCatalogClient(@Qualifier("catalogWebClient") WebClient catalogWebClient) {
        this.catalogWebClient = catalogWebClient;
    }

    /**
     * <p>Каждый {@code subscribe()} на возвращённый {@code Mono} приведёт к новому GET.</p>
     * <p>Лог {@code catalog -> GET} — маркер реального сетевого side-effect.</p>
     */
    public Mono<ProductDto> getProduct(String productId) {
        return catalogWebClient.get()
            .uri("/products/{id}", productId)
            .retrieve()
            .bodyToMono(ProductDto.class)
            .doOnSubscribe(s -> log.info("catalog -> GET /products/{}", productId))
            .doOnNext(p -> log.info("catalog <- id={}, price={}", p.id(), p.price()))
            .doOnError(e -> log.error("catalog !! failed productId={}", productId, e));
    }
}
