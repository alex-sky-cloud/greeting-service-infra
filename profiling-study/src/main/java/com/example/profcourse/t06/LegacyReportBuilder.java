package com.example.profcourse.t06;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Выгрузка заказов для бухгалтерии в том виде, в каком она ушла в продакшен. Строка на заказ:
 * {@code ORD-000001;01.10.2026 09:01;************7919;100.37}. Лаборатория {@code report} запускает именно её.
 */
public class LegacyReportBuilder {

    public String build(List<Order> orders) {
        String report = "";
        for (Order order : orders) {
            String date = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").format(order.createdAt());
            String card = order.cardNumber().replaceAll("\\d(?=\\d{4})", "*");
            String amount = String.format("%d.%02d", order.amountKopecks() / 100, order.amountKopecks() % 100);
            report += order.id() + ";" + date + ";" + card + ";" + amount + "\n";
        }
        return report;
    }
}
