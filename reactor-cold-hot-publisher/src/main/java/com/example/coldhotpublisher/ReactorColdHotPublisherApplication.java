package com.example.coldhotpublisher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <p>Запускает учебный стенд: в одном процессе живут HTTP-заглушки и WebClient-клиенты,
 * которые демонстрируют cold/hot Publisher в стиле production WebFlux.</p>
 */
@SpringBootApplication
public class ReactorColdHotPublisherApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactorColdHotPublisherApplication.class, args);
    }
}
