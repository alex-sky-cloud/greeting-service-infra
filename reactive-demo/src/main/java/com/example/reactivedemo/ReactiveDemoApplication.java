package com.example.reactivedemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * <p>Точка входа учебного приложения <strong>reactive-demo</strong>.</p>
 *
 * <p>Стек:</p>
 * <ul>
 *   <li>Spring WebFlux — REST без блокировки потока;</li>
 *   <li>Spring Data R2DBC — неблокирующий доступ к PostgreSQL;</li>
 *   <li>Flyway — миграции схемы {@code reactive_demo} (JDBC, см.
 *       {@code src/main/resources/db/flyway-r2dbc-migrations.md}).</li>
 * </ul>
 *
 * <p>Запуск локально (profile {@code local}, Flyway на JDBC, runtime на R2DBC):</p>
 * <pre>
 * {@code
 * bash scripts/create-reactive-demo-db.sh
 * gradlew.bat bootRun --args="--spring.profiles.active=local"
 * }
 * </pre>
 */
@SpringBootApplication
@EnableR2dbcRepositories
public class ReactiveDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactiveDemoApplication.class, args);
    }
}
