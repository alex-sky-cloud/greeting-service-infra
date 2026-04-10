package com.example.greeting.controller;

import com.example.greeting.model.GreetingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Единственный REST endpoint сервиса.
 * GET /api/greeting?name=World
 *
 * Принцип: environment variables управляют поведением сервиса.
 * APP_ENV и APP_VERSION инжектируются из ConfigMap / Secret через Kubernetes.
 */
@RestController
@RequestMapping("/api")
public class GreetingController {

    @Value("${app.env:local}")
    private String appEnv;

    @Value("${app.version:unknown}")
    private String appVersion;

    @GetMapping("/greeting")
    public GreetingResponse greeting(
            @RequestParam(name = "name", defaultValue = "World") String name) {

        String message = "Hello, %s! Environment: %s, Version: %s"
                .formatted(name, appEnv, appVersion);

        return new GreetingResponse(message, appEnv, appVersion, Instant.now().toString());
    }
}
