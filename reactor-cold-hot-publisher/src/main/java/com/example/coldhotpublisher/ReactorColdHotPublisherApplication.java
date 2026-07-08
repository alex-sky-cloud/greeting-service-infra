package com.example.coldhotpublisher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo-приложение: cold/hot Publisher, share/cache/replay/refCount с WebClient.
 *
 * @see docs/interview/Hot Publisher и Cold Publisher - примеры.md
 */
@SpringBootApplication
public class ReactorColdHotPublisherApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactorColdHotPublisherApplication.class, args);
    }
}
