package com.example.reactivestudy.controller;

import com.example.reactivestudy.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * HTTP-триггер для лаборатории Spring Events.
 * <p>См. {@code docs/SPRING-EVENTS-DEBUG-BREAKPOINTS.md}.</p>
 */
@RestController
@RequestMapping("/api/demo/events")
@RequiredArgsConstructor
public class BlockedListController {

    private final EmailService emailService;

    /**
     * Публикует {@link com.example.reactivestudy.event.BlockedListEvent}.
     *
     * <pre>{@code GET /api/demo/events/block/spammer@example.com}</pre>
     */
    @GetMapping("/block/{address}")
    public Mono<Map<String, String>> block(@PathVariable String address) {
        emailService.blockAddress(address);
        return Mono.just(Map.of(
                "status", "blocked",
                "address", address
        ));
    }
}
