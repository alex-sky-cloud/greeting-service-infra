package com.example.coldhotpublisher.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <p>Настраивает учебный стенд из {@code application.yml}: куда слушает приложение,
 * что отвечают заглушки и как быстро идёт сценарий в {@link com.example.coldhotpublisher.DemoRunner}.</p>
 * <p>Перечень ключей — {@code application.md}.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "demo")
public class DemoProperties {

    private Application application = new Application();
    private StubApi stubApi = new StubApi();
    private StubTiming stubTiming = new StubTiming();
    private StubData stubData = new StubData();
    private Cache cache = new Cache();
    private Runner runner = new Runner();

    /**
     * <p>Склеивает адрес, на котором WebClient находит заглушки в этом же процессе.</p>
     */
    public String stubBaseUrl() {
        return stubApi.baseUrl(application.getPort());
    }

    /**
     * <p>Задаёт HTTP-порт <em>только этого</em> модуля.</p>
     * <p>Один номер порта прописывается и в {@code server.port}, и в URL для WebClient —
     * иначе клиенты могли бы стучаться не туда или пересечься с {@code app} (8080)
     * и {@code reactive-demo} (8081).</p>
     */
    @Getter
    @Setter
    public static class Application {

        private int port = 8082;
    }

    /**
     * <p>Хост и схема для исходящих вызовов WebClient.</p>
     * <p>Порт подставляется из {@link Application}: заглушки и клиенты должны
     * смотреть на один Netty внутри JVM.</p>
     */
    @Getter
    @Setter
    public static class StubApi {

        private String scheme = "http";
        private String host = "localhost";

        public String baseUrl(int port) {
            return scheme + "://" + host + ":" + port;
        }
    }

    /**
     * <p>Растягивает ответы заглушек во времени.</p>
     * <p>Без пауз в логах трудно отличить «второй подписчик дождался первого запроса»
     * от «второй подписчик запустил свой запрос» — задержки делают хронологию читаемой.</p>
     */
    @Getter
    @Setter
    public static class StubTiming {

        private long productDelayMs = 300;
        private long fraudDelayMs = 400;
        private long tariffDelayMs = 300;
        private long statusElementDelayMs = 700;
        private long statusStepSeconds = 1;
        private long quoteIntervalMs = 500;
    }

    /**
     * <p>Что именно возвращают фиктивные REST/SSE-эндпоинты.</p>
     * <p>Меняется сценарий (цена, статусы заказа, длина потока котировок)
     * без правки Java-классов заглушек.</p>
     */
    @Getter
    @Setter
    public static class StubData {

        private String productNamePrefix = "Demo product ";
        private BigDecimal productPrice = new BigDecimal("99.90");
        private String fraudStatus = "ALLOW";
        private String fraudReason = "stub-approved";
        private String tariffVersion = "v1-local";
        private List<TariffRowData> tariffRows = defaultTariffRows();
        private List<String> orderStatuses = List.of("CREATED", "PAID", "PACKED", "SHIPPED");
        private BigDecimal quoteBaseBid = new BigDecimal("1.1000");
        private BigDecimal quoteBidStep = new BigDecimal("0.0001");
        private BigDecimal quoteAskSpread = new BigDecimal("0.0002");
        private int quoteMaxEvents = 20;
    }

    @Getter
    @Setter
    public static class TariffRowData {

        private String zone;
        private BigDecimal price;
    }

    /**
     * <p>Сколько минут {@code Mono.cache()} помнит тарифы после первой успешной загрузки.</p>
     * <p>Пока TTL не истёк, новые подписчики не инициируют повторный HTTP к {@code /tariffs}.</p>
     */
    @Getter
    @Setter
    public static class Cache {

        private long tariffTtlMinutes = 10;
    }

    /**
     * <p>Управляет синхронным сценарием в {@link com.example.coldhotpublisher.DemoRunner}.</p>
     * <p>Reactor исполняется асинхронно, а раннер — обычный Java-код с {@code Thread.sleep}.
     * Паузы нужны, чтобы: (1) дать потоку время выдать события до следующего шага;
     * (2) подключить «опоздавшего» подписчика уже после старта SSE — и увидеть разницу
     * между {@code share()} и {@code replay(1)}.</p>
     * <p>Разные id заказов и товаров разводят логи соседних прогонов.</p>
     */
    @Getter
    @Setter
    public static class Runner {

        private String productId = "p-100";
        private String fraudOrderId = "ord-500";
        private String sharedFluxOrderId = "ord-700";
        private String replayFluxOrderId = "ord-701";
        private String quoteSymbol = "EURUSD";
        private long coldMonoWaitMs = 1500;
        private long sharedMonoWaitMs = 1500;
        private long cachedMonoBetweenRequestsMs = 800;
        private long cachedMonoWaitMs = 1200;
        private long fluxLateSubscriberDelayMs = 2500;
        private long fluxWaitMs = 5000;
        private long refCountSecondSubscriberDelayMs = 1500;
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
