package com.example.coldhotpublisher.controller.shop;

import com.example.coldhotpublisher.model.TariffTable;
import com.example.coldhotpublisher.service.tariff.TariffDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * <p>HTTP API магазина: тарифы доставки.</p>
 * <p>Повторные GET от клиента демонстрируют {@code cache()} — один поход к внешнему справочнику.</p>
 */
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopTariffController {

    private final TariffDirectory tariffDirectory;

    @GetMapping("/tariffs")
    public Mono<TariffTable> getTariffs() {
        return tariffDirectory.getTariffs();
    }
}
