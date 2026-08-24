package com.example.reactorworkshop.t02_backpressure.service;

import com.example.reactorworkshop.t02_backpressure.domain.T02PacedId;
import com.example.reactorworkshop.t02_backpressure.domain.T02ReadingEntity;
import com.example.reactorworkshop.t02_backpressure.repository.T02ReadingRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Лаба 2.1: две разные «пачки» на таблице {@code readings} (~100 000 строк; думайте как о миллионе).
 * HTTP/WebFlux-подписчик не может проглотить всю таблицу сразу. Drop нам не нужен: продюсер должен
 * замедляться. Это делается двумя независимыми механизмами — их легко перепутать по логу QUERY.
 * <ul>
 *   <li>{@code limitRate} / {@code request(n)} / prefetch — backpressure <b>в JVM</b>. SQL не меняется.
 *       Строки в итоге все доходят (пока клиент не отменил запрос).</li>
 *   <li>{@code LIMIT}/{@code OFFSET} в native SQL — «backpressure» <b>в базе</b>. Запрос другой.
 *       В результате есть только эта страница, остаток 100k JVM не видит.</li>
 * </ul>
 *
 * <p><b>Сюжет 1.</b> {@link #exportReadingsLimited()} → {@code GET /api/t02/readings-limit-rate}.
 * Reactive Streams: subscriber говорит {@code request(n)}. У Reactor prefetch по умолчанию часто 256.
 * {@code limitRate(READINGS_PER_REQUEST)} при {@code READINGS_PER_REQUEST = 50} — это prefetch / high-tide:
 * вниз по цепочке всё равно уйдут <b>все</b> строки, но спрос режется на {@code request(50)}, потом ещё
 * {@code request(50)}, … пока таблица не кончится. Это <b>не</b> «вернуть 50 строк и остановиться».
 * HTTP-ответ стримит все ~100k. Число 50 — размер demand, не SQL {@code LIMIT}.</p>
 *
 * <p>Что делает PostgreSQL R2DBC-драйвер в этот момент:</p>
 * <ul>
 *   <li>Открывает <b>один</b> statement: {@code SELECT ... FROM readings} <b>без</b> {@code LIMIT} в SQL
 *       (это уже видно в логе QUERY).</li>
 *   <li>Portal/cursor на сервере остаётся открытым. Когда {@code Flux} запрашивает 50, драйвер Fetch-ит
 *       следующие 50 строк из того же ResultSet, мапит и эмитит. Он <b>не</b> шлёт новый SQL
 *       {@code LIMIT 50 OFFSET x}.</li>
 *   <li>Если подписчик медленный, следующий {@code request(50)} откладывается → драйвер не fetch-ит
 *       следующую пачку → Postgres не заталкивает миллион строк в heap JVM разом.</li>
 *   <li>Если HTTP-клиент отменить, {@code Flux} cancel, cursor закрывается, хвост таблицы не грузится.</li>
 * </ul>
 * <p>Почему в логе Time:~23s и Query без {@code LIMIT}: метод попросил у Postgres всю таблицу;
 * {@code limitRate} только задаёт темп, с которым строки входят в JVM. Сюжет 2 — наоборот.</p>
 *
 * <p><b>Сюжет 2.</b> {@link #exportReadingsSqlPage()} / {@code findPage} → {@code GET /api/t02/readings-sql-page}.
 * Студент смотрит QUERY после {@code limitRate} и не видит {@code LIMIT}. Чтобы пачка была <b>на стороне БД</b>,
 * native SQL пишем сами. Postgres обрезает ResultSet: демо отдаёт ровно {@link #SQL_LIMIT_ROWS} строк
 * ({@code = 5}) и {@code onComplete}. Это не prefetch и не 100k.</p>
 */
@Service
public class T02BackpressureLabService {

    /**
     * Prefetch / high-tide для {@code limitRate}: сколько элементов WebFlux запрашивает у {@code findAll}
     * одним {@code request(n)}. Все строки таблицы всё равно доходят; SQL {@code LIMIT} из этого числа не появляется.
     */
    static final int READINGS_PER_REQUEST = 50;

    /**
     * Сколько строк Postgres реально возвращает в демо SQL-пагинации ({@code LIMIT}).
     * Маленькое значение, чтобы лог QUERY был коротким и очевидным.
     */
    static final int SQL_LIMIT_ROWS = 5;

    /**
     * Сколько строк пропустить до {@code LIMIT} ({@code OFFSET}).
     * {@code 0} — первая страница (при {@code ORDER BY id} это ids 1..5, если счётчик начинается с 1).
     * Обход всех страниц потребовал бы {@code collectList}/{@code flatMapMany}/{@code concatWith} — темы позже.
     */
    static final long SQL_FIRST_OFFSET = 0L;

    /** Нижняя граница: поток не должен эмитить отрицательное число элементов. */
    private static final int EMPTY_COUNT = 0;

    /** Минимальный размер пачки {@code limitRate}, если клиент прислал 0. */
    private static final int MIN_IDS_PER_REQUEST = 1;

    /** Первое число в синтетическом {@code Flux.range}. */
    private static final int FIRST_SYNTHETIC_ID = 1;

    private final T02ReadingRepository readingRepository;

    /**
     * @param readingRepository R2DBC-репозиторий показаний ({@code findAll} и native {@code findPage})
     */
    public T02BackpressureLabService(T02ReadingRepository readingRepository) {
        this.readingRepository = readingRepository;
    }

    /**
     * Все показания с backpressure <b>в JVM</b> ({@code limitRate} / {@code request(n)} / prefetch).
     * В логе QUERY будет <b>один</b> {@code SELECT} без {@code LIMIT}: драйвер держит cursor и Fetch-ит
     * пачками по {@link #READINGS_PER_REQUEST}, когда downstream говорит {@code request(50)}.
     * HTTP-ответ стримит все ~100 000 строк; 50 — не «стоп после пятидесяти», а размер demand.
     * <pre>{@code
     * readingRepository.findAll().limitRate(readingsPerRequest);
     * }</pre>
     *
     * @return поток всех строк {@code readings} (пока клиент не cancel)
     */
    public Flux<T02ReadingEntity> exportReadingsLimited() {
        int readingsPerRequest = READINGS_PER_REQUEST;

        return readingRepository.findAll()
                .limitRate(readingsPerRequest); // prefetch 50: режет request(n) к ResultSet; все элементы дойдут; SQL LIMIT нет
    }

    /**
     * Одна страница показаний SQL-пагинацией — это <b>не</b> Reactive Streams backpressure.
     * Запрос сам режет ResultSet на стороне Postgres: {@code LIMIT} = максимум строк, потом STOP;
     * {@code OFFSET} = сколько строк пропустить (страница = {@code pageIndex * pageSize}).
     * Без {@code ORDER BY} {@code OFFSET} бессмысленен (любые 5 строк). Большой {@code OFFSET} дорогой:
     * Postgres всё равно проходит пропущенные строки — оставляем его, потому что именно его видно в логе
     * и потому что это классический ответ на «как постраничить в SQL».
     * Без цепочки {@code collectList}/{@code flatMapMany} — это операторы следующих тем.
     * <pre>{@code
     * readingRepository.findPage(sqlLimitRows, firstOffset);
     * }</pre>
     *
     * @return ровно {@link #SQL_LIMIT_ROWS} первых показаний по {@code id}, затем {@code onComplete}
     */
    public Flux<T02ReadingEntity> exportReadingsSqlPage() {
        int sqlLimitRows = SQL_LIMIT_ROWS;
        long firstOffset = SQL_FIRST_OFFSET;

        return readingRepository.findPage(sqlLimitRows, firstOffset); // один SELECT ... LIMIT 5 OFFSET 0: БД режет ResultSet, не prefetch
    }

    /**
     * Синтетический {@code Flux.range} без БД: видно, что {@code limitRate} не drop.
     * Десять id при rate 3 всё равно эмитят 1..10; пачка только размер {@code request(n)}.
     *
     * @param count сколько чисел эмитить
     * @param rate  размер пачки {@code limitRate} (prefetch), не SQL {@code LIMIT}
     * @return поток DTO с пояснением, что элементы не отбрасываются
     */
    public Flux<T02PacedId> pacedIds(int count, int rate) {
        int emptyCount = EMPTY_COUNT;
        int minIdsPerRequest = MIN_IDS_PER_REQUEST;
        int firstSyntheticId = FIRST_SYNTHETIC_ID;
        int totalIds = Math.max(count, emptyCount);
        int idsPerRequest = Math.max(rate, minIdsPerRequest);

        return Flux.range(firstSyntheticId, totalIds)
                .limitRate(idsPerRequest) // пачки спроса request(n); все id до onComplete, drop нет
                .map(id -> new T02PacedId(id, "limitRate, не drop")); // T -> DTO, без Publisher
    }
}
