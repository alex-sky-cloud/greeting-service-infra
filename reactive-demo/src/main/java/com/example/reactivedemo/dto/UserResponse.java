package com.example.reactivedemo.dto;

import com.example.reactivedemo.domain.User;

import java.time.Instant;

/**
 * <p>DTO пользователя для REST-ответа (без служебных полей persistence).</p>
 *
 * @param id        идентификатор
 * @param email     email
 * @param fullName  имя
 * @param createdAt дата создания
 */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        Instant createdAt
) {

    /**
     * <p>Маппинг из доменной сущности {@link User}.</p>
     *
     * @param user сущность из R2DBC
     * @return DTO для JSON
     */
    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.email(), user.fullName(), user.createdAt());
    }
}
