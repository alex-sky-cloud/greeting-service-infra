package com.example.profcourse.t03;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Реестр сессий в том виде, в каком он ушёл в продакшен. Работает правильно: просроченная сессия недействительна.
 * Лаборатория {@code leak} запускает именно его.
 */
public class LegacySessionRegistry {

    public static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    public LegacySessionRegistry(Clock clock) {
        this.clock = clock;
    }

    public String login(String userId) {
        String id = UUID.randomUUID().toString();
        sessions.put(id, new UserSession(id, userId, clock.instant()));
        return id;
    }

    public boolean touch(String sessionId) {
        UserSession session = sessions.get(sessionId);
        Instant now = clock.instant();
        if (session == null || !session.lastSeen().plus(TIMEOUT).isAfter(now)) {
            return false;
        }
        session.seenAt(now);
        return true;
    }

    public void logout(String sessionId) {
        sessions.remove(sessionId);
    }

    public int size() {
        return sessions.size();
    }
}
