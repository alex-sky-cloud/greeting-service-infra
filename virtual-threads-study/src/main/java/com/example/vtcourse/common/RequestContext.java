package com.example.vtcourse.common;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Пользователь, от имени которого выполняется текущий запрос. Его читают готовые сервисы тем 04 и 05.
 */
public final class RequestContext {

    public static final ScopedValue<String> USER = ScopedValue.newInstance();

    private RequestContext() {
    }

    /** Пользователь текущего запроса или {@code "anonymous"}, если запрос выполняется ни от чьего имени. */
    public static String currentUser() {
        return USER.orElse("anonymous");
    }
}
