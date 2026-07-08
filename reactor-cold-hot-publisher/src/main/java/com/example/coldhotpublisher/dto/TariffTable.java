package com.example.coldhotpublisher.dto;

import java.util.List;

public record TariffTable(
    String version,
    List<TariffRow> rows
) {}
