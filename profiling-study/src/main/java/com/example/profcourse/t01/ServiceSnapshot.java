package com.example.profcourse.t01;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Снимок метрик сервиса за окно инцидента — то, что дежурный видит на дашборде до запуска профайлера.
 *
 * @param p99Ms                   время ответа сервиса, 99-й перцентиль, мс
 * @param baselineP99Ms           то же в обычный день, мс
 * @param jvmCpuPercent           CPU, который тратит процесс JVM, % от всех ядер машины
 * @param hostCpuPercent          CPU всей машины, %
 * @param gcPausePercent          доля времени, когда приложение стояло в паузах GC, %
 * @param dbPoolActive            занято соединений с БД
 * @param dbPoolMax               размер пула соединений
 * @param dbPoolWaiting           потоков ждут свободное соединение
 * @param dependencyP99Ms         время ответа внешних систем (БД, HTTP-сервисы) по метрикам клиента, p99, мс
 * @param dependencyBaselineP99Ms то же в обычный день, мс
 */
public record ServiceSnapshot(
        long p99Ms,
        long baselineP99Ms,
        int jvmCpuPercent,
        int hostCpuPercent,
        int gcPausePercent,
        int dbPoolActive,
        int dbPoolMax,
        int dbPoolWaiting,
        long dependencyP99Ms,
        long dependencyBaselineP99Ms) {
}
