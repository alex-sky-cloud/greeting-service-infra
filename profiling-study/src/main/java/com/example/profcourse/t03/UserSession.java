package com.example.profcourse.t03;

import java.time.Instant;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Сессия пользователя интернет-магазина: кто вошёл, когда последний раз был активен и черновик корзины (512 байт).
 */
public final class UserSession {

    private final String sessionId;
    private final String userId;
    private final byte[] cartDraft = new byte[512];
    private volatile Instant lastSeen;

    public UserSession(String sessionId, String userId, Instant lastSeen) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.lastSeen = lastSeen;
    }

    public String sessionId() {
        return sessionId;
    }

    public String userId() {
        return userId;
    }

    public Instant lastSeen() {
        return lastSeen;
    }

    public void seenAt(Instant at) {
        this.lastSeen = at;
    }

    public int cartBytes() {
        return cartDraft.length;
    }
}
