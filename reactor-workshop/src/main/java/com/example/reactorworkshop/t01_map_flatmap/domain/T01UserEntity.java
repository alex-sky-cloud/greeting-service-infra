package com.example.reactorworkshop.t01_map_flatmap.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Строка {@code reactor_workshop.users} для лабы t01.
 * Схема в {@code @Table} обязательна: иначе R2DBC ищет {@code orders}/{@code users} в {@code public}.
 */
@Table(value = "users", schema = "reactor_workshop")
public record T01UserEntity(

        @Id
        Long id,

        String email,

        @Column("full_name")
        String fullName,

        @Column("created_at")
        Instant createdAt
) {
}
