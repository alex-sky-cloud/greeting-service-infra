package com.example.coldhotpublisher.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <p>Настройки учебного интернет-магазина из {@code application.yml}.</p>
 * <p>Задаёт порт, тестовые товары и тарифы и задержки ответов «внешних» систем.</p>
 * <p>Перечень ключей — {@code application.md}.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "demo")
public class DemoProperties {

    private Application application = new Application();
    private StubTiming stubTiming = new StubTiming();
    private StubData stubData = new StubData();
    private Cache cache = new Cache();

    /**
     * <p>HTTP-порт этого модуля (по умолчанию 8082).</p>
     * <p>Не пересекается с {@code app} (8080) и {@code reactive-demo} (8081).</p>
     */
    @Getter
    @Setter
    public static class Application {

        private int port = 8082;
    }

    /**
     * <p>Искусственные задержки ответов внешних систем.</p>
     * <p>Делают в логах видимой разницу между «повторным запросом» и «ожиданием того же результата».</p>
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
     * <p>Тестовые данные магазина: цены, вердикты anti-fraud, этапы заказа, котировки.</p>
     * <p>Меняются через конфиг без правки кода заглушек.</p>
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

    /** Строка тарифной таблицы в конфиге: зона доставки и цена. */
    @Getter
    @Setter
    public static class TariffRowData {

        private String zone;
        private BigDecimal price;
    }

    /**
     * <p>Сколько минут приложение помнит загруженный справочник тарифов.</p>
     * <p>Пока срок не истёк, повторные обращения к тарифам не ходят во внешнюю службу.</p>
     */
    @Getter
    @Setter
    public static class Cache {

        private long tariffTtlMinutes = 10;
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
