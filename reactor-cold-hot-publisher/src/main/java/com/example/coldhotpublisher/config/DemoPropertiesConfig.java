package com.example.coldhotpublisher.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Подключает настройки учебного стенда из {@code application.yml} к Spring-контексту. */
@Configuration
@EnableConfigurationProperties(DemoProperties.class)
public class DemoPropertiesConfig {
}
