package com.example.reactivedemo.dto;

import java.util.List;

/**
 * <p>Ответ endpoint'а {@code /api/demo/reactor/compare}: наглядное сравнение {@code map} и {@code flatMap}.</p>
 *
 * @param description     краткое пояснение
 * @param mapWrong        результат ошибочного {@code map}
 * @param flatMapCorrect  результат правильного {@code flatMap}
 */
public record MapVsFlatMapComparisonResponse(
        String description,
        MapWrongResult mapWrong,
        FlatMapCorrectResult flatMapCorrect
) {

    /**
     * <p>Что получается при {@code map(userRepository::findById)}.</p>
     *
     * @param streamElementType тип элемента в потоке (ожидается {@code Mono<User>})
     * @param elementsInStream  фактические типы объектов в потоке
     * @param resolvedUsers     пользователи (обычно пусто без {@code flatMap})
     * @param note              пояснение для обучения
     */
    public record MapWrongResult(
            String streamElementType,
            List<String> elementsInStream,
            List<UserResponse> resolvedUsers,
            String note
    ) {
    }

    /**
     * <p>Что получается при {@code flatMap(userRepository::findById)}.</p>
     *
     * @param streamElementType тип элемента ({@code User})
     * @param users             загруженные пользователи
     */
    public record FlatMapCorrectResult(
            String streamElementType,
            List<UserResponse> users
    ) {
    }
}
