package com.example.coldhotpublisher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <p>Учебный стенд интернет-магазина: каталог, anti-fraud, тарифы, трекинг заказов, котировки.</p>
 * <p>В одном процессе живут и «внешние» заглушки, и клиенты к ним — для демонстрации
 * типичных ситуаций с повторными и отложенными обращениями к данным.</p>
 */
@SpringBootApplication
public class ReactorColdHotPublisherApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactorColdHotPublisherApplication.class, args);
    }
}
