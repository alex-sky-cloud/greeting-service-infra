package com.example.coldhotpublisher.service.tariff;

import com.example.coldhotpublisher.model.TariffTable;
import reactor.core.publisher.Mono;

/** Справочник тарифов доставки: актуальная таблица цен по зонам. */
public interface TariffDirectory {

    Mono<TariffTable> getTariffs();
}
