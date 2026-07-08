package com.example.coldhotpublisher.catalog;

import com.example.coldhotpublisher.dto.ProductDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * <p>Моделирует два UI-виджета, которым нужна одна и та же карточка товара.</p>
 * <p>Без общего hot-источника каждый виджет тянет каталог сам — типичная ловушка cold {@code Mono}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductWidgetFacade {

    private final ProductCatalogClient productCatalogClient;

    /**
     * <p>Подписывает два потребителя на один и тот же {@code Mono} без {@code share()}.</p>
     * <p>Ожидаемый вывод: два HTTP-запроса, два ответа в {@code widget-1} и {@code widget-2}.</p>
     */
    public void coldMonoDemo(String productId) {
        Mono<ProductDto> productMono = productCatalogClient.getProduct(productId);

        productMono.subscribe(p -> log.info("widget-1 <- {}", p));
        productMono.subscribe(p -> log.info("widget-2 <- {}", p));
    }
}
