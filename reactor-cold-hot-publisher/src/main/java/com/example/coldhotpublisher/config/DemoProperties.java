package com.example.coldhotpublisher.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Типизированная конфигурация демо-модуля cold/hot Publisher.
 *
 * <p>Префикс в {@code application.yml}: {@code demo.*}. Описание всех ключей —
 * {@code src/main/resources/application.md}.</p>
 *
 * <p>Потребители:</p>
 * <ul>
 *   <li>{@link com.example.coldhotpublisher.infra.WebClientConfig} — {@link #stubBaseUrl()};</li>
 *   <li>{@link com.example.coldhotpublisher.stub.DemoStubController} — {@link #stubTiming}, {@link #stubData};</li>
 *   <li>{@link com.example.coldhotpublisher.tariff.TariffDirectoryClient} — {@link #cache};</li>
 *   <li>{@link com.example.coldhotpublisher.DemoRunner} — {@link #runner}.</li>
 * </ul>
 *
 * @see DemoPropertiesConfig
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "demo")
public class DemoProperties {

    /**
     * HTTP-порт этого модуля. Единственный источник порта: {@code demo.application.port}.
     *
     * <p>Также задаёт {@code server.port} через {@code ${demo.application.port}} в YAML.
     * Порты соседних модулей: {@code app}=8080, {@code reactive-demo}=8081,
     * этот модуль по умолчанию 8082.</p>
     */
    private Application application = new Application();

    /**
     * Схема и хост локальных заглушек. Порт подставляется из {@link #application}.
     *
     * @see com.example.coldhotpublisher.infra.WebClientConfig
     */
    private StubApi stubApi = new StubApi();

    /**
     * Искусственные задержки ответов заглушек (мс/с).
     *
     * @see com.example.coldhotpublisher.stub.DemoStubController
     */
    private StubTiming stubTiming = new StubTiming();

    /**
     * Тестовые данные, возвращаемые заглушками REST API.
     *
     * @see com.example.coldhotpublisher.stub.DemoStubController
     */
    private StubData stubData = new StubData();

    /**
     * Параметры кэширования reactive-источников.
     *
     * @see com.example.coldhotpublisher.tariff.TariffDirectoryClient
     */
    private Cache cache = new Cache();

    /**
     * Идентификаторы и таймауты сценариев {@link com.example.coldhotpublisher.DemoRunner}.
     */
    private Runner runner = new Runner();

    /**
     * Собирает {@code baseUrl} для всех {@link org.springframework.web.reactive.function.client.WebClient}:
     * {@code {stubApi.scheme}://{stubApi.host}:{application.port}}.
     *
     * @return URL локальных заглушек в этом же процессе
     * @see com.example.coldhotpublisher.infra.WebClientConfig
     */
    public String stubBaseUrl() {
        return stubApi.baseUrl(application.getPort());
    }

    /**
     * Параметры HTTP-сервера модуля.
     *
     * <p>YAML: {@code demo.application.port}. Связан с {@code server.port}.</p>
     */
    @Getter
    @Setter
    public static class Application {

        /** Порт Netty; по умолчанию 8082, чтобы не пересечься с app (8080) и reactive-demo (8081). */
        private int port = 8082;
    }

    /**
     * Хост и схема stub API. Порт передаётся отдельно из {@link Application#getPort()}.
     *
     * @see com.example.coldhotpublisher.infra.WebClientConfig
     */
    @Getter
    @Setter
    public static class StubApi {

        /** Схема URL, YAML: {@code demo.stub-api.scheme}. */
        private String scheme = "http";

        /** Хост заглушек, YAML: {@code demo.stub-api.host}. */
        private String host = "localhost";

        /**
         * @param port HTTP-порт приложения ({@link Application#port})
         * @return полный base URL для WebClient
         */
        public String baseUrl(int port) {
            return scheme + "://" + host + ":" + port;
        }
    }

    /**
     * Задержки stub-эндпоинтов. YAML: {@code demo.stub-timing.*}.
     *
     * @see com.example.coldhotpublisher.stub.DemoStubController
     */
    @Getter
    @Setter
    public static class StubTiming {

        /** {@code GET /products/{id}} — {@link com.example.coldhotpublisher.stub.DemoStubController#getProduct}. */
        private long productDelayMs = 300;

        /** {@code POST /fraud/check} — {@link com.example.coldhotpublisher.stub.DemoStubController#checkFraud}. */
        private long fraudDelayMs = 400;

        /** {@code GET /tariffs} — {@link com.example.coldhotpublisher.stub.DemoStubController#getTariffs}. */
        private long tariffDelayMs = 300;

        /** Пауза между SSE-событиями статусов — {@code streamStatuses}. */
        private long statusElementDelayMs = 700;

        /** Шаг {@code createdAt} между статусами в потоке — {@code streamStatuses}. */
        private long statusStepSeconds = 1;

        /** Интервал тиков котировок — {@code streamQuotes}. */
        private long quoteIntervalMs = 500;
    }

    /**
     * Данные заглушек. YAML: {@code demo.stub-data.*}.
     *
     * @see com.example.coldhotpublisher.stub.DemoStubController
     */
    @Getter
    @Setter
    public static class StubData {

        /** Имя товара: префикс + id — {@code getProduct}. */
        private String productNamePrefix = "Demo product ";

        /** Цена в ответе каталога — {@code getProduct}. */
        private BigDecimal productPrice = new BigDecimal("99.90");

        /** Статус anti-fraud — {@code checkFraud}. */
        private String fraudStatus = "ALLOW";

        /** Текст причины fraud — {@code checkFraud}. */
        private String fraudReason = "stub-approved";

        /** Версия тарифной таблицы — {@code getTariffs}. */
        private String tariffVersion = "v1-local";

        /** Строки тарифов — {@code getTariffs}. */
        private List<TariffRowData> tariffRows = defaultTariffRows();

        /** Последовательность статусов заказа в SSE — {@code streamStatuses}. */
        private List<String> orderStatuses = List.of("CREATED", "PAID", "PACKED", "SHIPPED");

        /** Начальный bid котировки — {@code streamQuotes}. */
        private BigDecimal quoteBaseBid = new BigDecimal("1.1000");

        /** Приращение bid на тик — {@code streamQuotes}. */
        private BigDecimal quoteBidStep = new BigDecimal("0.0001");

        /** Спред ask − bid — {@code streamQuotes}. */
        private BigDecimal quoteAskSpread = new BigDecimal("0.0002");

        /** Число событий в потоке котировок — {@code streamQuotes}. */
        private int quoteMaxEvents = 20;
    }

    /** Одна строка тарифа ({@code zone}, {@code price}) в {@link StubData#tariffRows}. */
    @Getter
    @Setter
    public static class TariffRowData {

        private String zone;
        private BigDecimal price;
    }

    /**
     * TTL и прочие настройки cache/replay.
     *
     * @see com.example.coldhotpublisher.tariff.TariffDirectoryClient#getTariffs()
     */
    @Getter
    @Setter
    public static class Cache {

        /**
         * Время жизни {@code Mono.cache()} для тарифов (минуты).
         * YAML: {@code demo.cache.tariff-ttl-minutes}.
         */
        private long tariffTtlMinutes = 10;
    }

    /**
     * Параметры автозапуска демо-сценариев (profile {@code demo}).
     *
     * @see com.example.coldhotpublisher.DemoRunner
     */
    @Getter
    @Setter
    public static class Runner {

        /** Id товара для cold {@code Mono} — {@link com.example.coldhotpublisher.DemoRunner#coldMono}. */
        private String productId = "p-100";

        /** Id заказа для {@code Mono.share()} — {@link com.example.coldhotpublisher.DemoRunner#sharedMono}. */
        private String fraudOrderId = "ord-500";

        /** Id заказа для {@code Flux.share()} — {@link com.example.coldhotpublisher.DemoRunner#sharedFlux}. */
        private String sharedFluxOrderId = "ord-700";

        /** Id заказа для {@code replay(1)} — {@link com.example.coldhotpublisher.DemoRunner#replayFlux}. */
        private String replayFluxOrderId = "ord-701";

        /** Символ котировок для {@code refCount(2)} — {@link com.example.coldhotpublisher.DemoRunner#refCountFlux}. */
        private String quoteSymbol = "EURUSD";

        /** Пауза после cold mono. */
        private long coldMonoWaitMs = 1500;

        /** Пауза после shared mono. */
        private long sharedMonoWaitMs = 1500;

        /** Пауза между двумя подписчиками cached mono. */
        private long cachedMonoBetweenRequestsMs = 800;

        /** Пауза в конце сценария cached mono. */
        private long cachedMonoWaitMs = 1200;

        /** Задержка «позднего» подписчика в flux-сценариях (share / replay). */
        private long fluxLateSubscriberDelayMs = 2500;

        /** Ожидание завершения flux-сценария. */
        private long fluxWaitMs = 5000;

        /** Задержка перед вторым подписчиком refCount. */
        private long refCountSecondSubscriberDelayMs = 1500;

        /** Ожидание завершения refCount-сценария. */
        private long refCountWaitMs = 5000;
    }

    private static List<TariffRowData> defaultTariffRows() {
        List<TariffRowData> rows = new ArrayList<>();
        rows.add(tariffRow("BY", "10.50"));
        rows.add(tariffRow("PL", "14.90"));
        rows.add(tariffRow("DE", "19.00"));
        return rows;
    }

    private static TariffRowData tariffRow(String zone, String price) {
        TariffRowData row = new TariffRowData();
        row.setZone(zone);
        row.setPrice(new BigDecimal(price));
        return row;
    }
}
