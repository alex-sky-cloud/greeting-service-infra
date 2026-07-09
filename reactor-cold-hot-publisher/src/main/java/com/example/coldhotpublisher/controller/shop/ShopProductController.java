package com.example.coldhotpublisher.controller.shop;

import com.example.coldhotpublisher.model.ProductDto;
import com.example.coldhotpublisher.service.catalog.ProductCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>HTTP API магазина: карточка товара для витрины.</p>
 * <p>Клиент (браузер, curl) вызывает этот endpoint; запрос к внешнему каталогу
 * инициирует {@link ProductCatalog} через исходящий {@code WebClient}.</p>
 * <p>Два независимых GET за одним {@code productId} — учебный cold {@code Mono}.</p>
 */
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopProductController {

    private final ProductCatalog productCatalog;

    @GetMapping("/products/{id}")
    public Mono<ProductDto> getProduct(@PathVariable String id) {
        return productCatalog.getProduct(id);
    }
}
