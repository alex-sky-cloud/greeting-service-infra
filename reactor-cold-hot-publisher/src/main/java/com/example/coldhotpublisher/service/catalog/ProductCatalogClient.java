package com.example.coldhotpublisher.service.catalog;

import com.example.coldhotpublisher.model.ProductDto;
import com.example.coldhotpublisher.infra.webclient.ApiClientKind;
import com.example.coldhotpublisher.infra.webclient.ExternalApiClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * <p>Загружает карточку товара из внешнего каталога.</p>
 * <p>Каждый независимый потребитель (виджет на странице) инициирует свой запрос —
 * в учебном сценарии это видно как два обращения к каталогу за одним товаром.</p>
 */
@Slf4j
@Service
public class ProductCatalogClient implements ProductCatalog {

    private final WebClient catalogWebClient;

    public ProductCatalogClient(ExternalApiClientRegistry externalApiClients) {
        this.catalogWebClient = externalApiClients.webClient(ApiClientKind.CATALOG);
    }

    @Override
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
