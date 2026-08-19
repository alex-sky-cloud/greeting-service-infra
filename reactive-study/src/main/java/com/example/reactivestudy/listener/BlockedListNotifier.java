package com.example.reactivestudy.listener;

import com.example.reactivestudy.event.BlockedListEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listener в аннотационном стиле.
 * <p>{@link EventListener} заменяет реализацию {@code ApplicationListener} —
 * Spring сам определяет тип события по параметру метода.</p>
 */
@Component
@Slf4j
public class BlockedListNotifier {

    /**
     * Вызывается контекстом при публикации {@link BlockedListEvent}.
     * <em>Не</em> вызывается издателем ({@link com.example.reactivestudy.service.EmailService}) напрямую.
     *
     * @param event опубликованное событие
     */
    @EventListener
    public void onBlocked(BlockedListEvent event) {
        log.info("Заблокирован адрес: {}", event.getAddress());
    }
}
