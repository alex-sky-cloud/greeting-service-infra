package com.example.coldhotpublisher.dto;

import java.util.List;

/** Тарифная таблица с версией. */
public record TariffTable(
    String version,
    List<TariffRow> rows
) {}
