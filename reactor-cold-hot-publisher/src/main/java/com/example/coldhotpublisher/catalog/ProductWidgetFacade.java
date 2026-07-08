package com.example.coldhotpublisher.catalog;

import com.example.coldhotpublisher.dto.ProductDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductWidgetFacade {

    private final ProductCatalogClient productCatalogClient;

    public void coldMonoDemo(String productId) {
        Mono<ProductDto> productMono = productCatalogClient.getProduct(productId);

        productMono.subscribe(p -> log.info("widget-1 <- {}", p));
        productMono.subscribe(p -> log.info("widget-2 <- {}", p));
    }
}
