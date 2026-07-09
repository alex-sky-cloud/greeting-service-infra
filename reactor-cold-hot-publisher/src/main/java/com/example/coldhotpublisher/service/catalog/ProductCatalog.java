package com.example.coldhotpublisher.service.catalog;

import com.example.coldhotpublisher.model.ProductDto;
import reactor.core.publisher.Mono;

/** Доступ к каталогу товаров: карточка по идентификатору. */
public interface ProductCatalog {

    Mono<ProductDto> getProduct(String productId);
}
