package com.example.reactivestudy.controller;

import com.example.reactivestudy.domain.dto.OrderResponse;
import com.example.reactivestudy.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * REST для лаборатории «путь HTTP-запроса» (см. docs/HTTP-REQUEST-DEBUG-BREAKPOINTS.md).
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Первые 10 заказов по id — реактивная цепочка Controller → Service → R2DBC → PostgreSQL.
     *
     * <pre>{@code GET /api/orders/first-10}</pre>
     */
    @GetMapping("/first-10")
    public Flux<OrderResponse> first10() {
        return orderService.findFirst10();
    }
}
