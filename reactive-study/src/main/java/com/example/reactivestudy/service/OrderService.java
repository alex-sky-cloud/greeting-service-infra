package com.example.reactivestudy.service;

import com.example.reactivestudy.domain.dto.OrderResponse;
import com.example.reactivestudy.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public Flux<OrderResponse> findFirst10() {
        return orderRepository.findTop10ByOrderByIdAsc()
                .map(OrderResponse::from);
    }
}
