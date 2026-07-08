package com.example.coldhotpublisher.catalog;

import com.example.coldhotpublisher.dto.ProductDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ProductCatalogClient {

    private final WebClient catalogWebClient;

    public ProductCatalogClient(@Qualifier("catalogWebClient") WebClient catalogWebClient) {
        this.catalogWebClient = catalogWebClient;
    }

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
