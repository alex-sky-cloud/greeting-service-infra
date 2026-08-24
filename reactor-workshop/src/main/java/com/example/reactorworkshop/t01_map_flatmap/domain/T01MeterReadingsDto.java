package com.example.reactorworkshop.t01_map_flatmap.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Счётчик плюс kWh его показаний (inner Publisher в {@code flatMap}).
 */
public record T01MeterReadingsDto(Long meterId, String serialNo, List<BigDecimal> kwhValues) {
}
