package com.example.coldhotpublisher.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Регистрирует {@link DemoProperties} как Spring-бин из YAML. */
@Configuration
@EnableConfigurationProperties(DemoProperties.class)
public class DemoPropertiesConfig {
}
