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
 * Базовый класс для интеграционных тестов с PostgreSQL в Testcontainers.
 *
 * <h2>Проблема</h2>
 * <p>
 * При завершении тестов в логах появлялись предупреждения HikariCP:
 * </p>
 * <pre>{@code
 * WARN  com.zaxxer.hikari.pool.PoolBase - HikariPool-1 - Failed to validate connection
 *       org.postgresql.jdbc.PgConnection@... (Соединение уже было закрыто).
 *       Possibly consider using a shorter maxLifetime value.
 * }</pre>
 * <p>
 * Иногда shutdown затягивался на ~30&nbsp;с из‑за таймаута
 * {@code EntityManagerFactory} при попытке выполнить DDL на уже остановленной БД.
 * </p>
 *
 * <h2>Почему возникала</h2>
 * <p>
 * При связке {@code @SpringBootTest} + {@code @Testcontainers} + {@code @Container}
 * жизненным циклом контейнера управляет <strong>JUnit</strong>, а пулом соединений —
 * <strong>Spring</strong> (кэш {@code ApplicationContext}).
 * </p>
 * <ul>
 *   <li>JUnit останавливает PostgreSQL сразу после класса тестов;</li>
 *   <li>Spring закрывает {@code HikariDataSource} и {@code EntityManagerFactory} позже;</li>
 *   <li>Hikari пытается провалидировать соединения к уже мёртвому контейнеру.</li>
 * </ul>
 * <p>
 * Настройки {@code max-lifetime} и {@code @DirtiesContext} не устраняют корень проблемы —
 * это гонка двух независимых механизмов shutdown.
 * </p>
 *
 * <h2>Как решено</h2>
 * <p>
 * Контейнер объявлен через {@link ImportTestcontainers}: Spring создаёт и останавливает
 * его <em>после</em> закрытия всех зависимых бинов ({@code DataSource}, JPA).
 * </p>
 * <ol>
 *   <li>интерфейс {@link PostgresContainers} — декларация контейнера с {@link ServiceConnection};</li>
 *   <li>{@link PostgresTestConfiguration} — импорт контейнера как Spring-бина;</li>
 *   <li>JUnit {@code @Testcontainers} на базовом классе <strong>не используется</strong>.</li>
 * </ol>
 *
 * <p><strong>Итог:</strong> чистый shutdown — {@code HikariPool shutdown completed} без WARN в логах.</p>
 *
 * @see <a href="https://docs.spring.io/spring-boot/reference/testing/testcontainers.html#testing.testcontainers.lifecycle">
 *      Spring Boot — Lifecycle of Managed Containers</a>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.PostgresTestConfiguration.class)
abstract class AbstractIntegrationTest {

    /**
     * Декларация PostgreSQL-контейнера для {@link ImportTestcontainers}.
     * Поле {@code @Container} здесь — метаданные для Spring, а не управление JUnit.
     */
    interface PostgresContainers {

        @Container
        @ServiceConnection
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.4"));
    }

    /**
     * Регистрирует контейнер как Spring-бин с корректным порядком остановки.
     */
    @TestConfiguration(proxyBeanMethods = false)
    @ImportTestcontainers(PostgresContainers.class)
    static class PostgresTestConfiguration {
    }
}
