package com.example.vtcourse.t04;

import com.example.vtcourse.common.RequestContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Журнал аудита. Каждая строка — {@code <пользователь>: <действие>}; пользователь берётся
 * из {@link RequestContext#currentUser()}.
 */
public class AuditLog {

    private final List<String> lines = new CopyOnWriteArrayList<>();

    public void record(String action) {
        lines.add(RequestContext.currentUser() + ": " + action);
    }

    public List<String> lines() {
        return List.copyOf(lines);
    }
}
