package com.example.profcourse.t02;

/**
 * Готовый класс, менять не нужно. Сводка по выводу {@code jstat -gcutil -t} между первой и последней строкой.
 *
 * @param seconds         сколько секунд прошло между первой и последней строкой
 * @param youngGcs        сколько молодых сборок прошло за это время
 * @param fullGcs         сколько полных сборок прошло за это время
 * @param gcTimePercent   какую долю этого времени заняли сборки, %
 * @param oldFirstPercent заполнение старого поколения в первой строке, %
 * @param oldLastPercent  заполнение старого поколения в последней строке, %
 */
public record GcUtilReport(
        double seconds,
        long youngGcs,
        long fullGcs,
        double gcTimePercent,
        double oldFirstPercent,
        double oldLastPercent) {
}
