package com.example.profcourse.lab;

import com.example.profcourse.common.Slow;
import com.example.profcourse.t03.LegacySessionRegistry;
import com.example.profcourse.t04.Account;
import com.example.profcourse.t04.ConnectionPool;
import com.example.profcourse.t04.LegacyTransfers;
import com.example.profcourse.t04.OrderController;
import com.example.profcourse.t06.LegacyReconciler;
import com.example.profcourse.t06.LegacyReportBuilder;
import com.example.profcourse.t06.Order;
import com.example.profcourse.t06.Payment;
import com.example.profcourse.t06.Reconciliation;
import com.example.profcourse.t06.StatementLine;
import com.example.profcourse.t07.LegacyCatalog;
import com.example.profcourse.t07.PriceList;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Лаборатория: учебный сервис, который воспроизводит одну проблему производительности.
 * К нему подключаются {@code jcmd}, {@code jstat}, {@code jfr}, VisualVM и JMC.
 * <p>
 * Запуск: {@code java -cp build/classes/java/main com.example.profcourse.lab.LabApp <сценарий>}.
 */
public final class LabApp {

    private static final Map<String, String> SCENARIOS = Map.of(
            "leak", "реестр сессий: память растёт, пока не кончится",
            "churn", "много короткоживущих объектов, утечки нет",
            "pool", "оформление заказов ждёт соединения с БД",
            "deadlock", "переводы между счетами зависли",
            "audit", "журнал аудита: потоки стоят в очереди на запись",
            "report", "выгрузка для бухгалтерии съедает CPU",
            "reconcile", "ночная сверка с банком идёт часами",
            "catalog", "каталог: GC каждые несколько миллисекунд");

    private LabApp() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !SCENARIOS.containsKey(args[0])) {
            System.out.println("Сценарии:");
            SCENARIOS.forEach((name, about) -> System.out.printf("  %-10s %s%n", name, about));
            return;
        }
        System.out.printf("Лаборатория '%s': %s. PID %d. Остановить — Ctrl+C.%n",
                args[0], SCENARIOS.get(args[0]), ProcessHandle.current().pid());
        switch (args[0]) {
            case "leak" -> leak();
            case "churn" -> churn();
            case "pool" -> pool();
            case "deadlock" -> deadlock();
            case "audit" -> audit();
            case "report" -> report();
            case "reconcile" -> reconcile();
            case "catalog" -> catalog();
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    /** 1000 входов в секунду; каждый вход сдвигает часы на секунду: через 30 «минут» сессии начинают истекать. */
    private static void leak() {
        AtomicLong logins = new AtomicLong();
        Instant start = Instant.parse("2026-10-20T09:00:00Z");
        Clock fast = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return start.plusSeconds(logins.get());
            }
        };
        LegacySessionRegistry registry = new LegacySessionRegistry(fast);
        long reported = System.nanoTime();
        while (true) {
            String id = registry.login("user-" + logins.incrementAndGet() % 50_000);
            registry.touch(id);
            requestGarbage(16 * 1024);
            Slow.sleep(1);
            if (System.nanoTime() - reported > 5_000_000_000L) {
                reported = System.nanoTime();
                System.out.printf("входов: %d, сессий в реестре: %d, действующих по таймауту: %d%n",
                        logins.get(), registry.size(), Math.min(logins.get(), 1800));
            }
        }
    }

    private static void churn() {
        long requests = 0;
        while (true) {
            requestGarbage(64 * 1024);
            if (++requests % 200 == 0) {
                Slow.sleep(1);
            }
        }
    }

    /** 20 потоков-обработчиков, пул на 4 соединения. */
    private static void pool() throws InterruptedException {
        OrderController controller = new OrderController(new ConnectionPool(4));
        for (int i = 1; i <= 20; i++) {
            int worker = i;
            Thread.ofPlatform().name("http-nio-8080-exec-" + i).start(() -> {
                long n = 0;
                while (true) {
                    controller.placeOrder("ORD-" + worker + "-" + n++);
                }
            });
        }
        Thread.currentThread().join();
    }

    private static void deadlock() throws InterruptedException {
        Account a = new Account("40817-001", 1_000_000);
        Account b = new Account("40817-002", 1_000_000);
        LegacyTransfers transfers = new LegacyTransfers();
        Thread.ofPlatform().name("transfer-1").start(() -> {
            while (true) {
                transfers.transfer(a, b, 100);
            }
        });
        Thread.ofPlatform().name("transfer-2").start(() -> {
            while (true) {
                transfers.transfer(b, a, 100);
            }
        });
        Thread.currentThread().join();
    }

    /** 8 обработчиков пишут в один журнал; запись держит монитор 5 мс. */
    private static void audit() throws InterruptedException {
        AuditLog log = new AuditLog();
        for (int i = 1; i <= 8; i++) {
            int worker = i;
            Thread.ofPlatform().name("http-nio-8080-exec-" + i).start(() -> {
                long n = 0;
                while (true) {
                    log.write("worker " + worker + " request " + n++);
                }
            });
        }
        Thread.currentThread().join();
    }

    private static void report() {
        List<Order> orders = Order.sample(8_000);
        LegacyReportBuilder builder = new LegacyReportBuilder();
        while (true) {
            long start = System.nanoTime();
            int length = builder.build(orders).length();
            System.out.printf("выгрузка: %d заказов, %d символов, %d мс%n",
                    orders.size(), length, (System.nanoTime() - start) / 1_000_000);
        }
    }

    private static void reconcile() {
        List<Payment> payments = Reconciliation.payments(30_000);
        List<StatementLine> statement = Reconciliation.statement(payments);
        LegacyReconciler reconciler = new LegacyReconciler();
        while (true) {
            long start = System.nanoTime();
            int unmatched = reconciler.unmatched(payments, statement).size();
            System.out.printf("сверка: %d платежей, расхождений %d, %d мс%n",
                    payments.size(), unmatched, (System.nanoTime() - start) / 1_000_000);
        }
    }

    private static void catalog() {
        LegacyCatalog catalog = new LegacyCatalog(new PriceList());
        long requests = 0;
        long start = System.nanoTime();
        while (true) {
            catalog.priceFor(String.format("SKU-%05d", requests % PriceList.SIZE + 1), 7);
            if (++requests % 500 == 0) {
                System.out.printf("цен рассчитано: %d, %.0f в секунду%n",
                        requests, requests / ((System.nanoTime() - start) / 1e9));
            }
        }
    }

    /** Временные объекты обработки запроса: живут миллисекунды. */
    private static void requestGarbage(int bytes) {
        byte[][] chunks = new byte[bytes / 1024][];
        for (int i = 0; i < chunks.length; i++) {
            chunks[i] = new byte[1024];
        }
        if (chunks[0].length == 0) {
            System.out.println("никогда");
        }
    }
}
