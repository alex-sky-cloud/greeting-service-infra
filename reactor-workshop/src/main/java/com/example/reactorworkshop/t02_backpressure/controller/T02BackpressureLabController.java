package com.example.reactorworkshop.t02_backpressure.controller;

import com.example.reactorworkshop.t02_backpressure.domain.T02PacedId;
import com.example.reactorworkshop.t02_backpressure.domain.T02ReadingEntity;
import com.example.reactorworkshop.t02_backpressure.service.T02BackpressureLabService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * HTTP лабы 2.1: два механизма, которые нельзя путать по одному слову «limit».
 * <ul>
 *   <li>{@code GET /api/t02/readings-limit-rate} — JVM backpressure: {@code limitRate} / {@code request(n)} /
 *       prefetch. SQL без {@code LIMIT}. HTTP стримит все ~100 000 строк пачками demand по 50.</li>
 *   <li>{@code GET /api/t02/readings-sql-page} — SQL-пагинация: {@code LIMIT 5 OFFSET 0}.
 *       В теле ответа ровно 5 строк, затем конец. Это не prefetch.</li>
 *   <li>{@code GET /api/t02/paced-ids} — синтетика без БД: {@code limitRate} не drop.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/t02")
public class T02BackpressureLabController {

    private static final int DEFAULT_ID_COUNT = 20;
    private static final int DEFAULT_IDS_PER_REQUEST = 5;

    private final T02BackpressureLabService service;

    /**
     * @param service лаба: {@code limitRate} на {@code findAll} и native {@code LIMIT}/{@code OFFSET}
     */
    public T02BackpressureLabController(T02BackpressureLabService service) {
        this.service = service;
    }


    /**
     * Сюжет 1: выгрузка всей таблицы {@code readings} (~100 000 строк) с backpressure в Reactor, не в SQL.
     *
     * <p>Подписчик здесь — WebFlux (HTTP-клиент читает тело ответа). По Reactive Streams он говорит
     * источнику {@code request(N)}: «дай следующие N элементов». Это и есть demand. Спрос идёт сверху вниз:
     * клиент → WebFlux → {@code limitRate} → {@code Flux} из {@code findAll()}.</p>
     *
     * <p>{@code limitRate(50)} живёт только в JVM. Он режет {@code request(N)}: вместо «дай сразу все 100 000»
     * источник слышит «дай 50», потом ещё 50, пока таблица не кончится. Все строки всё равно дойдут,
     * если клиент читает до конца. Это не «верни 50 и остановись» и не SQL {@code LIMIT 50}.</p>
     *
     * <p>В логе QUERY будет один {@code SELECT ... FROM readings} <b>без</b> {@code LIMIT}.
     * Один statement на всю выгрузку — так и задумано: несколько запросов с разными OFFSET были бы
     * медленнее и видели бы разный снимок данных.</p>
     *
     * <p>Portal / cursor — это уже протокол Postgres, не Reactor. Они появляются, только если у statement
     * включён {@code fetchSize > 0}: драйвер открывает на сервере именованный результат (portal),
     * просит пачку строк сообщением {@code Execute}, сервер отвечает {@code PortalSuspended}, драйвер
     * при новом спросе шлёт ещё {@code Execute} на тот же portal. По умолчанию у r2dbc-postgresql
     * {@code fetchSize = 0}, cursor-режим не обязан включаться. Тогда медленный клиент тормозит поток
     * через TCP: сокет не вычитывают — сервер перестаёт слать.</p>
     *
     * <p>Итого: 50 в этом методе — размер demand в Reactor. Это не размер Fetch драйвера и не
     * {@code LIMIT} в SQL. Cancel HTTP закрывает {@code Flux} и дочитывание останавливается.</p>
     *
     * @return поток всех показаний, пока клиент не отменил запрос
     */
    @GetMapping("/readings-limit-rate")
    public Flux<T02ReadingEntity> readingsLimitRate() {
        return service.exportReadingsLimited();
    }




    /**
     * Сюжет 2: одна страница из Postgres через SQL {@code LIMIT}/{@code OFFSET}.
     *
     * <p>Это не Reactive Streams и не способ выгрузить всю таблицу. Реактивный {@code Flux}
     * только доставляет уже обрезанный SQL-результат. Полная таблица — сюжет 1
     * ({@code findAll} + {@code limitRate}).</p>
     *
     * <p>Запрос демо:</p>
     * <pre>{@code
     * SELECT id, meter_id, kwh, recorded_at
     * FROM reactor_workshop.readings
     * ORDER BY id
     * LIMIT 5 OFFSET 0
     * }</pre>
     *
     * <ul>
     *   <li>{@code ORDER BY id} — страница стабильна. Без сортировки Postgres может отдать любые 5 строк.</li>
     *   <li>{@code LIMIT 5} — сервер возвращает не больше 5 строк и заканчивает запрос.
     *       JVM не видит остальные ~100 000. HTTP-тело: 5 JSON, затем конец.</li>
     *   <li>{@code OFFSET 0} — сколько строк пропустить. 0 = первая страница (ids 1..5, если id с 1).
     *       Страница 2 была бы {@code OFFSET 5 LIMIT 5}.</li>
     * </ul>
     *
     * <p>OFFSET называет страницу номером: «пропусти первые N, отдай следующие LIMIT».
     * На большой таблице это дорого. Запрос {@code LIMIT 50 OFFSET 50000} не прыгает к строке 50001.
     * Postgres идёт с начала {@code ORDER BY id}, отбрасывает 50 000 строк и только потом отдаёт 50.
     * Страница 1 ({@code OFFSET 0}) дешёвая, страница 2000 уже тяжёлая.</p>
     *
     * <p>Для «следующая пачка» без номера страницы лучше keyset. Клиент помнит последний {@code id}
     * прошлой пачки (например 50) и просит строки после него:</p>
     * <pre>{@code
     * SELECT id, kwh
     * FROM reactor_workshop.readings
     * WHERE id > 50
     * ORDER BY id
     * LIMIT 50
     * }</pre>
     *
     * <p>Это поиск в индексе после 50, а не пропуск 50 строк. Стоимость почти одинаковая
     * после {@code id = 50} и после {@code id = 90 000}.</p>
     *
     * <p>OFFSET оставляют, когда UI рисует кнопки «страница 1, 2, 3 … 17»: нужен номер
     * {@code OFFSET (page - 1) * size}. У keyset нет «страницы 17», только «дай после этого id».
     * Лента, скролл, выгрузка пачками — keyset. Небольшой админский список — OFFSET нормален.
     * В этой лабе {@code OFFSET 0 LIMIT 5} — просто первая страница, чтобы в QUERY-логе был виден {@code LIMIT}.
     * Keyset здесь не используем.</p>
     *
     * @return ровно одна SQL-страница: 5 показаний, затем {@code onComplete}
     */
    @GetMapping("/readings-sql-page")
    public Flux<T02ReadingEntity> readingsSqlPage() {
        return service.exportReadingsSqlPage();
    }

    /**
     * Синтетический поток без обращения к БД.
     * {@code count} id при любом {@code rate} все доходят: {@code limitRate} только размер {@code request(n)}.
     *
     * @param count сколько id (по умолчанию {@link #DEFAULT_ID_COUNT})
     * @param rate  пачка {@code limitRate}, не SQL {@code LIMIT}
     * @return все id 1..count с пометкой, что drop нет
     */
    @GetMapping("/paced-ids")
    public Flux<T02PacedId> pacedIds(
            @RequestParam(defaultValue = "" + DEFAULT_ID_COUNT) int count,
            @RequestParam(defaultValue = "" + DEFAULT_IDS_PER_REQUEST) int rate
    ) {
        return service.pacedIds(count, rate);
    }
}
