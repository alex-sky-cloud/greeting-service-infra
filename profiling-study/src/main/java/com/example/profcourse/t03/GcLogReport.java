package com.example.profcourse.t03;

import java.util.List;

/**
 * Готовый класс, менять не нужно. Сводка по GC-логу {@code -Xlog:gc}.
 *
 * @param youngPauses      сколько пауз {@code Pause Young}
 * @param fullPauses       сколько пауз {@code Pause Full}
 * @param totalPauseMs     сумма всех пауз ({@code Pause Young}, {@code Pause Full}, {@code Pause Remark}, {@code Pause Cleanup}), мс
 * @param maxPauseMs       самая длинная пауза, мс
 * @param heapAfterFullMb  занято кучи после каждой полной сборки, МБ, в порядке сборок
 * @param leakSuspected    признак утечки по правилу из условия задачи
 */
public record GcLogReport(
        int youngPauses,
        int fullPauses,
        double totalPauseMs,
        double maxPauseMs,
        List<Long> heapAfterFullMb,
        boolean leakSuspected) {
}
