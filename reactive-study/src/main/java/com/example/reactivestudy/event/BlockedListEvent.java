package com.example.reactivestudy.event;

import org.springframework.context.ApplicationEvent;

/**
 * Доменное событие: адрес добавлен в чёрный список.
 * <p>Расширяет {@link ApplicationEvent}: Spring автоматически
 * передаст объект всем listener'ам, подписанным на этот тип.</p>
 *
 * <p>Лаборатория: {@code docs/SPRING-EVENTS-DEBUG-BREAKPOINTS.md}.</p>
 */
public class BlockedListEvent extends ApplicationEvent {

    private final String address;

    /**
     * @param source  объект, опубликовавший событие (обычно {@code this})
     * @param address e-mail адрес, попавший в чёрный список
     */
    public BlockedListEvent(Object source, String address) {
        super(source);
        this.address = address;
    }

    /** @return заблокированный адрес */
    public String getAddress() {
        return address;
    }
}
