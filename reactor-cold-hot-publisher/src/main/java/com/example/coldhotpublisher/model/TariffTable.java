package com.example.coldhotpublisher.model;

import java.util.List;

/** Версионированный справочник тарифов доставки. */
public record TariffTable(
    String version,
    List<TariffRow> rows
) {}
