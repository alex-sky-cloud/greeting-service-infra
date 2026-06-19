package com.example.reactivedemo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * <p>Сущность пользователя. Таблица {@code reactive_demo.users}.</p>
 *
 * <p>Используется в R2DBC-репозитории {@link com.example.reactivedemo.repository.UserRepository};
 * методы репозитория возвращают {@link reactor.core.publisher.Mono} или
 * {@link reactor.core.publisher.Flux} — не блокирующий {@code Optional} / {@code List}.</p>
 *
 * @param id         первичный ключ
 * @param email      уникальный email
 * @param fullName   отображаемое имя (колонка {@code full_name})
 * @param createdAt  время создания записи
 */
@Table(name = "users", schema = "reactive_demo")
public record User(
        @Id Long id,
        String email,
        @Column("full_name") String fullName,
        @Column("created_at") Instant createdAt
) {
}
