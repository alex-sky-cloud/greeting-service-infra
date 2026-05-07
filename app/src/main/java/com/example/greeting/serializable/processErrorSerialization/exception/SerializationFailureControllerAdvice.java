package com.example.greeting.serializable.processErrorSerialization.exception;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Глобальный обработчик ошибок конкурентного изменения данных
 * для сценария write skew в варианте fail fast.
 *
 * <p>Преобразует прикладное исключение в информативный HTTP-ответ,
 * понятный фронтенду и разработчику.
 */
@RestControllerAdvice
@Slf4j
public class SerializationFailureControllerAdvice {

    /**
     * Обрабатывает ошибку fail fast, возникшую при конфликте параллельных транзакций.
     *
     * <p>Возвращает клиенту бизнес-понятное сообщение:
     * операция не выполнена, потому что исходные данные успели измениться
     * другой транзакцией.
     *
     * @param ex прикладное исключение сценария fail fast
     * @return HTTP-ответ с кодом 503 и диагностическим JSON
     */
    @ExceptionHandler(WriteSkewFailFastSerializationException.class)
    public ResponseEntity<Map<String, Object>> handleWriteSkewFailFastSerializationException(
            WriteSkewFailFastSerializationException ex
    ) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;

        SQLException sqlException = findSQLException(ex);

        String sqlState = null;
        String dbMessage = null;

        if (sqlException != null) {
            sqlState = sqlException.getSQLState();
            dbMessage = sqlException.getMessage();
        }

        log.warn(
                "Конфликт параллельных изменений при снятии врача с дежурства: message='{}', sqlState={}, dbMessage={}",
                ex.getMessage(),
                sqlState,
                dbMessage,
                ex
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());

        body.put(
                "message",
                "Невозможно снять врача с дежурства: список дежурных врачей был изменён параллельной операцией. " +
                        "Обновите данные и повторите попытку."
        );

        body.put(
                "reason",
                "Во время выполнения операции другой параллельный запрос уже изменил состояние дежурств."
        );

        if (sqlState != null) {
            body.put("sqlState", sqlState);
        }

        if (dbMessage != null) {
            body.put("dbMessage", dbMessage);
        }

        return ResponseEntity.status(status).body(body);
    }

    /**
     * Ищет первый {@link SQLException} в цепочке причин исключения.
     *
     * @param throwable исходное исключение
     * @return найденное SQL-исключение или {@code null}, если его нет
     */
    private SQLException findSQLException(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof SQLException) {
                return (SQLException) current;
            }
            current = current.getCause();
        }

        return null;
    }
}