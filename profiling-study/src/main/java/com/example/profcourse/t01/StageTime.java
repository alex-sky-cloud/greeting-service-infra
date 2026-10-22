package com.example.profcourse.t01;

import java.time.Duration;

/**
 * Готовый класс, менять не нужно.
 *
 * @param stage        имя шага, как в {@link CheckoutPipeline}
 * @param took         сколько шаг занял
 * @param sharePercent доля шага в общем времени, целые проценты
 */
public record StageTime(String stage, Duration took, int sharePercent) {
}
