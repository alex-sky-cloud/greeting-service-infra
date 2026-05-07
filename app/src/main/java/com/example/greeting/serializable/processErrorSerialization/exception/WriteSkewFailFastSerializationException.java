package com.example.greeting.serializable.processErrorSerialization.exception;

/**
 * Исключение, обозначающее отказ PostgreSQL
 * в завершении транзакции из-за serialization failure.
 */
public class WriteSkewFailFastSerializationException extends RuntimeException {

    /**
     * Создаёт исключение с сообщением и причиной.
     *
     * @param message текст ошибки
     * @param cause корневая причина
     */
    public WriteSkewFailFastSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
