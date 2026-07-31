package com.example.reactivestudy.service;

import com.example.reactivestudy.domain.dto.OrderResponse;
import com.example.reactivestudy.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    public Flux<OrderResponse> findFirst10() {

        return orderRepository.findTop10ByOrderByIdAsc()
                .doOnNext(order -> log.info(order.status()))
                .map(OrderResponse::from);
    }
}
