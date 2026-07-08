package com.example.coldhotpublisher.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Регистрирует бин {@link DemoProperties} из {@code application.yml} (префикс {@code demo}).
 *
 * <p>Описание ключей: {@code src/main/resources/application.md}.</p>
 */
@Configuration
@EnableConfigurationProperties(DemoProperties.class)
public class DemoPropertiesConfig {
}
