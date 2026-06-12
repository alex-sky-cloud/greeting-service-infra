package com.example.greeting;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres lifecycle is managed by Spring, not JUnit {@code @Testcontainers}.
 * Container stops after Hikari/DataSource are closed.
 *
 * @see <a href="https://docs.spring.io/spring-boot/reference/testing/testcontainers.html#testing.testcontainers.lifecycle">Spring Boot Testcontainers lifecycle</a>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.PostgresTestConfiguration.class)
abstract class AbstractIntegrationTest {

    interface PostgresContainers {

        @Container
        @ServiceConnection
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.4"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ImportTestcontainers(PostgresContainers.class)
    static class PostgresTestConfiguration {
    }
}
